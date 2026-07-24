package com.dav3.immichframe.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class PingResponse(
    val resp: String? = null,
)

@Serializable
data class UserResponse(
    val id: String,
    val email: String,
    val name: String,
)

@Serializable
data class AlbumDto(
    val id: String,
    val albumName: String,
    val assetCount: Int = 0,
    val albumThumbnailAssetId: String? = null,
)

@Serializable
data class AssetDto(
    val id: String,
    val type: String = "IMAGE",
)

// --- Search endpoint (POST /search/metadata) ---

@Serializable
data class SearchMetadataRequest(
    val albumIds: List<String>,
    val type: String = "IMAGE",
    val size: Int = 1000,
)

@Serializable
data class SearchMetadataResponse(
    val assets: SearchAssetsDto,
)

@Serializable
data class SearchAssetsDto(
    val total: Int = 0,
    val count: Int = 0,
    val items: List<AssetDto> = emptyList(),
)
