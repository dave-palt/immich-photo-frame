package com.dav3.immichframe.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dav3.immichframe.data.remote.ImmichRepositoryImpl
import com.dav3.immichframe.domain.model.FillMode
import com.dav3.immichframe.domain.model.SlideshowSettings
import com.dav3.immichframe.domain.repository.ImmichRepository
import com.dav3.immichframe.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

data class SettingsUiState(
    val settings: SlideshowSettings = SlideshowSettings(),
    val serverUrl: String = "",
    val apiKey: String = "",
)

@HiltViewModel
class SettingsViewModel
@Inject
constructor(
    private val settingsRepo: SettingsRepository,
    private val immichRepo: ImmichRepository,
) : ViewModel() {
    val uiState: StateFlow<SettingsUiState> =
        combine(
            settingsRepo.slideshowSettings,
            settingsRepo.serverUrl,
            settingsRepo.apiKey,
        ) { slideshow, url, key ->
            SettingsUiState(slideshow, url, key)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    private val updateMutex = Mutex()

    /** Reads the LATEST persisted state from DataStore (not stale StateFlow cache). */
    private fun update(block: (SlideshowSettings) -> SlideshowSettings) = viewModelScope.launch {
        updateMutex.withLock {
            val current = settingsRepo.slideshowSettings.first()
            settingsRepo.setSlideshowSettings(block(current))
        }
    }

    fun updateInterval(seconds: Int) = update { it.copy(intervalSeconds = seconds) }

    fun updateFillMode(mode: FillMode) = update { it.copy(fillMode = mode) }

    fun toggleBurnInProtection() = update { it.copy(burnInProtection = !it.burnInProtection) }

    fun toggleClock() = update { it.copy(showClock = !it.showClock) }

    fun updateClockSize(size: Float) = update { it.copy(clockSize = size) }

    fun toggleKeepScreenOn() = update { it.copy(keepScreenOn = !it.keepScreenOn) }

    fun toggleFullscreen() = update { it.copy(fullscreen = !it.fullscreen) }

    fun toggleShuffle() = update { it.copy(shuffle = !it.shuffle) }

    fun toggleSkipVideos() = update { it.copy(skipVideos = !it.skipVideos) }

    fun toggleMuted() = update { it.copy(muted = !it.muted) }

    fun toggleStartOnBoot() = update { it.copy(startOnBoot = !it.startOnBoot) }

    fun toggleAutoUpdate() = update { it.copy(autoUpdate = !it.autoUpdate) }

    fun toggleClockSnapToGrid() = update { it.copy(clockSnapToGrid = !it.clockSnapToGrid) }

    fun toggleAdaptiveBackground() = update { it.copy(adaptiveBackground = !it.adaptiveBackground) }

    fun updateServerUrl(url: String) = viewModelScope.launch {
        settingsRepo.setServerUrl(url.trim().trimEnd('/'))
        (immichRepo as ImmichRepositoryImpl).invalidateCache()
    }

    fun updateApiKey(key: String) = viewModelScope.launch {
        settingsRepo.setApiKey(key.trim())
        (immichRepo as ImmichRepositoryImpl).invalidateCache()
    }

    fun resetAll() = viewModelScope.launch {
        settingsRepo.clearAll()
    }
}
