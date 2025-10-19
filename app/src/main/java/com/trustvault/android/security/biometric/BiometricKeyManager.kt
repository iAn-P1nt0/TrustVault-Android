package com.trustvault.android.security.biometric

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Log
import com.trustvault.android.util.secureWipe
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BiometricKeyManager - Manages biometric-protected cryptographic keys in Android Keystore.
 *
 * **Zero-Knowledge Security Architecture:**
 * - Biometric authentication gates access to encryption keys
 * - Master password is never stored (only hash for verification)
 * - Encryption keys are wrapped/unwrapped with biometric key
 * - Keys invalidated when biometrics change (automatic security)
 *
 * **Key Hierarchy:**
 * ```
 * Master Password (user input) → PBKDF2 → Master Encryption Key (MEK)
 *                                              ↓
 *                                   Encrypted with Biometric Key
 *                                              ↓
 *                                   Stored in EncryptedPreferences
 *
 * Biometric Auth → Keystore Key → Decrypt MEK → Unlock Vault
 * ```
 *
 * **Security Features:**
 * - AES-256-GCM authenticated encryption
 * - Biometric-gated key access (`setUserAuthenticationRequired(true)`)
 * - Automatic key invalidation on biometric re-enrollment
 * - StrongBox support for hardware security module
 * - User authentication timeout (30 seconds)
 *
 * **OWASP Compliance:**
 * - OWASP MASTG: Secure biometric authentication
 * - NIST SP 800-63B: Multi-factor authentication
 * - Android Security Best Practices
 *
 * @see <a href="https://developer.android.com/training/sign-in/biometric-auth">Android Biometric Authentication</a>
 */
@Singleton
class BiometricKeyManager @Inject constructor() {

    companion object {
        private const val TAG = "BiometricKeyManager"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val BIOMETRIC_KEY_ALIAS = "trustvault_biometric_master_key"

        // AES-256-GCM parameters
        private const val KEY_SIZE = 256
        private const val GCM_TAG_LENGTH = 128
        private const val IV_SIZE = 12

        // User authentication timeout (30 seconds)
        // User must authenticate with biometrics within this window
        private const val AUTH_VALIDITY_DURATION_SECONDS = 30
    }

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    // ========================================================================
    // BIOMETRIC KEY GENERATION
    // ========================================================================

