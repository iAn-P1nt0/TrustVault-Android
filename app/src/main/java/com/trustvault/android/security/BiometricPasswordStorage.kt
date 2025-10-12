package com.trustvault.android.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Secure storage for master password when biometric authentication is enabled.
 *
 * SECURITY NOTES:
 * - Uses Android Keystore with hardware-backed encryption
 * - Password only stored when user explicitly enables biometric auth
 * - Encrypted with AES-256-GCM via EncryptedSharedPreferences
 * - Key material never leaves secure hardware on devices with StrongBox
 *
 * THREAT MODEL:
 * - Protects against: Memory dumps, file system access, backup extraction
 * - Vulnerable to: Root access (can extract from running process), physical attacks on Keystore
 *
 * This is a necessary trade-off for biometric convenience. Users should be warned
 * that biometric auth is less secure than password-only authentication.
 */
@Singleton
class BiometricPasswordStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val masterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val encryptedPrefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * Stores the master password encrypted with hardware-backed key.
     * Only call this when user explicitly enables biometric authentication.
     *
     * @param password The master password to store
     */
    fun storePassword(password: String) {
        encryptedPrefs.edit()
            .putString(KEY_MASTER_PASSWORD, password)
            .apply()
    }

    /**
     * Retrieves the stored master password.
     * Returns null if no password is stored or if biometric auth is disabled.
     *
     * @return The decrypted master password, or null if not available
     */
    fun getPassword(): String? {
        return encryptedPrefs.getString(KEY_MASTER_PASSWORD, null)
    }

    /**
     * Checks if a password is stored for biometric authentication.
     *
     * @return true if password is stored, false otherwise
     */
    fun hasStoredPassword(): Boolean {
        return encryptedPrefs.contains(KEY_MASTER_PASSWORD)
    }

    /**
     * Removes the stored password.
     * Should be called when user disables biometric authentication.
     */
    fun clearPassword() {
        encryptedPrefs.edit()
            .remove(KEY_MASTER_PASSWORD)
            .apply()
    }

    /**
     * Completely clears all stored data.
     * Use when user logs out or resets the app.
     */
    fun clearAll() {
        encryptedPrefs.edit()
            .clear()
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "trustvault_biometric_secure_storage"
        private const val KEY_MASTER_PASSWORD = "master_password_encrypted"
    }
}
