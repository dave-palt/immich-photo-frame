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
import javax.inject.Inject

data class SlideshowUiState(
    val assets: List<Asset> = emptyList(),
    val currentIndex: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
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

    val settings =
        settingsRepo.slideshowSettings
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                com.dav3.immichframe.domain.model
                    .SlideshowSettings(),
            )

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val s = settingsRepo.slideshowSettings.first()
            val albumIds = settingsRepo.selectedAlbumIds.first()
            if (albumIds.isEmpty()) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "No albums selected")
                return@launch
            }

            // First, try to load from cache (offline-capable fast path)
            val cachedAssets = mutableListOf<Asset>()
            for (id in albumIds) {
                cacheRepo.getCachedAssets(id).fold(
                    onSuccess = { assets -> cachedAssets.addAll(assets.map { it.toAsset() }) },
                    onFailure = { /* cache miss is non-fatal */ },
                )
            }

            if (cachedAssets.isNotEmpty()) {
                // Show cached assets immediately
                val videoCount = cachedAssets.count { it.type == AssetType.VIDEO }
                val imageCount = cachedAssets.count { it.type == AssetType.IMAGE }
                android.util.Log.d("SlideshowLoad", "Cache: $imageCount images, $videoCount videos, skipVideos=${s.skipVideos}")
                val filteredAssets = if (s.skipVideos) cachedAssets.filter { it.type == AssetType.IMAGE } else cachedAssets
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
                for (id in albumIds) {
                    immichRepo.getAlbumAssets(id).fold(
                        onSuccess = { allAssets.addAll(it) },
                        onFailure = { errors.add("${id.take(8)}: ${it.message ?: "unknown"}") },
                    )
                }

                val filteredAssets = if (s.skipVideos) allAssets.filter { it.type == AssetType.IMAGE } else allAssets
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

    fun imageUrl(assetId: String): String = immichRepo.imageUrl(assetId)

    fun videoUrl(assetId: String): String = immichRepo.videoUrl(assetId)
}

fun com.dav3.immichframe.domain.model.CachedAsset.toAsset(): Asset = Asset(
    id = id,
    type = type,
    lastModified = lastModified,
)
