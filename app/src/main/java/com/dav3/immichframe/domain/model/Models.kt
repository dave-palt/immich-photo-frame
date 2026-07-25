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
    val autoUpdate: Boolean = true,
    val clockSnapToGrid: Boolean = true,
    val adaptiveBackground: Boolean = false,
    // Ken Burns
    val photoAnimations: Boolean = false,
    val animZoomIn: Boolean = true,
    val animZoomOut: Boolean = true,
    val animPanLeft: Boolean = true,
    val animPanRight: Boolean = true,
    val animPanUp: Boolean = true,
    val animPanDown: Boolean = true,
) {
    /** Non-random enabled animations. Empty = no animation. */
    val enabledAnimations: List<PhotoAnimation>
        get() = PhotoAnimation.entries.filter { anim ->
            when (anim) {
                PhotoAnimation.ZOOM_IN -> animZoomIn
                PhotoAnimation.ZOOM_OUT -> animZoomOut
                PhotoAnimation.PAN_LEFT -> animPanLeft
                PhotoAnimation.PAN_RIGHT -> animPanRight
                PhotoAnimation.PAN_UP -> animPanUp
                PhotoAnimation.PAN_DOWN -> animPanDown
            }
        }
}

enum class PhotoAnimation {
    ZOOM_IN,
    ZOOM_OUT,
    PAN_LEFT,
    PAN_RIGHT,
    PAN_UP,
    PAN_DOWN,
}

enum class FillMode { CONTAIN, COVER }
