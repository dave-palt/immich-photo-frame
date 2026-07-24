package com.dav3.immichframe.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dav3.immichframe.data.remote.ImmichRepositoryImpl
import com.dav3.immichframe.domain.repository.ImmichRepository
import com.dav3.immichframe.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ConnectionState { IDLE, CONNECTING, SUCCESS, ERROR }

data class SetupUiState(
    val serverUrl: String = "",
    val apiKey: String = "",
    val connectionState: ConnectionState = ConnectionState.IDLE,
    val errorMessage: String? = null,
    val connectedEmail: String? = null
)

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val immichRepo: ImmichRepository,
    private val settingsRepo: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState

    init {
        viewModelScope.launch {
            val url = settingsRepo.serverUrl.first()
            val key = settingsRepo.apiKey.first()
            _uiState.value = _uiState.value.copy(serverUrl = url, apiKey = key)
        }
    }

    fun updateServerUrl(url: String) {
        _uiState.value = _uiState.value.copy(serverUrl = url, connectionState = ConnectionState.IDLE)
    }

    fun updateApiKey(key: String) {
        _uiState.value = _uiState.value.copy(apiKey = key, connectionState = ConnectionState.IDLE)
    }

    fun testConnection() {
        val state = _uiState.value
        if (state.serverUrl.isBlank() || state.apiKey.isBlank()) {
            _uiState.value = state.copy(
                connectionState = ConnectionState.ERROR,
                errorMessage = "Server URL and API key are required"
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                connectionState = ConnectionState.CONNECTING,
                errorMessage = null
            )

            settingsRepo.setServerUrl(state.serverUrl.trim().trimEnd('/'))
            settingsRepo.setApiKey(state.apiKey.trim())

            // Invalidate repo cache so it picks up new credentials
            (immichRepo as ImmichRepositoryImpl).invalidateCache()

            val pingResult = immichRepo.ping()
            if (pingResult.isFailure) {
                _uiState.value = _uiState.value.copy(
                    connectionState = ConnectionState.ERROR,
                    errorMessage = pingResult.exceptionOrNull()?.message ?: "Server unreachable"
                )
                return@launch
            }

            val userResult = immichRepo.validateApiKey()
            if (userResult.isFailure) {
                _uiState.value = _uiState.value.copy(
                    connectionState = ConnectionState.ERROR,
                    errorMessage = "API key rejected: ${userResult.exceptionOrNull()?.message}"
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                connectionState = ConnectionState.SUCCESS,
                connectedEmail = userResult.getOrThrow()
            )
        }
    }
}
