package com.dav3.immichframe.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dav3.immichframe.data.update.UpdateManager
import com.dav3.immichframe.data.update.UpdateState
import com.dav3.immichframe.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel
@Inject
constructor(
    private val updateManager: UpdateManager,
    private val settingsRepo: SettingsRepository,
) : ViewModel() {
    val updateState: StateFlow<UpdateState> = updateManager.state

    private val _updateDismissed = MutableStateFlow(false)
    val updateDismissed = _updateDismissed.asStateFlow()

    fun checkForUpdate() {
        viewModelScope.launch {
            val settings = settingsRepo.slideshowSettings.first()
            if (!settings.autoUpdate) return@launch
            updateManager.checkForUpdate()
        }
    }

    /** Manual check — ignores the autoUpdate setting. */
    fun checkForUpdateNow() {
        viewModelScope.launch {
            updateManager.checkForUpdate()
        }
    }

    fun installUpdate() {
        updateManager.installUpdate()
    }

    fun dismissUpdate() {
        _updateDismissed.value = true
    }

    fun isInstalledFromPlayStore(): Boolean = updateManager.isInstalledFromPlayStore()
}
