package com.dav3.immichframe.ui.albums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dav3.immichframe.domain.model.Album
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

data class AlbumSelectionUiState(
    val albums: List<Album> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val error: String? = null,
    /**
     * True when the server was reached successfully but returned zero albums.
     * Distinct from [error] (server unreachable) — shows a dedicated message
     * instead of a retry button.
     */
    val noAlbumsAvailable: Boolean = false,
)

@HiltViewModel
class AlbumSelectionViewModel
@Inject
constructor(
    private val immichRepo: ImmichRepository,
    private val settingsRepo: SettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AlbumSelectionUiState())
    val uiState: StateFlow<AlbumSelectionUiState> = _uiState

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

    init {
        loadAlbums()
    }

    private fun loadAlbums() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = immichRepo.getAlbums()
            val savedSelections = settingsRepo.selectedAlbumIds.first().toSet()
            _uiState.value =
                result.fold(
                    onSuccess = { albums ->
                        if (albums.isEmpty()) {
                            AlbumSelectionUiState(
                                isLoading = false,
                                noAlbumsAvailable = true,
                            )
                        } else {
                            AlbumSelectionUiState(
                                albums = albums,
                                selectedIds = savedSelections,
                                isLoading = false,
                            )
                        }
                    },
                    onFailure = {
                        AlbumSelectionUiState(
                            isLoading = false,
                            error = it.message ?: "Failed to load albums",
                        )
                    },
                )
        }
    }

    fun toggleAlbum(id: String) {
        val current = _uiState.value.selectedIds
        _uiState.value =
            _uiState.value.copy(
                selectedIds = if (id in current) current - id else current + id,
            )
    }

    fun retry() = loadAlbums()

    fun thumbnailUrl(assetId: String?): String? = assetId?.let { immichRepo.thumbnailUrl(it) }

    fun startSlideshow() {
        viewModelScope.launch {
            settingsRepo.setSelectedAlbumIds(_uiState.value.selectedIds.toList())
        }
    }
}
