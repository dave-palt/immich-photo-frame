package com.dav3.immichframe.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

enum class ConnectionState { IDLE, CONNECTING, SUCCESS, ERROR }

data class SetupUiState(
    val useHttps: Boolean = true,
    val domain: String = "",
    val apiKey: String = "",
    val connectionState: ConnectionState = ConnectionState.IDLE,
    val errorMessage: String? = null,
    val connectedEmail: String? = null,
) {
    val serverUrl: String get() = "${if (useHttps) "https" else "http"}://$domain".removeSuffix("/")
}

@HiltViewModel
class SetupViewModel
@Inject
constructor(
    private val immichRepo: ImmichRepository,
    private val settingsRepo: SettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState

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
        viewModelScope.launch {
            val url = settingsRepo.serverUrl.first()
            val key = settingsRepo.apiKey.first()
            val (https, domain) = parseUrl(url)
            _uiState.value = _uiState.value.copy(useHttps = https, domain = domain, apiKey = key)
        }
    }

    fun updateProtocol(https: Boolean) {
        _uiState.value = _uiState.value.copy(useHttps = https, connectionState = ConnectionState.IDLE)
    }

    fun updateDomain(input: String) {
        // Parse pasted URLs that include protocol — strip it, set dropdown accordingly
        val (https, domain) = parseUrl(input)
        _uiState.value = _uiState.value.copy(useHttps = https, domain = domain, connectionState = ConnectionState.IDLE)
    }

    private fun parseUrl(input: String): Pair<Boolean, String> {
        val trimmed = input.trim().trimEnd('/')
        return when {
            trimmed.startsWith("https://") -> true to trimmed.removePrefix("https://")
            trimmed.startsWith("http://") -> false to trimmed.removePrefix("http://")
            else -> _uiState.value.useHttps to trimmed
        }
    }

    fun updateApiKey(key: String) {
        _uiState.value = _uiState.value.copy(apiKey = key, connectionState = ConnectionState.IDLE)
    }

    fun testConnection() {
        val state = _uiState.value
        if (state.domain.isBlank() || state.apiKey.isBlank()) {
            _uiState.value =
                state.copy(
                    connectionState = ConnectionState.ERROR,
                    errorMessage = "Server URL and API key are required",
                )
            return
        }

        viewModelScope.launch {
            _uiState.value =
                _uiState.value.copy(
                    connectionState = ConnectionState.CONNECTING,
                    errorMessage = null,
                )

            settingsRepo.setServerUrl(state.serverUrl)
            settingsRepo.setApiKey(state.apiKey.trim())

            // Invalidate repo cache so it picks up new credentials
            immichRepo.invalidateCache()

            val pingResult = immichRepo.ping()
            if (pingResult.isFailure) {
                _uiState.value =
                    _uiState.value.copy(
                        connectionState = ConnectionState.ERROR,
                        errorMessage = pingResult.exceptionOrNull()?.message ?: "Server unreachable",
                    )
                return@launch
            }

            val userResult = immichRepo.validateApiKey()
            if (userResult.isFailure) {
                _uiState.value =
                    _uiState.value.copy(
                        connectionState = ConnectionState.ERROR,
                        errorMessage = "API key rejected: ${userResult.exceptionOrNull()?.message}",
                    )
                return@launch
            }

            _uiState.value =
                _uiState.value.copy(
                    connectionState = ConnectionState.SUCCESS,
                    connectedEmail = userResult.getOrThrow(),
                )
        }
    }
}
