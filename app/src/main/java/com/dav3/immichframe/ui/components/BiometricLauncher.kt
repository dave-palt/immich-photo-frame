package com.dav3.immichframe.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import com.dav3.immichframe.domain.system.AuthCapability
import com.dav3.immichframe.domain.system.AuthResult
import com.dav3.immichframe.domain.system.BiometricHelper

/**
 * A composable-scoped handle that checks the device's biometric / credential
 * capability and triggers the system prompt.
 *
 * Usage:
 * ```
 * val biometric = rememberBiometricLauncher()
 *
 * Button(onClick = {
 *     biometric.launch(
 *         title = "Authenticate",
 *         onNotSetup = { /* show dialog → openSecuritySettings */ },
 *         onSuccess = { /* proceed with the gated action */ },
 *     )
 * }) { ... }
 * ```
 *
 * If the device has no biometric / credential enrolled, [launch] calls
 * [onNotSetup] instead of showing a prompt — the caller decides how to guide
 * the user (typically a dialog with a link to security settings).
 *
 * Cancellation is silent: the prompt dismissed, back pressed, or credential
 * flow cancelled → nothing happens (no callback). Only success calls
 * [onSuccess]; only hard errors call [onError].
 */
class BiometricLauncher internal constructor(
    private val activity: FragmentActivity,
) {
    val capability: AuthCapability
        get() = BiometricHelper.checkCapability(activity)

    fun launch(
        title: String,
        subtitle: String? = null,
        onNotSetup: () -> Unit = {},
        onSuccess: () -> Unit,
        onError: (String) -> Unit = {},
    ) {
        val cap = BiometricHelper.checkCapability(activity)
        if (cap !is AuthCapability.Available) {
            onNotSetup()
            return
        }
        BiometricHelper.authenticate(
            activity = activity,
            title = title,
            subtitle = subtitle,
        ) { result ->
            when (result) {
                AuthResult.Success -> onSuccess()
                AuthResult.Cancelled -> { /* no-op: user backed out */ }
                is AuthResult.Error -> onError(result.message)
            }
        }
    }
}

/**
 * Returns a [BiometricLauncher] bound to the current [FragmentActivity].
 *
 * The host activity **must** be a [FragmentActivity] — [BiometricPrompt]
 * requires one to host its internal dialog fragment. The app's
 * `MainActivity` extends `FragmentActivity`.
 */
@Composable
fun rememberBiometricLauncher(): BiometricLauncher {
    val context = LocalContext.current
    val activity = remember(context) {
        context as? FragmentActivity
            ?: error("BiometricLauncher requires a FragmentActivity context")
    }
    return remember(activity) { BiometricLauncher(activity) }
}
