package com.dav3.immichframe.ui.slideshow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dav3.immichframe.domain.model.Asset
import com.dav3.immichframe.domain.repository.ImmichRepository
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
    private val settingsRepo: SettingsRepository,
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
            val albumIds = settingsRepo.selectedAlbumIds.first()
            if (albumIds.isEmpty()) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "No albums selected")
                return@launch
            }

            val allAssets = mutableListOf<Asset>()
            for (id in albumIds) {
                immichRepo.getAlbumAssets(id).onSuccess { allAssets.addAll(it) }
            }

            _uiState.value =
                if (allAssets.isEmpty()) {
                    SlideshowUiState(isLoading = false, error = "No images found")
                } else {
                    SlideshowUiState(assets = allAssets.shuffled(), currentIndex = 0, isLoading = false)
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

    fun imageUrl(assetId: String): String = immichRepo.imageUrl(assetId)
}
