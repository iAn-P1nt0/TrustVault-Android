package com.trustvault.android.security.biometric

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.fragment.app.FragmentActivity
import com.trustvault.android.security.zeroknowledge.MasterKeyHierarchy
import com.trustvault.android.util.PreferencesManager
import com.trustvault.android.util.secureWipe
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.crypto.Cipher
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BiometricVaultUnlocker - Manages biometric-based vault unlocking with zero-knowledge security.
 *
 * **Zero-Knowledge Architecture:**
 * ```
 * Setup Flow (First Time):
 * 1. User enters master password
 * 2. PBKDF2 derives Master Encryption Key (MEK)
 * 3. Biometric key generated in Keystore
 * 4. MEK encrypted with biometric key → Wrapped MEK
 * 5. Wrapped MEK stored in preferences
 * 6. Original MEK wiped from memory
 *
 * Unlock Flow (Biometric):
 * 1. User triggers biometric unlock
 * 2. BiometricPrompt shows fingerprint/face prompt
 * 3. User authenticates successfully
 * 4. Cipher unlocked by biometric authentication
 * 5. Wrapped MEK decrypted with cipher → MEK
 * 6. MEK used to unlock vault
 * 7. MEK wiped from memory
 * ```
 *
 * **Security Properties:**
 * - Master password never stored (only Argon2id hash)
 * - MEK only exists in memory during active session
 * - Biometric key cannot be exported from Keystore
 * - MEK encrypted at rest (wrapped with biometric key)
 * - Automatic key invalidation when biometrics change
 * - Compatible with existing password-based unlock
 *
 * **OWASP Compliance:**
 * - OWASP MASTG: Secure biometric authentication
 * - NIST SP 800-63B: Multi-factor authentication
 * - Zero-knowledge proof principles
 *
 * @property context Application context
 * @property biometricKeyManager Manages biometric keys in Keystore
 * @property biometricAuthManager Handles biometric prompts
 * @property masterKeyHierarchy Derives keys from master password
 * @property preferencesManager Stores wrapped MEK
 */