    /**
     * Generates a new biometric-protected encryption key in Android Keystore.
     *
     * **Security Properties:**
     * - Key generated inside hardware security module (StrongBox if available)
     * - Biometric authentication required for key usage
     * - Key invalidated automatically when biometrics change
     * - Cannot be exported from Keystore
     *
     * **Key Parameters:**
     * - Algorithm: AES-256-GCM
     * - Purpose: ENCRYPT + DECRYPT
     * - User Authentication: Required (biometric)
     * - Invalidation: On biometric enrollment change
     *
     * @return Result indicating success or failure
     */
    fun generateBiometricKey(): Result<Unit> {
        return try {
            Log.d(TAG, "Generating biometric-protected key")

            // Delete existing key if present (fresh start)
            if (keyStore.containsAlias(BIOMETRIC_KEY_ALIAS)) {
                keyStore.deleteEntry(BIOMETRIC_KEY_ALIAS)
                Log.d(TAG, "Deleted existing biometric key")
            }

            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )

            val builder = KeyGenParameterSpec.Builder(
                BIOMETRIC_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE)
                // SECURITY CRITICAL: Require biometric authentication for key use
                .setUserAuthenticationRequired(true)
                // Invalidate key when new biometric is enrolled (security best practice)
                .setInvalidatedByBiometricEnrollment(true)

            // Set authentication validity duration (API 30+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder.setUserAuthenticationParameters(
                    AUTH_VALIDITY_DURATION_SECONDS,
                    KeyProperties.AUTH_BIOMETRIC_STRONG
                )
            } else {
                @Suppress("DEPRECATION")
                builder.setUserAuthenticationValidityDurationSeconds(AUTH_VALIDITY_DURATION_SECONDS)
            }

            // Use StrongBox for hardware security module if available (Pixel 3+, Samsung S9+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                builder.setIsStrongBoxBacked(true)
            }

            try {
                keyGenerator.init(builder.build())
                keyGenerator.generateKey()
                Log.d(TAG, "Biometric key generated successfully")
                Result.success(Unit)
            } catch (e: Exception) {
                // Fallback: Try without StrongBox if not supported
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    Log.w(TAG, "StrongBox not available, falling back to TEE")
                    builder.setIsStrongBoxBacked(false)
                    keyGenerator.init(builder.build())
                    keyGenerator.generateKey()
                    Log.d(TAG, "Biometric key generated (TEE fallback)")
                    Result.success(Unit)
                } else {
                    throw e
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate biometric key", e)
            Result.failure(BiometricKeyException("Key generation failed: ${e.message}", e))
        }
    }

    // ========================================================================
    // KEY RETRIEVAL & CIPHER CREATION
    // ========================================================================

    /**
     * Gets the biometric-protected secret key from Keystore.
     *
     * @return SecretKey or null if not found
     * @throws KeyPermanentlyInvalidatedException if biometrics changed
     */
    private fun getBiometricKey(): SecretKey? {
        return try {
            if (!keyStore.containsAlias(BIOMETRIC_KEY_ALIAS)) {
                Log.w(TAG, "Biometric key not found in Keystore")
                return null
            }

            val entry = keyStore.getEntry(BIOMETRIC_KEY_ALIAS, null)
            if (entry !is KeyStore.SecretKeyEntry) {
                Log.e(TAG, "Keystore entry is not a SecretKey")
                return null
            }

            entry.secretKey
        } catch (e: KeyPermanentlyInvalidatedException) {
            Log.w(TAG, "Biometric key invalidated (biometrics changed)")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving biometric key", e)
            null
        }
    }

    /**
     * Creates a Cipher for encryption with the biometric key.
     *
     * **Usage:** This cipher must be used with BiometricPrompt.CryptoObject
     * to authenticate the user before encryption.
     *
     * @return Cipher initialized for encryption
     * @throws KeyPermanentlyInvalidatedException if biometrics changed
     */
    fun getCipherForEncryption(): Result<Cipher> {
        return try {
            val key = getBiometricKey()
                ?: return Result.failure(BiometricKeyException("Biometric key not found"))

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)

            Result.success(cipher)
        } catch (e: KeyPermanentlyInvalidatedException) {
            Log.w(TAG, "Biometric key invalidated during cipher creation")
            Result.failure(BiometricKeyInvalidatedException("Biometric key invalidated", e))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create encryption cipher", e)
            Result.failure(BiometricKeyException("Cipher creation failed: ${e.message}", e))
        }
    }

    /**
     * Creates a Cipher for decryption with the biometric key.
     *
     * **Usage:** This cipher must be used with BiometricPrompt.CryptoObject
     * to authenticate the user before decryption.
     *
     * @param iv Initialization vector from encryption
     * @return Cipher initialized for decryption
     * @throws KeyPermanentlyInvalidatedException if biometrics changed
     */
    fun getCipherForDecryption(iv: ByteArray): Result<Cipher> {
        return try {
            require(iv.size == IV_SIZE) { "Invalid IV size: ${iv.size} (expected $IV_SIZE)" }

            val key = getBiometricKey()
                ?: return Result.failure(BiometricKeyException("Biometric key not found"))

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)

            Result.success(cipher)
        } catch (e: KeyPermanentlyInvalidatedException) {
            Log.w(TAG, "Biometric key invalidated during cipher creation")
            Result.failure(BiometricKeyInvalidatedException("Biometric key invalidated", e))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create decryption cipher", e)
            Result.failure(BiometricKeyException("Cipher creation failed: ${e.message}", e))
        }
    }

    // ========================================================================
    // MASTER KEY WRAPPING/UNWRAPPING
    // ========================================================================

    /**
     * Encrypts (wraps) the Master Encryption Key with biometric key.
     *
     * **Zero-Knowledge Flow:**
     * 1. User enters master password during setup
     * 2. PBKDF2 derives Master Encryption Key (MEK)
     * 3. Biometric key encrypts MEK
     * 4. Encrypted MEK stored in preferences (not sensitive)
     * 5. Original MEK wiped from memory
     *
     * **Usage:** Call this after successful biometric authentication
     *
     * @param cipher Authenticated cipher from BiometricPrompt
     * @param masterEncryptionKey The MEK to wrap (will NOT be wiped)
     * @return Encrypted MEK with IV
     */
    fun wrapMasterKey(cipher: Cipher, masterEncryptionKey: ByteArray): Result<WrappedKey> {
        return try {
            require(masterEncryptionKey.size == 32) { "Invalid MEK size: ${masterEncryptionKey.size}" }

            // Encrypt MEK with biometric-protected cipher
            val encryptedKey = cipher.doFinal(masterEncryptionKey)
            val iv = cipher.iv

            Log.d(TAG, "Master key wrapped successfully (${encryptedKey.size} bytes)")

            Result.success(
                WrappedKey(
                    encryptedKey = encryptedKey,
                    iv = iv
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to wrap master key", e)
            Result.failure(BiometricKeyException("Key wrapping failed: ${e.message}", e))
        }
    }

    /**
     * Decrypts (unwraps) the Master Encryption Key with biometric key.
     *
     * **Zero-Knowledge Flow:**
     * 1. User authenticates with biometrics
     * 2. Retrieve encrypted MEK from preferences
     * 3. Biometric key decrypts MEK
     * 4. MEK used to unlock vault
     * 5. MEK wiped from memory after use
     *
     * **Usage:** Call this after successful biometric authentication
     *
     * @param cipher Authenticated cipher from BiometricPrompt
     * @param wrappedKey Encrypted MEK with IV
     * @return Decrypted MEK (MUST be wiped after use)
     */
    fun unwrapMasterKey(cipher: Cipher, wrappedKey: WrappedKey): Result<ByteArray> {
        return try {
            // Decrypt MEK with biometric-protected cipher
            val masterEncryptionKey = cipher.doFinal(wrappedKey.encryptedKey)

            if (masterEncryptionKey.size != 32) {
                // Security: Wipe invalid key from memory
                masterEncryptionKey.secureWipe()
                return Result.failure(
                    BiometricKeyException("Invalid unwrapped key size: ${masterEncryptionKey.size}")
                )
            }

            Log.d(TAG, "Master key unwrapped successfully")

            Result.success(masterEncryptionKey)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unwrap master key", e)
            Result.failure(BiometricKeyException("Key unwrapping failed: ${e.message}", e))
        }
    }

    // ========================================================================
    // KEY MANAGEMENT
    // ========================================================================

    /**
     * Checks if biometric key exists in Keystore.
     */
    fun hasBiometricKey(): Boolean {
        return try {
            keyStore.containsAlias(BIOMETRIC_KEY_ALIAS)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for biometric key", e)
            false
        }
    }

    /**
     * Deletes the biometric key from Keystore.
     * Called when user disables biometric unlock.
     */
    fun deleteBiometricKey(): Result<Unit> {
        return try {
            if (keyStore.containsAlias(BIOMETRIC_KEY_ALIAS)) {
                keyStore.deleteEntry(BIOMETRIC_KEY_ALIAS)
                Log.d(TAG, "Biometric key deleted")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete biometric key", e)
            Result.failure(BiometricKeyException("Key deletion failed: ${e.message}", e))
        }
    }

    /**
     * Checks if the biometric key is still valid.
     * Returns false if biometrics have changed (key invalidated).
     */
    fun isBiometricKeyValid(): Boolean {
        return try {
            getBiometricKey() != null
        } catch (e: KeyPermanentlyInvalidatedException) {
            Log.w(TAG, "Biometric key invalidated")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error validating biometric key", e)
            false
        }
    }
}

// ========================================================================
// DATA CLASSES
// ========================================================================

/**
 * Container for wrapped (encrypted) Master Encryption Key.
 *
 * @property encryptedKey AES-GCM encrypted MEK (includes auth tag)
 * @property iv Initialization vector for decryption
 */
data class WrappedKey(
    val encryptedKey: ByteArray,
    val iv: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as WrappedKey
        if (!encryptedKey.contentEquals(other.encryptedKey)) return false
        if (!iv.contentEquals(other.iv)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = encryptedKey.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        return result
    }

    /**
     * Securely wipes encrypted key and IV from memory.
     * SECURITY: Call this when wrapped key is no longer needed.
     */
    fun secureWipe() {
        encryptedKey.secureWipe()
        iv.secureWipe()
    }
}

// ========================================================================
// EXCEPTIONS
// ========================================================================

/**
 * Exception thrown when biometric key operations fail.
 */
class BiometricKeyException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Exception thrown when biometric key is invalidated due to biometric changes.
 */
class BiometricKeyInvalidatedException(message: String, cause: Throwable? = null) : Exception(message, cause)
