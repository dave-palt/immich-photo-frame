package com.dav3.immichframe.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dav3.immichframe.domain.repository.ImmichRepository
import com.dav3.immichframe.domain.repository.OAuthStartResult
import com.dav3.immichframe.domain.repository.ServerInfo
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

/**
 * Which auth method the user is using on the setup screen.
 */
enum class AuthMode {
    /** Email/password login → key generation. */
    GENERATE,

    /** Manual API-key paste. */
    MANUAL_KEY,
}

/**
 * The setup sub-step — drives the UI layout.
 */
enum class SetupStep {
    /** User is entering/validating the server domain. */
    DOMAIN,

    /** Server validated, now choosing auth method. */
    AUTH,
}

data class SetupUiState(
    val useHttps: Boolean = true,
    val domain: String = "",
    val apiKey: String = "",
    val step: SetupStep = SetupStep.DOMAIN,
    val authMode: AuthMode = AuthMode.MANUAL_KEY,
    val email: String = "",
    val password: String = "",
    val connectionState: ConnectionState = ConnectionState.IDLE,
    val errorMessage: String? = null,
    val connectedEmail: String? = null,
    val serverInfo: ServerInfo? = null,
    val serverVersionDisplay: String? = null,
    val showOAuthButton: Boolean = false,
    /** Transient: PKCE state retained for the OAuth callback. */
    val pendingOAuth: OAuthStartResult? = null,
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

    fun resetOnboarding() {
        viewModelScope.launch { settingsRepo.resetOnboarding() }
    }

    init {
        viewModelScope.launch {
            val url = settingsRepo.serverUrl.first()
            val key = settingsRepo.apiKey.first()
            if (url.isNotBlank() || key.isNotBlank()) {
                val (https, domain) = parseUrl(url)
                _uiState.value = _uiState.value.copy(
                    useHttps = https,
                    domain = domain,
                    apiKey = key,
                    authMode = AuthMode.MANUAL_KEY,
                )
            }
        }
    }

    fun updateProtocol(https: Boolean) {
        _uiState.value = _uiState.value.copy(useHttps = https, connectionState = ConnectionState.IDLE)
    }

    fun updateDomain(input: String) {
        val (https, domain) = parseUrl(input)
        _uiState.value =
            _uiState.value.copy(useHttps = https, domain = domain, connectionState = ConnectionState.IDLE)
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

    fun updateEmail(email: String) {
        _uiState.value = _uiState.value.copy(email = email, connectionState = ConnectionState.IDLE)
    }

    fun updatePassword(password: String) {
        _uiState.value = _uiState.value.copy(password = password, connectionState = ConnectionState.IDLE)
    }

    fun setAuthMode(mode: AuthMode) {
        _uiState.value = _uiState.value.copy(authMode = mode, connectionState = ConnectionState.IDLE)
    }

    fun backToDomainStep() {
        _uiState.value = _uiState.value.copy(step = SetupStep.DOMAIN, connectionState = ConnectionState.IDLE)
    }

    /**
     * Step 1: validate the server — ping it, fetch version + features.
     * On success, advance to the AUTH step and show server info.
     */
    fun validateServer() {
        val state = _uiState.value
        if (state.domain.isBlank()) {
            _uiState.value =
                state.copy(connectionState = ConnectionState.ERROR, errorMessage = "Server URL is required")
            return
        }

        viewModelScope.launch {
            _uiState.value =
                _uiState.value.copy(connectionState = ConnectionState.CONNECTING, errorMessage = null)

            val infoResult = immichRepo.getServerInfo(state.serverUrl)
            if (infoResult.isFailure) {
                _uiState.value =
                    _uiState.value.copy(
                        connectionState = ConnectionState.ERROR,
                        errorMessage = infoResult.exceptionOrNull()?.message ?: "Server unreachable",
                    )
                return@launch
            }

            val info = infoResult.getOrThrow()
            _uiState.value =
                _uiState.value.copy(
                    step = SetupStep.AUTH,
                    connectionState = ConnectionState.IDLE,
                    serverInfo = info,
                    serverVersionDisplay = info.version.formatted(),
                    showOAuthButton = info.oauthEnabled,
                )
        }
    }

    /**
     * Step 2a: login with email/password and generate a scoped API key.
     * The password is discarded after this call — never persisted.
     */
    fun generateKey() {
        val state = _uiState.value
        if (state.email.isBlank() || state.password.isBlank()) {
            _uiState.value =
                state.copy(
                    connectionState = ConnectionState.ERROR,
                    errorMessage = "Email and password are required",
                )
            return
        }

        viewModelScope.launch {
            _uiState.value =
                _uiState.value.copy(connectionState = ConnectionState.CONNECTING, errorMessage = null)

            val keyResult = immichRepo.generateApiKey(state.serverUrl, state.email, state.password)
            if (keyResult.isFailure) {
                _uiState.value =
                    _uiState.value.copy(
                        connectionState = ConnectionState.ERROR,
                        errorMessage = keyResult.exceptionOrNull()?.message ?: "Login or key creation failed",
                    )
                return@launch
            }

            val key = keyResult.getOrThrow()
            val info = state.serverInfo
            completeConnection(key, info?.supportsScopedKeys == true, info?.version?.formatted() ?: "")
        }
    }

    /**
     * Step 2b: start OAuth flow. Opens the IdP login page in a browser.
     * The callback is handled by [handleOAuthCallback].
     */
    fun startOAuth() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.value =
                _uiState.value.copy(connectionState = ConnectionState.CONNECTING, errorMessage = null)

            val result = immichRepo.startOAuth(state.serverUrl)
            if (result.isFailure) {
                _uiState.value =
                    _uiState.value.copy(
                        connectionState = ConnectionState.ERROR,
                        errorMessage = result.exceptionOrNull()?.message ?: "OAuth setup failed",
                    )
                return@launch
            }

            val oauth = result.getOrThrow()
            _uiState.value = _uiState.value.copy(pendingOAuth = oauth)
        }
    }

    /**
     * Handle the OAuth callback deep-link. Exchanges the authorization code
     * for a session token and creates the API key.
     */
    fun handleOAuthCallback(callbackUrl: String) {
        val state = _uiState.value
        val oauth = state.pendingOAuth ?: return

        viewModelScope.launch {
            _uiState.value =
                _uiState.value.copy(connectionState = ConnectionState.CONNECTING, errorMessage = null)

            val result =
                immichRepo.finishOAuth(
                    state.serverUrl,
                    callbackUrl,
                    oauth.codeVerifier,
                    oauth.state,
                )
            if (result.isFailure) {
                _uiState.value =
                    _uiState.value.copy(
                        connectionState = ConnectionState.ERROR,
                        errorMessage = result.exceptionOrNull()?.message ?: "OAuth callback failed",
                        pendingOAuth = null,
                    )
                return@launch
            }

            val key = result.getOrThrow()
            val info = state.serverInfo
            completeConnection(key, info?.supportsScopedKeys == true, info?.version?.formatted() ?: "")
        }
    }

    /**
     * Step 2c: test connection with a manually-entered API key.
     */
    fun testConnection() {
        val state = _uiState.value
        if (state.apiKey.isBlank()) {
            _uiState.value =
                state.copy(
                    connectionState = ConnectionState.ERROR,
                    errorMessage = "API key is required",
                )
            return
        }

        viewModelScope.launch {
            _uiState.value =
                _uiState.value.copy(connectionState = ConnectionState.CONNECTING, errorMessage = null)

            settingsRepo.setServerUrl(state.serverUrl)
            settingsRepo.setApiKey(state.apiKey.trim())
            immichRepo.invalidateCache()

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

    /**
     * Common path for key generation (password + OAuth): store the key, persist
     * version/scope metadata, then validate and surface success.
     */
    private suspend fun completeConnection(key: String, scoped: Boolean, version: String) {
        settingsRepo.setServerUrl(_uiState.value.serverUrl)
        settingsRepo.setApiKey(key)
        settingsRepo.setApiKeyScoped(scoped)
        if (version.isNotBlank()) settingsRepo.setServerVersion(version)
        immichRepo.invalidateCache()

        val userResult = immichRepo.validateApiKey()
        if (userResult.isFailure) {
            _uiState.value =
                _uiState.value.copy(
                    connectionState = ConnectionState.ERROR,
                    errorMessage = "Key created but validation failed: ${userResult.exceptionOrNull()?.message}",
                )
            return
        }

        _uiState.value =
            _uiState.value.copy(
                connectionState = ConnectionState.SUCCESS,
                connectedEmail = userResult.getOrThrow(),
                pendingOAuth = null,
            )
    }
}
