package com.dav3.immichframe.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class PingResponse(val resp: String)

@Serializable
data class UserResponse(val id: String, val email: String, val name: String)

@Serializable
data class AlbumDto(
    val id: String,
    val albumName: String,
    val assetCount: Int = 0,
    val albumThumbnailAssetId: String? = null
)

@Serializable
data class AlbumInfoDto(
    val id: String,
    val albumName: String,
    val assetCount: Int = 0,
    val assets: List<AssetDto> = emptyList()
)

@Serializable
data class AssetDto(
    val id: String,
    val type: String = "IMAGE"
)
