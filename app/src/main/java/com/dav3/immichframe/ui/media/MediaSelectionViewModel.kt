package com.dav3.immichframe.ui.media

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dav3.immichframe.domain.model.Asset
import com.dav3.immichframe.domain.repository.ImmichRepository
import com.dav3.immichframe.domain.repository.MediaCacheRepository
import com.dav3.immichframe.domain.repository.SettingsRepository
import com.dav3.immichframe.ui.slideshow.toAsset
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MediaSelectionUiState(
    val assets: List<Asset> = emptyList(),
    val toggledIds: Set<String> = emptySet(),
    val newItemsShown: Boolean = true,
    val isLoading: Boolean = false,
    val error: String? = null,
) {
    /** Whether a given asset is currently marked as "shown" (visible). */
    fun isShown(assetId: String): Boolean = if (newItemsShown) assetId !in toggledIds else assetId in toggledIds

    val shownCount: Int
        get() = assets.count { isShown(it.id) }

    val totalCount: Int
        get() = assets.size
}

@HiltViewModel
class MediaSelectionViewModel
@Inject
constructor(
    private val immichRepo: ImmichRepository,
    private val cacheRepo: MediaCacheRepository,
    private val settingsRepo: SettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MediaSelectionUiState())
    val uiState: StateFlow<MediaSelectionUiState> = _uiState

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val albumIds = settingsRepo.selectedAlbumIds.first()
            val toggledIds = settingsRepo.mediaSelectionToggledIds.first()
            val newItemsShown = settingsRepo.mediaSelectionNewItemsShown.first()

            if (albumIds.isEmpty()) {
                _uiState.value = MediaSelectionUiState(
                    isLoading = false,
                    error = "No albums selected",
                )
                return@launch
            }

            // Load assets — cache-first, fall back to network
            val assets = mutableListOf<Asset>()
            for (id in albumIds) {
                cacheRepo.getCachedAssets(id).fold(
                    onSuccess = { cached -> assets.addAll(cached.map { it.toAsset() }) },
                    onFailure = { },
                )
            }

            if (assets.isEmpty()) {
                // Cold start — fetch from network
                for (id in albumIds) {
                    immichRepo.getAlbumAssets(id).fold(
                        onSuccess = { assets.addAll(it) },
                        onFailure = { },
                    )
                }
            }

            _uiState.value = MediaSelectionUiState(
                assets = assets.sortedByDescending { it.lastModified },
                toggledIds = toggledIds,
                newItemsShown = newItemsShown,
                isLoading = false,
            )
        }
    }

    /** Toggles an asset's shown/hidden state. */
    fun toggleAsset(assetId: String) {
        val current = _uiState.value
        val newToggled = if (assetId in current.toggledIds) {
            current.toggledIds - assetId
        } else {
            current.toggledIds + assetId
        }
        _uiState.value = current.copy(toggledIds = newToggled)
        viewModelScope.launch {
            settingsRepo.setMediaSelectionToggledIds(newToggled)
        }
    }

    /**
     * Flips the "new items shown" mode. When toggled, the stored [toggledIds]
     * set is recomputed so the **current visible selection is preserved** —
     * only the default for *future* new media changes.
     */
    fun toggleNewItemsShown() {
        val current = _uiState.value
        val newMode = !current.newItemsShown

        // Recompute toggled set against current assets to preserve visible state
        val currentlyShownIds = current.assets
            .filter { current.isShown(it.id) }
            .map { it.id }
            .toSet()

        // In the new mode, toggledIds represents the opposite of what's shown
        val newToggled = if (newMode) {
            // newMode=true: shown by default, toggled=hidden ones
            current.assets.map { it.id }.toSet() - currentlyShownIds
        } else {
            // newMode=false: hidden by default, toggled=shown ones
            currentlyShownIds
        }

        _uiState.value = current.copy(
            newItemsShown = newMode,
            toggledIds = newToggled,
        )
        viewModelScope.launch {
            settingsRepo.setMediaSelectionNewItemsShown(newMode)
            settingsRepo.setMediaSelectionToggledIds(newToggled)
        }
    }

    /** Marks all currently loaded assets as shown. */
    fun selectAll() {
        val current = _uiState.value
        val newToggled = if (current.newItemsShown) {
            // shown by default → clear toggled (nothing hidden)
            emptySet()
        } else {
            // hidden by default → toggle all to shown
            current.assets.map { it.id }.toSet()
        }
        _uiState.value = current.copy(toggledIds = newToggled)
        viewModelScope.launch {
            settingsRepo.setMediaSelectionToggledIds(newToggled)
        }
    }

    /** Marks all currently loaded assets as hidden. */
    fun selectNone() {
        val current = _uiState.value
        val newToggled = if (current.newItemsShown) {
            // shown by default → toggle all to hidden
            current.assets.map { it.id }.toSet()
        } else {
            // hidden by default → clear toggled (nothing shown)
            emptySet()
        }
        _uiState.value = current.copy(toggledIds = newToggled)
        viewModelScope.launch {
            settingsRepo.setMediaSelectionToggledIds(newToggled)
        }
    }

    fun thumbnailUrl(assetId: String): String = immichRepo.thumbnailUrl(assetId)
}
