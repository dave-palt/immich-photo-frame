package com.dav3.immichframe.ui.slideshow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dav3.immichframe.data.sync.SyncScheduler
import com.dav3.immichframe.domain.model.Asset
import com.dav3.immichframe.domain.model.AssetType
import com.dav3.immichframe.domain.model.ClockPosition
import com.dav3.immichframe.domain.repository.ImmichRepository
import com.dav3.immichframe.domain.repository.MediaCacheRepository
import com.dav3.immichframe.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class SlideshowUiState(
    val assets: List<Asset> = emptyList(),
    val currentIndex: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
    /**
     * Set when the selected album(s) no longer exist on the server. The UI
     * should navigate back to album selection so the user can pick again.
     */
    val albumGone: Boolean = false,
)

@HiltViewModel
class SlideshowViewModel
@Inject
constructor(
    private val immichRepo: ImmichRepository,
    private val cacheRepo: MediaCacheRepository,
    private val settingsRepo: SettingsRepository,
    private val syncScheduler: SyncScheduler,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SlideshowUiState())
    val uiState: StateFlow<SlideshowUiState> = _uiState

    /**
     * Asset ID → local cached file path. Populated in [load] so that
     * [imageUrl] / [videoUrl] can return a `file://` URI for offline display
     * instead of a network URL. Falls back to network for cache misses.
     */
    private val localFilePaths = mutableMapOf<String, String>()

    /**
     * Asset ID → local cached thumbnail path. Populated in [load] so that
     * [thumbnailUrl] can return a `file://` URI for the small JPEG thumbnail
     * (used by adaptive background color extraction) without a DB lookup.
     */
    private val localThumbnailPaths = mutableMapOf<String, String>()

    val settings =
        settingsRepo.slideshowSettings
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                com.dav3.immichframe.domain.model
                    .SlideshowSettings(),
            )

    val onboardingSteps: StateFlow<Set<String>> =
        settingsRepo.onboardingCompletedSteps
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun markStepCompleted(stepId: String) {
        viewModelScope.launch { settingsRepo.markOnboardingStepCompleted(stepId) }
    }

    fun skipOnboarding(stepIds: List<String>) {
        viewModelScope.launch {
            stepIds.forEach { settingsRepo.markOnboardingStepCompleted(it) }
        }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, albumGone = false)
            val s = settingsRepo.slideshowSettings.first()
            val albumIds = settingsRepo.selectedAlbumIds.first()
            if (albumIds.isEmpty()) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "No albums selected")
                return@launch
            }

            val toggledIds = settingsRepo.mediaSelectionToggledIds.first()
            val newItemsShown = settingsRepo.mediaSelectionNewItemsShown.first()

            // First, try to load from cache (offline-capable fast path)
            val cachedAssets = mutableListOf<Asset>()
            for (id in albumIds) {
                cacheRepo.getCachedAssets(id).fold(
                    onSuccess = { assets -> cachedAssets.addAll(assets.map { it.toAsset() }) },
                    onFailure = { /* cache miss is non-fatal */ },
                )
            }

            if (cachedAssets.isNotEmpty()) {
                // Resolve local file paths up front so imageUrl/videoUrl can
                // serve offline `file://` URIs without per-frame DB lookups.
                localFilePaths.clear()
                localFilePaths.putAll(
                    cacheRepo.getAssetFilePaths(cachedAssets.map { it.id }),
                )
                localThumbnailPaths.clear()
                localThumbnailPaths.putAll(
                    cacheRepo.getAssetThumbnailPaths(cachedAssets.map { it.id }),
                )

                // Show cached assets immediately
                val videoCount = cachedAssets.count { it.type == AssetType.VIDEO }
                val imageCount = cachedAssets.count { it.type == AssetType.IMAGE }
                android.util.Log.d("SlideshowLoad", "Cache: $imageCount images, $videoCount videos, skipVideos=${s.skipVideos}")
                val filteredAssets = applyMediaSelection(cachedAssets, toggledIds, newItemsShown)
                    .let { if (s.skipVideos) it.filter { it.type == AssetType.IMAGE } else it }
                val ordered = if (s.shuffle) filteredAssets.shuffled() else filteredAssets
                _uiState.value = if (ordered.isNotEmpty()) {
                    SlideshowUiState(assets = ordered, currentIndex = 0, isLoading = false)
                } else {
                    SlideshowUiState(isLoading = false, error = "No images found in cache")
                }

                // Kick off background sync via WorkManager (worker handles download + reconcile)
                if (s.autoSync) {
                    syncScheduler.syncNow(albumIds)
                }
            } else {
                // Cold start: no cache yet — fetch metadata from network for immediate display
                val allAssets = mutableListOf<Asset>()
                val errors = mutableListOf<String>()
                var albumGone = false
                for (id in albumIds) {
                    immichRepo.getAlbumAssets(id).fold(
                        onSuccess = { allAssets.addAll(it) },
                        onFailure = {
                            // Immich returns 404 for a deleted album. Treat that
                            // as permanent — we'll bounce back to album selection.
                            if (isAlbumGone(it)) albumGone = true
                            errors.add("${id.take(8)}: ${it.message ?: "unknown"}")
                        },
                    )
                }

                if (albumGone) {
                    // Album deleted on server — clear selection and signal UI
                    settingsRepo.setSelectedAlbumIds(emptyList())
                    _uiState.value = SlideshowUiState(isLoading = false, albumGone = true)
                    return@launch
                }

                val filteredAssets = applyMediaSelection(allAssets, toggledIds, newItemsShown)
                    .let { if (s.skipVideos) it.filter { it.type == AssetType.IMAGE } else it }
                android.util.Log.d("SlideshowLoad", "Network: ${allAssets.count { it.type == AssetType.IMAGE }} images, ${allAssets.count { it.type == AssetType.VIDEO }} videos, skipVideos=${s.skipVideos}")
                val ordered = if (s.shuffle) filteredAssets.shuffled() else filteredAssets

                _uiState.value = when {
                    ordered.isNotEmpty() -> {
                        SlideshowUiState(assets = ordered, currentIndex = 0, isLoading = false)
                    }
                    errors.isNotEmpty() -> {
                        SlideshowUiState(isLoading = false, error = "Asset load failed:\n${errors.joinToString("\n")}")
                    }
                    else -> {
                        SlideshowUiState(isLoading = false, error = "No images found")
                    }
                }

                // Populate cache in background (worker downloads files + writes DB)
                if (ordered.isNotEmpty()) {
                    syncScheduler.syncNow(albumIds)
                }
            }
        }
    }

    fun next() {
        val s = _uiState.value
        if (s.assets.isNotEmpty()) {
            _uiState.value = s.copy(currentIndex = (s.currentIndex + 1) % s.assets.size)
        }
    }

    fun previous() {
        val s = _uiState.value
        if (s.assets.isNotEmpty()) {
            _uiState.value = s.copy(currentIndex = (s.currentIndex - 1 + s.assets.size) % s.assets.size)
        }
    }

    fun setClockPosition(pos: ClockPosition) {
        viewModelScope.launch {
            settingsRepo.setSlideshowSettings(settings.value.copy(clockPosition = pos))
        }
    }

    fun setMuted(value: Boolean) {
        viewModelScope.launch {
            settingsRepo.setSlideshowSettings(settings.value.copy(muted = value))
        }
    }

    /**
     * Returns a display URL for an image asset. If the asset is cached
     * locally on disk, returns a `file://` URI (works offline). Otherwise
     * returns the network URL (requires server connectivity).
     *
     * GIFs are routed to the `/original` endpoint (via [ImmichRepository.imageUrl])
     * so Coil's GifDecoder receives the raw animated bytes. Other images use
     * the transcoded preview thumbnail.
     */
    fun imageUrl(asset: Asset): String {
        localFilePaths[asset.id]?.let { path ->
            if (File(path).exists()) return "file://$path"
        }
        return immichRepo.imageUrl(asset.id, asset.originalMimeType)
    }

    fun videoUrl(assetId: String): String {
        localFilePaths[assetId]?.let { path ->
            if (File(path).exists()) return "file://$path"
        }
        return immichRepo.videoUrl(assetId)
    }

    /**
     * Small JPEG thumbnail URL — used for adaptive background color
     * extraction. Always prefers the locally cached thumbnail (works for
     * GIFs, videos, and regular images alike — Coil only needs a few pixels
     * to extract a dominant color, so the tiny transcode is perfect).
     */
    fun thumbnailUrl(assetId: String): String {
        localThumbnailPaths[assetId]?.let { path ->
            if (File(path).exists()) return "file://$path"
        }
        return immichRepo.thumbnailUrl(assetId)
    }
}

