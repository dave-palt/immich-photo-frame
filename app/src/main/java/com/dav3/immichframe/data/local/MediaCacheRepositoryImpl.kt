package com.dav3.immichframe.data.local

import android.content.Context
import com.dav3.immichframe.domain.model.AlbumSyncState
import com.dav3.immichframe.domain.model.CachedAsset
import com.dav3.immichframe.domain.model.SyncProgress
import com.dav3.immichframe.domain.repository.MediaCacheRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaCacheRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : MediaCacheRepository {
    private val db = MediaCacheDatabase.getDatabase(context)

    override val cacheDir: String = context.getExternalFilesDir("media_cache")?.absolutePath
        ?: context.cacheDir.absolutePath + "/media_cache"

    private val _syncProgress = MutableStateFlow<SyncProgress?>(null)
    override val syncProgress: StateFlow<SyncProgress?> = _syncProgress.asStateFlow()

    override suspend fun getCachedAssets(albumId: String): Result<List<CachedAsset>> = withContext(Dispatchers.IO) {
        try {
            val assets = db.cachedAssetDao().getByAlbumId(albumId)
            Result.success(assets.map { CachedAssetEntity.toDomain(it) })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getAllCachedAssets(): Result<List<CachedAsset>> = withContext(Dispatchers.IO) {
        try {
            val assets = db.cachedAssetDao().getAll()
            Result.success(assets.map { CachedAssetEntity.toDomain(it) })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun upsertAssets(assets: List<CachedAsset>) = withContext(Dispatchers.IO) {
        db.cachedAssetDao().insertAll(assets.map { CachedAssetEntity.fromDomain(it) })
    }

    override suspend fun removeAssets(assetIds: List<String>) = withContext(Dispatchers.IO) {
        val assets = db.cachedAssetDao().getByIds(assetIds)
        assets.forEach { entity ->
            deleteFile(entity.filePath)
            deleteFile(entity.thumbnailPath)
        }
        db.cachedAssetDao().deleteByIds(assetIds)
    }

    override suspend fun clearAlbum(albumId: String) = withContext(Dispatchers.IO) {
        val assets = db.cachedAssetDao().getByAlbumId(albumId)
        assets.forEach { entity ->
            deleteFile(entity.filePath)
            deleteFile(entity.thumbnailPath)
        }
        db.cachedAssetDao().deleteByAlbumId(albumId)
    }

    override suspend fun clearAll() = withContext(Dispatchers.IO) {
        val assets = db.cachedAssetDao().getAll()
        assets.forEach { entity ->
            deleteFile(entity.filePath)
            deleteFile(entity.thumbnailPath)
        }
        db.cachedAssetDao().deleteAll()
    }

    override suspend fun getAlbumSyncState(albumId: String): Result<AlbumSyncState> = withContext(Dispatchers.IO) {
        try {
            val state = db.albumSyncStateDao().getByAlbumId(albumId)
            Result.success(
                state?.let { AlbumSyncStateEntity.toDomain(it) } ?: AlbumSyncState(albumId = albumId),
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getAllAlbumSyncStates(): Result<List<AlbumSyncState>> = withContext(Dispatchers.IO) {
        try {
            val states = db.albumSyncStateDao().getAll()
            Result.success(states.map { AlbumSyncStateEntity.toDomain(it) })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateAlbumSyncState(state: AlbumSyncState) = withContext(Dispatchers.IO) {
        db.albumSyncStateDao().insert(AlbumSyncStateEntity.fromDomain(state))
    }

    override suspend fun getAssetFilePath(assetId: String): String? = withContext(Dispatchers.IO) {
        db.cachedAssetDao().getById(assetId)?.let { CachedAssetEntity.toDomain(it).filePath }
    }

    override suspend fun getAssetThumbnailPath(assetId: String): String? = withContext(Dispatchers.IO) {
        db.cachedAssetDao().getById(assetId)?.let { CachedAssetEntity.toDomain(it).thumbnailPath }
    }

    override suspend fun deleteAssetFiles(assetId: String) {
        withContext(Dispatchers.IO) {
            db.cachedAssetDao().getById(assetId)?.let { entity ->
                val domain = CachedAssetEntity.toDomain(entity)
                deleteFile(domain.filePath)
                deleteFile(domain.thumbnailPath)
                db.cachedAssetDao().deleteByIds(listOf(assetId))
            }
        }
    }

    // Called by MediaCacheWorker to surface progress in the UI
    internal fun updateSyncProgress(progress: SyncProgress) {
        _syncProgress.value = progress
    }

    internal fun clearSyncProgress() {
        _syncProgress.value = null
    }

    private fun deleteFile(path: String?) {
        path?.let { File(it).delete() }
    }
}
