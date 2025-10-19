package com.trustvault.android.security.biometric

import android.content.Context
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.trustvault.android.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.crypto.Cipher
import javax.inject.Inject
import javax.inject.Singleton

/**
 * EnhancedBiometricAuthManager - Advanced biometric authentication with CryptoObject support.
 *
 * **Enhancements over basic BiometricAuthManager:**
 * - CryptoObject support for cryptographic key operations
 * - Handles biometric key invalidation scenarios
 * - Provides detailed error callbacks
 * - Supports both simple auth and crypto-based auth
 *
 * **Use Cases:**
 * 1. **Simple Authentication:** Just verify user identity (existing BiometricAuthManager)
 * 2. **Crypto Authentication:** Unlock cryptographic keys gated by biometrics (this class)
 *
 * **Security Architecture:**
 * ```
 * User → Biometric Sensor → System Validates → Keystore Unlocks Key → App Uses Key
 *                                                        ↓
 *                              Key only accessible after successful biometric auth
 * ```
 *
 * @property context Application context
 */
@Singleton
class EnhancedBiometricAuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "EnhancedBiometricAuth"

        // Authenticator types for different security levels
        private const val AUTHENTICATORS_STRONG =
            BiometricManager.Authenticators.BIOMETRIC_STRONG

        private const val AUTHENTICATORS_WITH_CREDENTIAL =
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
    }

    // ========================================================================
    // BIOMETRIC AVAILABILITY
    // ========================================================================

    /**
     * Checks if strong biometric authentication is available on the device.
     *
     * **Strong Biometrics:** Class 3 (fingerprint, face, iris)
     * - Security: Cannot be spoofed easily
     * - Required for cryptographic operations
     *
     * @return BiometricAvailability status
     */
    fun checkBiometricAvailability(): BiometricAvailability {
        val biometricManager = BiometricManager.from(context)
        return when (biometricManager.canAuthenticate(AUTHENTICATORS_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS ->
                BiometricAvailability.Available

            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                BiometricAvailability.NoHardware

            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                BiometricAvailability.HardwareUnavailable

            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                BiometricAvailability.NoneEnrolled

            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
                BiometricAvailability.SecurityUpdateRequired

            else -> BiometricAvailability.Unknown
        }
    }

    /**
     * Checks if device credential (PIN/pattern/password) is available as fallback.
     */
    fun isDeviceCredentialAvailable(): Boolean {
        val biometricManager = BiometricManager.from(context)
        return biometricManager.canAuthenticate(AUTHENTICATORS_WITH_CREDENTIAL) ==
                BiometricManager.BIOMETRIC_SUCCESS
    }

    // ========================================================================
    // CRYPTO-BASED AUTHENTICATION
    // ========================================================================

    /**
     * Authenticates user with biometrics to unlock a Cipher.
     *
     * **Cryptographic Authentication Flow:**
     * 1. Caller creates Cipher with biometric-protected key
     * 2. BiometricPrompt shows fingerprint/face prompt
     * 3. User authenticates with biometrics
     * 4. System unlocks Cipher for use
     * 5. Caller uses authenticated Cipher to encrypt/decrypt
     *
     * **Security:** Cipher can only be used after successful biometric authentication.
     * If authentication fails, Cipher remains locked.
     *
     * @param activity FragmentActivity for showing biometric prompt
     * @param cipher Cipher initialized with biometric-protected key
     * @param title Prompt title (optional, defaults to app string)
     * @param subtitle Prompt subtitle (optional)
     * @param description Prompt description (optional)
     * @param allowDeviceCredential Allow PIN/password fallback (default: false for crypto)
     * @param onSuccess Callback with authenticated cipher
     * @param onError Callback with error details
     * @param onUserCancelled Callback when user cancels
     */
    fun authenticateWithCrypto(
        activity: FragmentActivity,
        cipher: Cipher,
        title: String = context.getString(R.string.biometric_prompt_title),
        subtitle: String? = context.getString(R.string.biometric_prompt_subtitle),
        description: String? = context.getString(R.string.biometric_prompt_description),
        allowDeviceCredential: Boolean = false,
        onSuccess: (Cipher) -> Unit,
        onError: (BiometricError) -> Unit,
        onUserCancelled: () -> Unit = {}
    ) {
        val executor = ContextCompat.getMainExecutor(activity)

        // Create BiometricPrompt with callbacks
        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)

                    // Extract authenticated cipher from CryptoObject
                    val authenticatedCipher = result.cryptoObject?.cipher
                    if (authenticatedCipher == null) {
                        Log.e(TAG, "CryptoObject cipher is null after authentication")
                        onError(BiometricError.CryptoError("Cipher not available after authentication"))
                        return
                    }

                    Log.d(TAG, "Biometric authentication succeeded with crypto")
                    onSuccess(authenticatedCipher)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)

                    // Map error codes to detailed errors
                    val error = when (errorCode) {
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON -> {
                            Log.d(TAG, "User cancelled biometric authentication")
                            onUserCancelled()
                            return
                        }

                        BiometricPrompt.ERROR_LOCKOUT,
                        BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> {
                            Log.w(TAG, "Biometric authentication locked out")
                            BiometricError.Lockout(errString.toString(), isPermanent = errorCode == BiometricPrompt.ERROR_LOCKOUT_PERMANENT)
                        }

                        BiometricPrompt.ERROR_NO_BIOMETRICS -> {
                            Log.w(TAG, "No biometrics enrolled")
                            BiometricError.NoneEnrolled(errString.toString())
                        }

                        BiometricPrompt.ERROR_HW_NOT_PRESENT,
                        BiometricPrompt.ERROR_HW_UNAVAILABLE -> {
                            Log.w(TAG, "Biometric hardware unavailable")
                            BiometricError.HardwareError(errString.toString())
                        }

                        BiometricPrompt.ERROR_TIMEOUT -> {
                            Log.w(TAG, "Biometric authentication timeout")
                            BiometricError.Timeout(errString.toString())
                        }

                        else -> {
                            Log.e(TAG, "Biometric authentication error: $errorCode - $errString")
                            BiometricError.Unknown(errorCode, errString.toString())
                        }
                    }

                    onError(error)
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    // Individual attempt failed, but prompt remains active
                    Log.d(TAG, "Biometric authentication attempt failed (user can retry)")
                    // Don't call onError - let user retry
                }
            }
        )

        // Build prompt info
        val promptInfoBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)

        if (subtitle != null) {
            promptInfoBuilder.setSubtitle(subtitle)
        }

        if (description != null) {
            promptInfoBuilder.setDescription(description)
        }

        // Configure authenticators
        if (allowDeviceCredential) {
            // Allow biometric OR device credential (no negative button)
            promptInfoBuilder.setAllowedAuthenticators(AUTHENTICATORS_WITH_CREDENTIAL)
        } else {
            // Biometric only (show cancel button)
            promptInfoBuilder
                .setAllowedAuthenticators(AUTHENTICATORS_STRONG)
                .setNegativeButtonText(context.getString(android.R.string.cancel))
        }

        val promptInfo = promptInfoBuilder.build()

        // Authenticate with CryptoObject
        val cryptoObject = BiometricPrompt.CryptoObject(cipher)
        biometricPrompt.authenticate(promptInfo, cryptoObject)
    }

    // ========================================================================
    // SIMPLE AUTHENTICATION (Non-Crypto)
    // ========================================================================

    /**
     * Authenticates user with biometrics (simple mode, no crypto).
     *
     * **Use Case:** Verify user identity without cryptographic operations.
     * For vault unlocking with biometric-protected keys, use authenticateWithCrypto() instead.
     *
     * @param activity FragmentActivity for showing biometric prompt
     * @param title Prompt title
     * @param subtitle Prompt subtitle
     * @param allowDeviceCredential Allow PIN/password fallback
     * @param onSuccess Callback on successful authentication
     * @param onError Callback with error details
     * @param onUserCancelled Callback when user cancels
     */
    fun authenticateSimple(
        activity: FragmentActivity,
        title: String = context.getString(R.string.biometric_prompt_title),
        subtitle: String? = context.getString(R.string.biometric_prompt_subtitle),
        allowDeviceCredential: Boolean = true,
        onSuccess: () -> Unit,
        onError: (BiometricError) -> Unit,
        onUserCancelled: () -> Unit = {}
    ) {
        val executor = ContextCompat.getMainExecutor(activity)

        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    Log.d(TAG, "Simple biometric authentication succeeded")
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)

                    when (errorCode) {
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON -> {
                            onUserCancelled()
                            return
                        }
                    }

                    val error = mapErrorCodeToError(errorCode, errString.toString())
                    onError(error)
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    // Let user retry
                }
            }
        )

        val promptInfoBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)

        if (subtitle != null) {
            promptInfoBuilder.setSubtitle(subtitle)
        }

        if (allowDeviceCredential) {
            promptInfoBuilder.setAllowedAuthenticators(AUTHENTICATORS_WITH_CREDENTIAL)
        } else {
            promptInfoBuilder
                .setAllowedAuthenticators(AUTHENTICATORS_STRONG)
                .setNegativeButtonText(context.getString(android.R.string.cancel))
        }

        biometricPrompt.authenticate(promptInfoBuilder.build())
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private fun mapErrorCodeToError(errorCode: Int, errString: String): BiometricError {
        return when (errorCode) {
            BiometricPrompt.ERROR_LOCKOUT,
            BiometricPrompt.ERROR_LOCKOUT_PERMANENT ->
                BiometricError.Lockout(errString, isPermanent = errorCode == BiometricPrompt.ERROR_LOCKOUT_PERMANENT)

            BiometricPrompt.ERROR_NO_BIOMETRICS ->
                BiometricError.NoneEnrolled(errString)

            BiometricPrompt.ERROR_HW_NOT_PRESENT,
            BiometricPrompt.ERROR_HW_UNAVAILABLE ->
                BiometricError.HardwareError(errString)

            BiometricPrompt.ERROR_TIMEOUT ->
                BiometricError.Timeout(errString)

            else ->
                BiometricError.Unknown(errorCode, errString)
        }
    }
}

