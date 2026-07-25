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

data class ClockPosition(
    val x: Float = -1f, // -1 = unset (default bottom-start)
    val y: Float = -1f, // normalized 0..1 of screen
)

data class SlideshowSettings(
    val intervalSeconds: Int = 30,
    val transitionSeconds: Float = 1f,
    val fillMode: FillMode = FillMode.CONTAIN,
    val burnInProtection: Boolean = false,
    val showClock: Boolean = false,
    val clockSize: Float = 48f, // sp
    val clockPosition: ClockPosition = ClockPosition(),
    val keepScreenOn: Boolean = true,
    val fullscreen: Boolean = true,
    val shuffle: Boolean = true,
    val skipVideos: Boolean = true,
    val muted: Boolean = true,
    val startOnBoot: Boolean = false,
)

enum class FillMode { CONTAIN, COVER }
