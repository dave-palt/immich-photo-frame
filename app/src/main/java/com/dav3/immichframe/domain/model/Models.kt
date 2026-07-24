package com.dav3.immichframe.domain.model

data class Album(
    val id: String,
    val name: String,
    val assetCount: Int,
    val thumbnailAssetId: String?,
)

data class Asset(
    val id: String,
    val type: AssetType,
)

enum class AssetType { IMAGE, VIDEO }

data class SlideshowSettings(
    val intervalSeconds: Int = 30,
    val transitionSeconds: Float = 1f,
    val fillMode: FillMode = FillMode.CONTAIN,
    val kenBurns: Boolean = false,
    val showClock: Boolean = false,
    val keepScreenOn: Boolean = true,
)

enum class FillMode { CONTAIN, COVER }