// ========================================================================
// ENUMS & DATA CLASSES
// ========================================================================

/**
 * Biometric availability status.
 */
sealed class BiometricAvailability {
    /** Biometric authentication is available */
    object Available : BiometricAvailability()

    /** No biometric hardware on device */
    object NoHardware : BiometricAvailability()

    /** Hardware temporarily unavailable */
    object HardwareUnavailable : BiometricAvailability()

    /** No biometrics enrolled */
    object NoneEnrolled : BiometricAvailability()

    /** Security update required */
    object SecurityUpdateRequired : BiometricAvailability()

    /** Unknown status */
    object Unknown : BiometricAvailability()

    fun isAvailable(): Boolean = this is Available
}

/**
 * Detailed biometric error types.
 */
sealed class BiometricError(open val message: String) {
    /** User locked out (too many failed attempts) */
    data class Lockout(override val message: String, val isPermanent: Boolean) : BiometricError(message)

    /** No biometrics enrolled */
    data class NoneEnrolled(override val message: String) : BiometricError(message)

    /** Hardware error or unavailable */
    data class HardwareError(override val message: String) : BiometricError(message)

    /** Authentication timeout */
    data class Timeout(override val message: String) : BiometricError(message)

    /** Cryptographic operation error */
    data class CryptoError(override val message: String) : BiometricError(message)

    /** Unknown error */
    data class Unknown(val errorCode: Int, override val message: String) : BiometricError(message)
}