/**
 * Returns true if the exception indicates the album no longer exists on the
 * server (HTTP 404), as opposed to a transient network/server error.
 */
private fun isAlbumGone(throwable: Throwable): Boolean {
    val msg = throwable.message.orEmpty()
    // Retrofit/OkHttp surfaces HTTP status in the message for HttpException;
    // for an IOException (server unreachable) the message won't contain a code.
    return msg.contains("404") || msg.contains("Not Found", ignoreCase = true)
}

fun com.dav3.immichframe.domain.model.CachedAsset.toAsset(): Asset = Asset(
    id = id,
    type = type,
    lastModified = lastModified,
    originalMimeType = originalMimeType,
    exif = com.dav3.immichframe.domain.model.AssetExif(
        dateTimeOriginal = exifDateTimeOriginal,
        description = exifDescription,
        city = exifCity,
        state = exifState,
        country = exifCountry,
    ),
    tags = tags,
)

/**
 * Computes which assets are visible based on the media-selection state.
 *
 * - [newItemsShown] = true (default): all assets start **shown**. IDs in
 *   [toggledIds] are the ones the user tapped to **hide**.
 * - [newItemsShown] = false: all assets start **hidden**. IDs in
 *   [toggledIds] are the ones the user tapped to **show**.
 *
 * This is used by both the slideshow playback and the media-selection grid
 * so the two views stay consistent.
 */
fun applyMediaSelection(
    assets: List<Asset>,
    toggledIds: Set<String>,
    newItemsShown: Boolean,
): List<Asset> = if (newItemsShown) {
    assets.filter { it.id !in toggledIds }
} else {
    assets.filter { it.id in toggledIds }
}
