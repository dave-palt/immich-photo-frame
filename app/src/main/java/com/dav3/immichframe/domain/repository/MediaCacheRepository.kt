package com.dav3.immichframe.domain.repository

import com.dav3.immichframe.domain.model.AlbumSyncState
import com.dav3.immichframe.domain.model.CachedAsset
import com.dav3.immichframe.domain.model.SyncProgress
import kotlinx.coroutines.flow.StateFlow

interface MediaCacheRepository {
    // Cached assets
    suspend fun getCachedAssets(albumId: String): Result<List<CachedAsset>>
    suspend fun getAllCachedAssets(): Result<List<CachedAsset>>
    suspend fun upsertAssets(assets: List<CachedAsset>)
    suspend fun removeAssets(assetIds: List<String>)
    suspend fun clearAlbum(albumId: String)
    suspend fun clearAll()

    // Album sync state
    suspend fun getAlbumSyncState(albumId: String): Result<AlbumSyncState>
    suspend fun getAllAlbumSyncStates(): Result<List<AlbumSyncState>>
    suspend fun updateAlbumSyncState(state: AlbumSyncState)

    // File management
    suspend fun getAssetFilePath(assetId: String): String?
    suspend fun getAssetThumbnailPath(assetId: String): String?
    suspend fun deleteAssetFiles(assetId: String)

    // Cache directory
    val cacheDir: String

    // Progress tracking
    val syncProgress: StateFlow<SyncProgress?>
}
