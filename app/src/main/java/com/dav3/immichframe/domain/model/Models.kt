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
    val lastModified: Long = 0,
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
    val showClock: Boolean = false,
    val clockSeconds: Boolean = false,
    val clockFormat: ClockFormat = ClockFormat.H24,
    val clockSize: Float = 48f, // sp
    val clockPosition: ClockPosition = ClockPosition(),
    val keepScreenOn: Boolean = true,
    val fullscreen: Boolean = true,
    val shuffle: Boolean = true,
    val skipVideos: Boolean = true,
    val muted: Boolean = true,
    val startOnBoot: Boolean = false,
    val launcherMode: Boolean = false,
    val bootVerified: Boolean = false,
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
    // Media Cache
    val autoSync: Boolean = true,
    val syncIntervalMinutes: Int = 30,
    // Night Mode (brightness-based display schedule)
    val nightMode: Boolean = false,
    val nightModeStart: Int = 1320, // minutes since midnight (22:00)
    val nightModeEnd: Int = 420, // minutes since midnight (07:00)
    val nightModeBrightness: Int = 0, // 0-100 percent
) {
    /**
     * Whether the current wall-clock time falls inside the configured night-mode
     * window. Handles wrap-around (start > end means overnight, e.g. 22:00→07:00).
     */
    fun isNightModeActive(hourMinute: Int): Boolean {
        val now = hourMinute
        return if (nightModeStart <= nightModeEnd) {
            // Same-day window, e.g. 09:00→17:00
            now in nightModeStart until nightModeEnd
        } else {
            // Overnight window, e.g. 22:00→07:00
            now >= nightModeStart || now < nightModeEnd
        }
    }

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

enum class ClockFormat { H12, H24 }

// Media Cache Models

data class CachedAsset(
    val id: String,
    val albumId: String,
    val type: AssetType,
    val filePath: String,
    val thumbnailPath: String?,
    val fileSize: Long,
    val checksum: String?,
    val lastModified: Long,
    val cachedAt: Long,
)

data class AlbumSyncState(
    val albumId: String,
    val lastSyncedAt: Long = 0,
    val lastCursor: String? = null,
    val assetCount: Int = 0,
)

// Sync Progress Models

data class SyncProgress(
    val albumIds: List<String> = emptyList(),
    val currentAlbum: String = "",
    val phase: Phase = Phase.IDLE,
    val totalAssets: Int = 0,
    val processedAssets: Int = 0,
    val currentAsset: String = "",
) {
    enum class Phase {
        IDLE,
        FETCHING_METADATA,
        DOWNLOADING,
        PROCESSING,
        COMPLETE,
        ERROR,
    }

    val progressPercent: Float
        get() = if (totalAssets > 0) processedAssets.toFloat() / totalAssets else 0f
}

sealed class SyncResult {
    data class Success(
        val added: Int,
        val updated: Int,
        val removed: Int,
        val failed: Int,
        val errors: List<String>,
    ) : SyncResult()

    data class Failure(val error: String) : SyncResult()
}
