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
        // If the API key lacks asset.download, skip the entire download phase —
        // the cache can't fetch originals without it. Metadata sync still runs
        // so the asset list stays current; images just won't be available offline.
        val permStatus = settingsRepository.permissionStatus.first()
        val downloadDenied = permStatus?.statuses?.get(
            com.dav3.immichframe.domain.model.RequiredPermission.ASSET_DOWNLOAD,
        ) == com.dav3.immichframe.domain.model.PermissionStatus.Denied

        if (downloadDenied) {
            mediaCacheRepository.updateSyncProgress(
                SyncProgress(
                    albumIds = albumIds,
                    currentAlbum = "",
                    phase = SyncProgress.Phase.COMPLETE,
                    totalAssets = 0,
                    processedAssets = 0,
                    currentAsset = "Skipped — API key lacks asset.download permission",
                ),
            )
            return
        }

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

        // Track which albums no longer exist on the server. If all selected
        // albums are gone, clear the selection so the user is sent back to
        // album selection instead of silently running an empty slideshow.
        val goneAlbums = mutableListOf<String>()

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
                onFailure = { e ->
                    if (isAlbumGone(e)) {
                        // Album deleted on server — purge its cache and note it
                        mediaCacheRepository.clearAlbum(albumId)
                        goneAlbums.add(albumId)
                    }
                    // Transient network errors: skip, keep existing cache.
                },
            )
        }

        // If every selected album is gone, clear the selection so NavViewModel
        // routes the user back to album selection on next foreground.
        if (goneAlbums.isNotEmpty() && goneAlbums.size == albumIds.size) {
            settingsRepository.setSelectedAlbumIds(emptyList())
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
        val remoteIds = remoteAssets.map { it.id }.toSet()

        // Purge corrupt cache entries: files that are missing, empty, or whose
        // on-disk size doesn't match the stored fileSize. These are removed from
        // the DB so they're treated as "not cached" and redownloaded below.
        val corruptIds = cachedAssets.filter { cached ->
            val file = File(cached.filePath)
            !file.exists() || file.length() == 0L || file.length() != cached.fileSize
        }.map { it.id }
        if (corruptIds.isNotEmpty()) {
            android.util.Log.w("MediaCacheWorker", "Purging ${corruptIds.size} corrupt cache entries: ${corruptIds.take(3)}")
            mediaCacheRepository.removeAssets(corruptIds)
        }
        // Recompute after purge so the download loop redownloads them.
        val validCachedAssets = cachedAssets.filter { it.id !in corruptIds }
        val validCachedIds = validCachedAssets.map { it.id }.toSet()

        // Remove assets that are in cache but no longer in the album.
        // Guard: only reconcile when we actually got a non-empty remote list —
        // an empty response could be a transient server issue (e.g. search
        // service restarting), and wiping the cache on that would leave the
        // user with nothing to display offline.
        if (remoteAssets.isNotEmpty()) {
            val toRemove = validCachedAssets.filter { it.id !in remoteIds }
            if (toRemove.isNotEmpty()) {
                mediaCacheRepository.removeAssets(toRemove.map { it.id })
            }
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

            if (asset.id !in validCachedIds) {
                downloadAsset(albumId, asset).onSuccess { cached ->
                    mediaCacheRepository.upsertAssets(listOf(cached))
                }
            } else {
                val cached = validCachedAssets.find { it.id == asset.id }
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
            val filePath = File(cacheDir, asset.id)
            val thumbPath = File(cacheDir, "${asset.id}_thumb")
            val tmpPath = File(cacheDir, "${asset.id}.tmp")

            val client = OkHttpClient()

            // Download main file to a .tmp file first, then atomically rename
            // to the final path only after validating completeness. This prevents
            // truncated/partial files from being served as cached media.
            val response = client.newCall(Request.Builder().url(fileUrl).build()).execute()
            if (!response.isSuccessful) {
                return@withContext kotlin.Result.failure(
                    Exception("Download failed: ${response.code}"),
                )
            }
            val expectedLength = response.header("Content-Length")?.toLongOrNull() ?: -1L
            val body = response.body?.byteStream()
                ?: return@withContext kotlin.Result.failure(Exception("Empty response body"))
            tmpPath.outputStream().use { output -> body.copyTo(output) }

            // Validate downloaded size against Content-Length (if server provided it).
            // A mismatch means the download was truncated — delete and fail so the
            // caller can retry and the UI falls back to the network URL.
            val actualLength = tmpPath.length()
            if (expectedLength > 0 && actualLength != expectedLength) {
                tmpPath.delete()
                return@withContext kotlin.Result.failure(
                    Exception("Download truncated: got $actualLength/$expectedLength bytes"),
                )
            }
            if (actualLength == 0L) {
                tmpPath.delete()
                return@withContext kotlin.Result.failure(Exception("Downloaded file is empty"))
            }
            // Atomic move — the final path only exists once the file is complete.
            if (!tmpPath.renameTo(filePath)) {
                tmpPath.delete()
                return@withContext kotlin.Result.failure(Exception("Failed to move temp file to final path"))
            }

            // Download thumbnail (best-effort) — same atomic pattern.
            var thumbPathResult: String? = null
            val thumbTmp = File(cacheDir, "${asset.id}_thumb.tmp")
            val thumbResponse = client.newCall(Request.Builder().url(thumbUrl).build()).execute()
            if (thumbResponse.isSuccessful) {
                val thumbExpected = thumbResponse.header("Content-Length")?.toLongOrNull() ?: -1L
                thumbResponse.body?.byteStream()?.use { input ->
                    thumbTmp.outputStream().use { output -> input.copyTo(output) }
                    val thumbActual = thumbTmp.length()
                    if (thumbExpected > 0 && thumbActual != thumbExpected) {
                        thumbTmp.delete()
                    } else if (thumbActual > 0) {
                        if (thumbTmp.renameTo(thumbPath)) {
                            thumbPathResult = thumbPath.absolutePath
                        } else {
                            thumbTmp.delete()
                        }
                    } else {
                        thumbTmp.delete()
                    }
                }
            }

            kotlin.Result.success(
                CachedAsset(
                    id = asset.id,
                    albumId = albumId,
                    type = asset.type,
                    filePath = filePath.absolutePath,
                    thumbnailPath = thumbPathResult,
                    fileSize = filePath.length(),
                    checksum = null,
                    lastModified = System.currentTimeMillis(),
                    cachedAt = System.currentTimeMillis(),
                    originalMimeType = asset.originalMimeType,
                    exifDateTimeOriginal = asset.exif?.dateTimeOriginal,
                    exifDescription = asset.exif?.description,
                    exifCity = asset.exif?.city,
                    exifState = asset.exif?.state,
                    exifCountry = asset.exif?.country,
                    tags = asset.tags,
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

/**
 * Returns true if the exception indicates the album no longer exists on the
 * server (HTTP 404), as opposed to a transient network/server error.
 */
private fun isAlbumGone(throwable: Throwable): Boolean {
    val msg = throwable.message.orEmpty()
    return msg.contains("404") || msg.contains("Not Found", ignoreCase = true)
}
