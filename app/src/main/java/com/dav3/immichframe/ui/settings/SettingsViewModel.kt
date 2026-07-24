package com.dav3.immichframe.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dav3.immichframe.domain.model.FillMode
import com.dav3.immichframe.domain.model.SlideshowSettings
import com.dav3.immichframe.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository
) : ViewModel() {

    val settings: StateFlow<SlideshowSettings> = settingsRepo.slideshowSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SlideshowSettings())

    fun updateInterval(seconds: Int) = viewModelScope.launch {
        settingsRepo.setSlideshowSettings(settings.value.copy(intervalSeconds = seconds))
    }

    fun updateFillMode(mode: FillMode) = viewModelScope.launch {
        settingsRepo.setSlideshowSettings(settings.value.copy(fillMode = mode))
    }

    fun toggleKenBurns() = viewModelScope.launch {
        settingsRepo.setSlideshowSettings(settings.value.copy(kenBurns = !settings.value.kenBurns))
    }

    fun toggleClock() = viewModelScope.launch {
        settingsRepo.setSlideshowSettings(settings.value.copy(showClock = !settings.value.showClock))
    }

    fun toggleKeepScreenOn() = viewModelScope.launch {
        settingsRepo.setSlideshowSettings(settings.value.copy(keepScreenOn = !settings.value.keepScreenOn))
    }
}
