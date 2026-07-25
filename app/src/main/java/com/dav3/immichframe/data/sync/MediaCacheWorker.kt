package com.dav3.immichframe.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.dav3.immichframe.data.local.MediaCacheRepositoryImpl
import com.dav3.immichframe.domain.model.AlbumSyncState
import com.dav3.immichframe.domain.model.Asset
import com.dav3.immichframe.domain.model.CachedAsset
import com.dav3.immichframe.domain.model.SyncProgress
import com.dav3.immichframe.domain.repository.ImmichRepository
import com.dav3.immichframe.domain.repository.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

@HiltWorker
class MediaCacheWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val mediaCacheRepository: MediaCacheRepositoryImpl,
    private val immichRepository: ImmichRepository,
    private val settingsRepository: SettingsRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): ListenableWorker.Result = withContext(Dispatchers.IO) {
        val albumIds = inputData.getStringArray(KEY_ALBUM_IDS)?.toList() ?: emptyList()
        if (albumIds.isEmpty()) return@withContext ListenableWorker.Result.success()

        try {
            performFullSync(albumIds)
            ListenableWorker.Result.success()
        } catch (e: Exception) {
            ListenableWorker.Result.failure()
        }
    }

    private suspend fun performFullSync(albumIds: List<String>) {
        mediaCacheRepository.updateSyncProgress(
            SyncProgress(
                albumIds = albumIds,
                currentAlbum = albumIds.firstOrNull() ?: "",
                phase = SyncProgress.Phase.FETCHING_METADATA,
                totalAssets = 0,
                processedAssets = 0,
                currentAsset = "",
            ),
        )

        for (albumId in albumIds) {
            mediaCacheRepository.updateSyncProgress(
                SyncProgress(
                    albumIds = albumIds,
                    currentAlbum = albumId,
                    phase = SyncProgress.Phase.FETCHING_METADATA,
                    totalAssets = 0,
                    processedAssets = 0,
                    currentAsset = "Fetching album metadata...",
                ),
            )

            immichRepository.getAlbumAssets(albumId).fold(
                onSuccess = { remoteAssets ->
                    downloadAndReconcile(albumId, albumIds, remoteAssets)
                },
                onFailure = { /* skip this album, continue others */ },
            )
        }

        mediaCacheRepository.clearSyncProgress()
    }

    private suspend fun downloadAndReconcile(
        albumId: String,
        albumIds: List<String>,
        remoteAssets: List<Asset>,
    ) {
        mediaCacheRepository.updateSyncProgress(
            SyncProgress(
                albumIds = albumIds,
                currentAlbum = albumId,
                phase = SyncProgress.Phase.DOWNLOADING,
                totalAssets = remoteAssets.size,
                processedAssets = 0,
                currentAsset = "Downloading ${remoteAssets.size} assets...",
            ),
        )

        val syncState = mediaCacheRepository.getAlbumSyncState(albumId).getOrElse {
            AlbumSyncState(albumId = albumId)
        }
        val cachedAssets = mediaCacheRepository.getCachedAssets(albumId).getOrElse { emptyList() }
        val cachedIds = cachedAssets.map { it.id }.toSet()
        val remoteIds = remoteAssets.map { it.id }.toSet()

        // Remove assets that are in cache but no longer in the album
        val toRemove = cachedAssets.filter { it.id !in remoteIds }
        if (toRemove.isNotEmpty()) {
            mediaCacheRepository.removeAssets(toRemove.map { it.id })
        }

        var processed = 0
        for (asset in remoteAssets) {
            processed++
            mediaCacheRepository.updateSyncProgress(
                SyncProgress(
                    albumIds = albumIds,
                    currentAlbum = albumId,
                    phase = SyncProgress.Phase.DOWNLOADING,
                    totalAssets = remoteAssets.size,
                    processedAssets = processed,
                    currentAsset = "Downloading ${asset.id.take(8)}...",
                ),
            )

            if (asset.id !in cachedIds) {
                downloadAsset(albumId, asset).onSuccess { cached ->
                    mediaCacheRepository.upsertAssets(listOf(cached))
                }
            } else {
                val cached = cachedAssets.find { it.id == asset.id }
                if (cached?.lastModified != asset.lastModified) {
                    downloadAsset(albumId, asset).onSuccess { updated ->
                        mediaCacheRepository.upsertAssets(listOf(updated))
                    }
                }
            }
        }

        mediaCacheRepository.updateAlbumSyncState(
            syncState.copy(
                lastSyncedAt = System.currentTimeMillis(),
                assetCount = remoteAssets.size,
            ),
        )
    }

    private suspend fun downloadAsset(
        albumId: String,
        asset: Asset,
    ): kotlin.Result<CachedAsset> = withContext(Dispatchers.IO) {
        try {
            val apiKey = settingsRepository.apiKey.first()
            val baseUrl = settingsRepository.serverUrl.first()
            val base = if (baseUrl.endsWith("/")) "$baseUrl/api/" else "$baseUrl/api/"

            val fileUrl = "${base}assets/${asset.id}/original?apiKey=$apiKey"
            val thumbUrl = "${base}assets/${asset.id}/thumbnail?size=thumbnail&apiKey=$apiKey"

            val cacheDir = mediaCacheRepository.cacheDir
            val filePath = File(cacheDir, asset.id).absolutePath
            val thumbPath = File(cacheDir, "${asset.id}_thumb").absolutePath

            val client = OkHttpClient()

            // Download main file
            val response = client.newCall(Request.Builder().url(fileUrl).build()).execute()
            if (!response.isSuccessful) {
                return@withContext kotlin.Result.failure(
                    Exception("Download failed: ${response.code}"),
                )
            }
            val body = response.body?.byteStream()
                ?: return@withContext kotlin.Result.failure(Exception("Empty response body"))
            File(filePath).outputStream().use { output -> body.copyTo(output) }

            // Download thumbnail (best-effort)
            var thumbPathResult: String? = null
            val thumbResponse = client.newCall(Request.Builder().url(thumbUrl).build()).execute()
            if (thumbResponse.isSuccessful) {
                thumbResponse.body?.byteStream()?.use { input ->
                    File(thumbPath).outputStream().use { output -> input.copyTo(output) }
                    thumbPathResult = thumbPath
                }
            }

            kotlin.Result.success(
                CachedAsset(
                    id = asset.id,
                    albumId = albumId,
                    type = asset.type,
                    filePath = filePath,
                    thumbnailPath = thumbPathResult,
                    fileSize = File(filePath).length(),
                    checksum = null,
                    lastModified = System.currentTimeMillis(),
                    cachedAt = System.currentTimeMillis(),
                ),
            )
        } catch (e: Exception) {
            kotlin.Result.failure(e)
        }
    }

    companion object {
        const val WORK_NAME = "media_cache_sync"
        const val KEY_ALBUM_IDS = "albumIds"
        const val KEY_INCREMENTAL = "incremental"
    }
}
