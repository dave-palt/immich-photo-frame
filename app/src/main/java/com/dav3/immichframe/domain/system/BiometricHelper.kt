package com.dav3.immichframe.domain.system

import android.content.Context
import android.content.Intent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import android.provider.Settings as AndroidSettings

/**
 * Wrapper around [BiometricPrompt] that allows fingerprint, face, or device
 * credential (PIN / pattern / password) authentication.
 *
 * The prompt is fully cancellable — the user can back out at any time with no
 * penalty or lockout. A cancel (user dismissed the prompt, pressed back, or
 * cancelled the credential flow) is reported via [AuthResult.Cancelled], not
 * treated as an error.
 */
object BiometricHelper {

    /**
     * Checks whether the device can authenticate the user with any acceptable
     * authenticator (biometric or device credential).
     *
     * Returns:
     *  - [AuthCapability.Available] — the device has at least one suitable
     *    authenticator enrolled and ready.
     *  - [AuthCapability.NoHardware] — the device has no biometric hardware.
     *  - [AuthCapability.NotEnrolled] — hardware exists but no biometric /
     *    device credential is enrolled. The user should set up a screen lock.
     *  - [AuthCapability.Unavailable] — biometric is temporarily unavailable
     *    (e.g. locked out, hardware unavailable).
     */
    fun checkCapability(context: Context): AuthCapability {
        val bm = BiometricManager.from(context)
        return when (
            bm.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )
        ) {
            BiometricManager.BIOMETRIC_SUCCESS -> AuthCapability.Available
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
            -> AuthCapability.NoHardware
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> AuthCapability.NotEnrolled
            else -> AuthCapability.Unavailable
        }
    }

    /**
     * Shows the system biometric / credential prompt.
     *
     * [activity] **must** be a [FragmentActivity] — [BiometricPrompt] requires
     * a `FragmentActivity` context to host its internal dialog fragment. All
     * activities in this app extend `FragmentActivity` (via
     * `ComponentActivity` → `AppCompatActivity`).
     *
     * Cancellation (user dismissed, pressed back, or cancelled the credential
     * flow) calls [onResult] with [AuthResult.Cancelled] — it is **not** an
     * error. Only hard failures (lockout, hardware error) call [onResult] with
     * [AuthResult.Error].
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String? = null,
        onResult: (AuthResult) -> Unit,
    ) {
        val executor = androidx.core.content.ContextCompat.getMainExecutor(activity)
        val callback =
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onResult(AuthResult.Success)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // User-initiated cancels and the back button are NOT errors.
                    when (errorCode) {
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                        BiometricPrompt.ERROR_CANCELED,
                        -> onResult(AuthResult.Cancelled)
                        else -> onResult(AuthResult.Error(errString.toString()))
                    }
                }

                // A failed attempt (wrong finger, etc.) does not dismiss the
                // prompt — the system lets the user retry. We don't report it.
                override fun onAuthenticationFailed() {
                    // no-op: system handles retry
                }
            }

        val prompt =
            BiometricPrompt(activity, executor, callback)
        val info =
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .apply { subtitle?.let { setSubtitle(it) } }
                // Allow biometric OR device credential (PIN/pattern/password).
                // This lets phones without biometrics still gate on their
                // screen lock, and provides a PIN fallback everywhere.
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                )
                // setNegativeButtonText is incompatible with DEVICE_CREDENTIAL
                // — the system supplies its own "Cancel" / "Use PIN" button.
                .setConfirmationRequired(false)
                .build()
        prompt.authenticate(info)
    }

    /**
     * Opens the system security settings (where the user can enroll a
     * fingerprint / face / screen lock). Used when [checkCapability] returns
     * [AuthCapability.NotEnrolled] or [AuthCapability.NoHardware].
     */
    fun openSecuritySettings(context: Context) {
        val intent =
            Intent(AndroidSettings.ACTION_SECURITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        runCatching { context.startActivity(intent) }
    }
}

/** Result of a biometric / credential authentication attempt. */
sealed interface AuthResult {
    /** Authentication succeeded — proceed with the gated action. */
    data object Success : AuthResult

    /**
     * The user cancelled (dismissed the prompt, pressed back, or cancelled the
     * credential flow). The gated action should simply not run — no penalty.
     */
    data object Cancelled : AuthResult

    /** A hard error occurred (lockout, hardware failure, etc.). */
    data class Error(val message: String) : AuthResult
}

/** Whether the device can present a biometric / credential prompt. */
sealed interface AuthCapability {
    /** A suitable authenticator is available and enrolled. */
    data object Available : AuthCapability

    /** No biometric hardware present. Device credential may still be usable. */
    data object NoHardware : AuthCapability

    /**
     * Hardware exists but no biometric / device credential is enrolled. The
     * user should set up a screen lock.
     */
    data object NotEnrolled : AuthCapability

    /** Biometric is temporarily unavailable (lockout, etc.). */
    data object Unavailable : AuthCapability
}
