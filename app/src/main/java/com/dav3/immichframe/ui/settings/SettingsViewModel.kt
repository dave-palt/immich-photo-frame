package com.dav3.immichframe.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dav3.immichframe.data.sync.SyncScheduler
import com.dav3.immichframe.domain.model.ClockPosition
import com.dav3.immichframe.domain.model.FillMode
import com.dav3.immichframe.domain.model.PermissionCheckResult
import com.dav3.immichframe.domain.model.PhotoAnimation
import com.dav3.immichframe.domain.model.SlideshowSettings
import com.dav3.immichframe.domain.repository.ImmichRepository
import com.dav3.immichframe.domain.repository.SettingsRepository
import com.dav3.immichframe.domain.system.openLauncherSettings
import com.dav3.immichframe.domain.system.setLauncherModeEnabled
import com.dav3.immichframe.ui.onboarding.TourSteps
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
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
    val permissionStatus: PermissionCheckResult? = null,
    val permissionCheckInProgress: Boolean = false,
)

@HiltViewModel
class SettingsViewModel
@Inject
constructor(
    private val settingsRepo: SettingsRepository,
    private val immichRepo: ImmichRepository,
    private val syncScheduler: SyncScheduler,
) : ViewModel() {
    private val permissionCheckingFlow = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> =
        combine(
            settingsRepo.slideshowSettings,
            settingsRepo.serverUrl,
            settingsRepo.apiKey,
            settingsRepo.permissionStatus,
            permissionCheckingFlow,
        ) { slideshow, url, key, perms, checking ->
            SettingsUiState(slideshow, url, key, perms, checking)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

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

    fun resetOnboarding() {
        viewModelScope.launch { settingsRepo.resetOnboarding() }
    }

    /**
     * Re-probe all API endpoints and update the stored permission status.
     * Called when the Settings screen opens and via the "Re-check" button.
     */
    fun recheckPermissions() {
        viewModelScope.launch {
            permissionCheckingFlow.value = true
            immichRepo.checkPermissions().onSuccess { result ->
                settingsRepo.setPermissionStatus(result)
                enforceDegradedSettings(result)
            }.onFailure {
                // Network error — keep the previous status, don't wipe it
            }
            permissionCheckingFlow.value = false
        }
    }

    /**
     * Force-off any setting gated by a missing optional permission.
     * Mirrors the logic in SetupViewModel.
     */
    private suspend fun enforceDegradedSettings(result: PermissionCheckResult) {
        val currentSettings = settingsRepo.slideshowSettings.first()
        var newSettings = currentSettings
        for (perm in result.missingOptional) {
            when (perm.gatedSettingKey) {
                "skip_videos" -> newSettings = newSettings.copy(skipVideos = true)
            }
        }
        if (newSettings != currentSettings) {
            settingsRepo.setSlideshowSettings(newSettings)
        }
    }

    fun resetOnboardingForSettings() {
        viewModelScope.launch {
            settingsRepo.resetOnboardingForScreen(TourSteps.SETTINGS.map { it.id })
        }
    }

    private val updateMutex = Mutex()

    /** Reads the LATEST persisted state from DataStore (not stale StateFlow cache). */
    private fun update(block: (SlideshowSettings) -> SlideshowSettings) = viewModelScope.launch {
        updateMutex.withLock {
            val current = settingsRepo.slideshowSettings.first()
            val newSettings = block(current)
            settingsRepo.setSlideshowSettings(newSettings)

            // Update sync schedule when autoSync or interval changes
            if (newSettings.autoSync != current.autoSync || newSettings.syncIntervalMinutes != current.syncIntervalMinutes) {
                if (newSettings.autoSync) {
                    syncScheduler.schedulePeriodicSync()
                } else {
                    syncScheduler.cancelPeriodicSync()
                }
            }
        }
    }

    fun updateInterval(seconds: Int) = update { it.copy(intervalSeconds = seconds) }

    fun updateFillMode(mode: FillMode) = update { it.copy(fillMode = mode) }

    fun toggleClock() = update {
        val newShowClock = !it.showClock
        it.copy(
            showClock = newShowClock,
            // Reset to center when enabling
            clockPosition = if (newShowClock) ClockPosition(0.5f, 0.5f) else it.clockPosition,
        )
    }

    fun updateClockSize(size: Float) = update { it.copy(clockSize = size) }

    fun toggleKeepScreenOn() = update { it.copy(keepScreenOn = !it.keepScreenOn) }

    fun toggleFullscreen() = update { it.copy(fullscreen = !it.fullscreen) }

    fun toggleShuffle() = update { it.copy(shuffle = !it.shuffle) }

    fun toggleSkipVideos() = update { it.copy(skipVideos = !it.skipVideos) }

    fun toggleMuted() = update { it.copy(muted = !it.muted) }

    fun toggleStartOnBoot() = update {
        it.copy(startOnBoot = !it.startOnBoot, bootVerified = false)
    }

    fun toggleLauncherMode(context: Context) {
        val newEnabled = !uiState.value.settings.launcherMode
        setLauncherModeEnabled(context, newEnabled)
        update { it.copy(launcherMode = newEnabled) }
        // When enabling, show the home-chooser so the user can pick this app
        // as the default launcher.
        if (newEnabled) {
            openLauncherSettings(context)
        }
    }

    fun toggleAutoUpdate() = update { it.copy(autoUpdate = !it.autoUpdate) }

    fun toggleAutoSync() = update { it.copy(autoSync = !it.autoSync) }

    fun updateSyncInterval(minutes: Int) = update { it.copy(syncIntervalMinutes = minutes) }

    fun toggleNightMode() = update { it.copy(nightMode = !it.nightMode) }

    fun updateNightModeStart(minutes: Int) = update { it.copy(nightModeStart = minutes) }

    fun updateNightModeEnd(minutes: Int) = update { it.copy(nightModeEnd = minutes) }

    fun updateNightModeBrightness(percent: Int) = update { it.copy(nightModeBrightness = percent.coerceIn(0, 100)) }

    fun toggleClockSnapToGrid() = update { it.copy(clockSnapToGrid = !it.clockSnapToGrid) }

    fun toggleAdaptiveBackground() = update { it.copy(adaptiveBackground = !it.adaptiveBackground) }

    fun togglePhotoAnimations() = update { it.copy(photoAnimations = !it.photoAnimations) }

    fun toggleAnimation(anim: PhotoAnimation) = update {
        when (anim) {
            PhotoAnimation.ZOOM_IN -> it.copy(animZoomIn = !it.animZoomIn)
            PhotoAnimation.ZOOM_OUT -> it.copy(animZoomOut = !it.animZoomOut)
            PhotoAnimation.PAN_LEFT -> it.copy(animPanLeft = !it.animPanLeft)
            PhotoAnimation.PAN_RIGHT -> it.copy(animPanRight = !it.animPanRight)
            PhotoAnimation.PAN_UP -> it.copy(animPanUp = !it.animPanUp)
            PhotoAnimation.PAN_DOWN -> it.copy(animPanDown = !it.animPanDown)
        }
    }

    fun updateServerUrl(url: String) = viewModelScope.launch {
        settingsRepo.setServerUrl(url.trim().trimEnd('/'))
        immichRepo.invalidateCache()
    }

    fun updateApiKey(key: String) = viewModelScope.launch {
        settingsRepo.setApiKey(key.trim())
        immichRepo.invalidateCache()
    }

    fun resetAll() = viewModelScope.launch {
        settingsRepo.clearAll()
    }

    fun syncNow() = viewModelScope.launch {
        val settings = settingsRepo.slideshowSettings.first()
        val albumIds = settingsRepo.selectedAlbumIds.first()
        syncScheduler.syncNow(albumIds)
    }
}