@Singleton
class BiometricVaultUnlocker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val biometricKeyManager: BiometricKeyManager,
    private val biometricAuthManager: EnhancedBiometricAuthManager,
    private val masterKeyHierarchy: MasterKeyHierarchy,
    private val preferencesManager: PreferencesManager
) {

    companion object {
        private const val TAG = "BiometricVaultUnlocker"

        // SharedPreferences keys for wrapped MEK
        private const val PREF_WRAPPED_MEK = "biometric_wrapped_mek"
        private const val PREF_WRAPPED_MEK_IV = "biometric_wrapped_mek_iv"
    }

    // ========================================================================
    // BIOMETRIC SETUP
    // ========================================================================

    /**
     * Sets up biometric unlock for the vault.
     *
     * **Setup Flow:**
     * 1. Generate biometric key in Keystore
     * 2. Derive MEK from master password
     * 3. Show biometric prompt to wrap MEK
     * 4. Store wrapped MEK in preferences
     * 5. Enable biometric unlock in preferences
     *
     * **Usage:** Call this after user successfully creates/enters master password
     * and chooses to enable biometric unlock.
     *
     * @param activity FragmentActivity for biometric prompt
     * @param masterPassword User's master password (will NOT be wiped)
     * @param onSuccess Callback on successful setup
     * @param onError Callback with error message
     * @param onUserCancelled Callback when user cancels
     */
    suspend fun setupBiometricUnlock(
        activity: FragmentActivity,
        masterPassword: CharArray,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onUserCancelled: () -> Unit = {}
    ) {
        try {
            Log.d(TAG, "Starting biometric unlock setup")

            // Step 1: Check biometric availability
            val availability = biometricAuthManager.checkBiometricAvailability()
            if (!availability.isAvailable()) {
                val errorMsg = when (availability) {
                    is BiometricAvailability.NoHardware -> "No biometric hardware available"
                    is BiometricAvailability.NoneEnrolled -> "No biometrics enrolled. Please enroll fingerprint or face in Settings."
                    is BiometricAvailability.HardwareUnavailable -> "Biometric hardware temporarily unavailable"
                    is BiometricAvailability.SecurityUpdateRequired -> "Security update required for biometric authentication"
                    else -> "Biometric authentication not available"
                }
                onError(errorMsg)
                return
            }

            // Step 2: Generate biometric key
            biometricKeyManager.generateBiometricKey().onFailure { exception ->
                Log.e(TAG, "Failed to generate biometric key", exception)
                onError("Failed to generate biometric key: ${exception.message}")
                return
            }

            // Step 3: Derive Master Encryption Key from password
            val mek = try {
                masterKeyHierarchy.deriveMasterEncryptionKey(masterPassword)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to derive MEK", e)
                onError("Failed to derive encryption key: ${e.message}")
                return
            }

            // Step 4: Get cipher for encryption
            val cipher = biometricKeyManager.getCipherForEncryption().getOrElse { exception ->
                Log.e(TAG, "Failed to get encryption cipher", exception)
                mek.secureWipe()
                onError("Failed to create encryption cipher: ${exception.message}")
                return
            }

            // Step 5: Show biometric prompt to encrypt MEK
            biometricAuthManager.authenticateWithCrypto(
                activity = activity,
                cipher = cipher,
                title = "Enable Biometric Unlock",
                subtitle = "Authenticate to enable fingerprint/face unlock",
                description = "Your master password will be securely encrypted with your biometrics",
                allowDeviceCredential = false,
                onSuccess = { authenticatedCipher ->
                    try {
                        // Step 6: Encrypt MEK with biometric key
                        val wrappedKey = biometricKeyManager.wrapMasterKey(authenticatedCipher, mek)
                            .getOrElse { exception ->
                                Log.e(TAG, "Failed to wrap MEK", exception)
                                mek.secureWipe()
                                onError("Failed to encrypt master key: ${exception.message}")
                                return@authenticateWithCrypto
                            }

                        // Step 7: Store wrapped MEK in preferences
                        saveWrappedMEK(wrappedKey)

                        // Step 8: Enable biometric unlock preference
                        kotlinx.coroutines.runBlocking {
                            preferencesManager.setBiometricEnabled(true)
                        }

                        Log.d(TAG, "Biometric unlock setup successful")
                        onSuccess()

                    } catch (e: Exception) {
                        Log.e(TAG, "Error during biometric setup", e)
                        onError("Setup failed: ${e.message}")
                    } finally {
                        // SECURITY: Always wipe MEK from memory
                        mek.secureWipe()
                    }
                },
                onError = { error ->
                    Log.w(TAG, "Biometric authentication failed: ${error.message}")
                    mek.secureWipe()
                    onError("Authentication failed: ${error.message}")
                },
                onUserCancelled = {
                    Log.d(TAG, "User cancelled biometric setup")
                    mek.secureWipe()
                    onUserCancelled()
                }
            )

        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during biometric setup", e)
            onError("Unexpected error: ${e.message}")
        }
    }

    // ========================================================================
    // BIOMETRIC UNLOCK
    // ========================================================================

    /**
     * Unlocks the vault using biometric authentication.
     *
     * **Unlock Flow:**
     * 1. Retrieve wrapped MEK from preferences
     * 2. Get decryption cipher from biometric key
     * 3. Show biometric prompt
     * 4. Decrypt MEK with authenticated cipher
     * 5. Return MEK for vault unlocking
     *
     * **SECURITY CRITICAL:** Caller MUST wipe returned MEK after use!
     *
     * @param activity FragmentActivity for biometric prompt
     * @param onSuccess Callback with unwrapped MEK (MUST be wiped after use)
     * @param onError Callback with error message
     * @param onUserCancelled Callback when user cancels
     * @param onBiometricInvalidated Callback when biometric key is invalidated
     */
    suspend fun unlockWithBiometric(
        activity: FragmentActivity,
        onSuccess: (ByteArray) -> Unit,
        onError: (String) -> Unit,
        onUserCancelled: () -> Unit = {},
        onBiometricInvalidated: () -> Unit = {}
    ) {
        try {
            Log.d(TAG, "Starting biometric unlock")

            // Step 1: Check if biometric unlock is enabled
            val isBiometricEnabled = preferencesManager.isBiometricEnabled.first()
            if (!isBiometricEnabled) {
                onError("Biometric unlock is not enabled")
                return
            }

            // Step 2: Check biometric availability
            val availability = biometricAuthManager.checkBiometricAvailability()
            if (!availability.isAvailable()) {
                val errorMsg = when (availability) {
                    is BiometricAvailability.NoHardware -> "No biometric hardware available"
                    is BiometricAvailability.NoneEnrolled -> "No biometrics enrolled"
                    is BiometricAvailability.HardwareUnavailable -> "Biometric hardware unavailable"
                    else -> "Biometric authentication not available"
                }
                onError(errorMsg)
                return
            }

            // Step 3: Retrieve wrapped MEK from preferences
            val wrappedKey = retrieveWrappedMEK()
            if (wrappedKey == null) {
                Log.e(TAG, "Wrapped MEK not found in preferences")
                onError("Biometric unlock data not found. Please disable and re-enable biometric unlock.")
                return
            }

            // Step 4: Get decryption cipher
            val cipher = biometricKeyManager.getCipherForDecryption(wrappedKey.iv).getOrElse { exception ->
                Log.e(TAG, "Failed to get decryption cipher", exception)

                // Check if key was invalidated
                if (exception is BiometricKeyInvalidatedException) {
                    Log.w(TAG, "Biometric key invalidated - biometrics have changed")
                    disableBiometricUnlock()
                    onBiometricInvalidated()
                    return
                }

                onError("Failed to create decryption cipher: ${exception.message}")
                return
            }

            // Step 5: Show biometric prompt to decrypt MEK
            biometricAuthManager.authenticateWithCrypto(
                activity = activity,
                cipher = cipher,
                title = "Unlock TrustVault",
                subtitle = "Authenticate to unlock your vault",
                description = "Use your fingerprint or face to unlock",
                allowDeviceCredential = true, // Allow PIN/password as fallback
                onSuccess = { authenticatedCipher ->
                    try {
                        // Step 6: Decrypt MEK
                        val mek = biometricKeyManager.unwrapMasterKey(authenticatedCipher, wrappedKey)
                            .getOrElse { exception ->
                                Log.e(TAG, "Failed to unwrap MEK", exception)
                                onError("Failed to decrypt master key: ${exception.message}")
                                return@authenticateWithCrypto
                            }

                        Log.d(TAG, "Biometric unlock successful")

                        // Step 7: Return MEK to caller
                        // SECURITY: Caller MUST wipe MEK after use
                        onSuccess(mek)

                    } catch (e: Exception) {
                        Log.e(TAG, "Error during biometric unlock", e)
                        onError("Unlock failed: ${e.message}")
                    }
                },
                onError = { error ->
                    Log.w(TAG, "Biometric authentication failed: ${error.message}")
                    onError("Authentication failed: ${error.message}")
                },
                onUserCancelled = {
                    Log.d(TAG, "User cancelled biometric unlock")
                    onUserCancelled()
                }
            )

        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during biometric unlock", e)
            onError("Unexpected error: ${e.message}")
        }
    }

    // ========================================================================
    // BIOMETRIC MANAGEMENT
    // ========================================================================

    /**
     * Disables biometric unlock.
     * Deletes biometric key and wrapped MEK from storage.
     *
     * **Usage:** Call when user disables biometric unlock in settings,
     * or when biometric key is invalidated.
     */
    suspend fun disableBiometricUnlock() {
        try {
            Log.d(TAG, "Disabling biometric unlock")

            // Delete biometric key from Keystore
            biometricKeyManager.deleteBiometricKey()

            // Clear wrapped MEK from preferences
            clearWrappedMEK()

            // Disable biometric unlock preference
            preferencesManager.setBiometricEnabled(false)

            Log.d(TAG, "Biometric unlock disabled successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Error disabling biometric unlock", e)
            throw e
        }
    }

    /**
     * Checks if biometric unlock is available and configured.
     *
     * @return true if biometric unlock can be used
     */
    suspend fun isBiometricUnlockAvailable(): Boolean {
        return try {
            // Check if enabled in preferences
            val isEnabled = preferencesManager.isBiometricEnabled.first()
            if (!isEnabled) {
                return false
            }

            // Check if biometric hardware is available
            val availability = biometricAuthManager.checkBiometricAvailability()
            if (!availability.isAvailable()) {
                return false
            }

            // Check if biometric key exists and is valid
            if (!biometricKeyManager.hasBiometricKey() || !biometricKeyManager.isBiometricKeyValid()) {
                return false
            }

            // Check if wrapped MEK exists
            retrieveWrappedMEK() != null

        } catch (e: Exception) {
            Log.e(TAG, "Error checking biometric availability", e)
            false
        }
    }

    // ========================================================================
    // STORAGE HELPERS
    // ========================================================================

    private fun saveWrappedMEK(wrappedKey: WrappedKey) {
        val prefs = context.getSharedPreferences("trustvault_biometric", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(PREF_WRAPPED_MEK, Base64.encodeToString(wrappedKey.encryptedKey, Base64.NO_WRAP))
            putString(PREF_WRAPPED_MEK_IV, Base64.encodeToString(wrappedKey.iv, Base64.NO_WRAP))
            apply()
        }
    }

    private fun retrieveWrappedMEK(): WrappedKey? {
        return try {
            val prefs = context.getSharedPreferences("trustvault_biometric", Context.MODE_PRIVATE)
            val encryptedKeyB64 = prefs.getString(PREF_WRAPPED_MEK, null) ?: return null
            val ivB64 = prefs.getString(PREF_WRAPPED_MEK_IV, null) ?: return null

            val encryptedKey = Base64.decode(encryptedKeyB64, Base64.NO_WRAP)
            val iv = Base64.decode(ivB64, Base64.NO_WRAP)

            WrappedKey(encryptedKey, iv)
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving wrapped MEK", e)
            null
        }
    }

    private fun clearWrappedMEK() {
        val prefs = context.getSharedPreferences("trustvault_biometric", Context.MODE_PRIVATE)
        prefs.edit().apply {
            remove(PREF_WRAPPED_MEK)
            remove(PREF_WRAPPED_MEK_IV)
            apply()
        }
    }
}
