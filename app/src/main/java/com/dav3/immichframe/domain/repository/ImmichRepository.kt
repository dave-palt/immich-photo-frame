package com.dav3.immichframe.domain.repository

import com.dav3.immichframe.domain.model.Album
import com.dav3.immichframe.domain.model.Asset

interface ImmichRepository {
    suspend fun ping(): Result<Unit>

    suspend fun validateApiKey(): Result<String> // returns user email

    suspend fun getAlbums(): Result<List<Album>>

    suspend fun getAlbumAssets(albumId: String): Result<List<Asset>>

    suspend fun getAlbumAssets(albumId: String, cursor: String?): Result<List<Asset>>

    fun imageUrl(assetId: String): String

    fun thumbnailUrl(assetId: String): String

    fun videoUrl(assetId: String): String

    /** Invalidate cached API/client so new credentials take effect. */
    fun invalidateCache()
}
