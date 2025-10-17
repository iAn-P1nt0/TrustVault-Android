package com.trustvault.android.security

import android.content.Context
import android.provider.Settings
import com.trustvault.android.util.secureWipe
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import java.util.Base64

/**
 * Derives secure database encryption keys from master password.
 * Uses PBKDF2-HMAC-SHA256 with device-specific salt for additional security.
 *
 * Security Features:
 * - Master password + device binding (ANDROID_ID)
 * - 600,000 iterations (OWASP 2025 standard for PBKDF2-HMAC-SHA256)
 * - 256-bit output key length
 * - Additional random salt stored securely
 * - Protection against rainbow table attacks
 */
@Singleton
class DatabaseKeyDerivation @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keystoreManager: AndroidKeystoreManager
) {

    /**
     * Derives a 256-bit database encryption key from the master password.
     * This key is used to encrypt the SQLCipher database.
     *
     * The derivation uses:
     * 1. Master password (user input as CharArray for secure memory handling)
     * 2. Device-specific identifier (ANDROID_ID)
     * 3. Random salt (generated once, stored encrypted)
     * 4. PBKDF2-HMAC-SHA256 with 600,000 iterations (OWASP 2025 standard)
     *
     * SECURITY: Accepts CharArray to allow secure memory wiping after use.
     *
     * @param masterPassword The user's master password as CharArray
     * @return 256-bit key suitable for AES-256 encryption (must be wiped after use)
     */
    fun deriveKey(masterPassword: CharArray): ByteArray {
        require(masterPassword.isNotEmpty()) { "Master password cannot be empty" }

        // Get device-specific identifier
        val deviceId = getDeviceIdentifier()

        // Get or create random salt (stored encrypted in Android Keystore)
        val salt = getOrCreateSalt()

        // Combine device ID and salt for additional entropy
        val finalSalt = (deviceId + salt.contentToString()).toByteArray()

        try {
            // Derive key using PBKDF2
            return deriveKeyWithPBKDF2(
                password = masterPassword,
                salt = finalSalt,
                iterations = PBKDF2_ITERATIONS,
                keyLength = KEY_LENGTH_BITS
            )
        } finally {
            // SECURITY CONTROL: Clear salt from memory
            finalSalt.secureWipe()
        }
    }

    /**
     * Derives a key using PBKDF2-HMAC-SHA256.
     * This is a computationally expensive operation (intentional security feature).
     *
     * SECURITY: Accepts CharArray directly to avoid creating intermediate String copies.
     */
    private fun deriveKeyWithPBKDF2(
        password: CharArray,
        salt: ByteArray,
        iterations: Int,
        keyLength: Int
    ): ByteArray {
        val spec = PBEKeySpec(
            password,
            salt,
            iterations,
            keyLength
        )

        try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            return factory.generateSecret(spec).encoded
        } finally {
            // SECURITY CONTROL: Clear sensitive data from memory
            spec.clearPassword()
        }
    }

    /**
     * Gets a stable device identifier.
     * Uses ANDROID_ID which is unique per app per device.
     */
    private fun getDeviceIdentifier(): String {
        return Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "default_device_id"
    }

    /**
     * Gets or creates a random salt for key derivation.
     * The salt is generated once and stored encrypted using Android Keystore.
     * This prevents the same master password from generating the same key across devices.
     */
    private fun getOrCreateSalt(): ByteArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val encryptedSaltBase64 = prefs.getString(KEY_SALT, null)

        return if (encryptedSaltBase64 != null) {
            // Decrypt existing salt
            val encryptedSalt = Base64.getDecoder().decode(encryptedSaltBase64)
            keystoreManager.decrypt(SALT_KEY_ALIAS, encryptedSalt)
        } else {
            // Generate new random salt
            val salt = ByteArray(SALT_LENGTH)
            SecureRandom().nextBytes(salt)

            // Encrypt and store salt
            val encryptedSalt = keystoreManager.encrypt(SALT_KEY_ALIAS, salt)
            val encryptedSaltBase64 = Base64.getEncoder().encodeToString(encryptedSalt)

            prefs.edit()
                .putString(KEY_SALT, encryptedSaltBase64)
                .apply()

            salt
        }
    }

    /**
     * Clears all stored key derivation data.
     * Should be called when resetting the app or changing master password.
     */
    fun clearKeyDerivationData() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        keystoreManager.deleteKey(SALT_KEY_ALIAS)
    }

    /**
     * Validates that a master password can derive a valid key.
     * Used for testing and validation purposes.
     *
     * @param masterPassword The master password as CharArray
     * @return true if key derivation succeeds and produces correct length key
     */
    fun validateMasterPassword(masterPassword: CharArray): Boolean {
        return try {
            val key = deriveKey(masterPassword)
            val isValid = key.size == KEY_LENGTH_BITS / 8
            // SECURITY CONTROL: Clear key from memory
            key.secureWipe()
            isValid
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        // OWASP 2025 recommends minimum 600,000 iterations for PBKDF2-HMAC-SHA256
        // Updated from 100,000 to meet current security standards
        // Reference: https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html
        private const val PBKDF2_ITERATIONS = 600_000

        // 256-bit key for AES-256 encryption
        private const val KEY_LENGTH_BITS = 256

        // Salt length (128 bits recommended minimum)
        private const val SALT_LENGTH = 16

        // Storage keys
        private const val PREFS_NAME = "trustvault_key_derivation"
        private const val KEY_SALT = "db_key_salt"
        private const val SALT_KEY_ALIAS = "trustvault_salt_encryption_key"
    }
}