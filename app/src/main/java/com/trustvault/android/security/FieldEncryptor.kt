package com.trustvault.android.security

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles field-level encryption for sensitive credential data.
 * Uses Android Keystore for key management.
 */
@Singleton
class FieldEncryptor @Inject constructor(
    private val keystoreManager: AndroidKeystoreManager
) {

    /**
     * Encrypts a string value.
     * Returns Base64-encoded encrypted data.
     */
    fun encrypt(value: String): String {
        if (value.isEmpty()) return ""
        
        val encrypted = keystoreManager.encrypt(
            KEY_ALIAS,
            value.toByteArray(Charsets.UTF_8)
        )
        
        return android.util.Base64.encodeToString(
            encrypted,
            android.util.Base64.NO_WRAP
        )
    }

    /**
     * Decrypts a Base64-encoded encrypted string.
     * Returns the original string value.
     */
    fun decrypt(encryptedValue: String): String {
        if (encryptedValue.isEmpty()) return ""
        
        val encrypted = android.util.Base64.decode(
            encryptedValue,
            android.util.Base64.NO_WRAP
        )
        
        val decrypted = keystoreManager.decrypt(KEY_ALIAS, encrypted)
        return String(decrypted, Charsets.UTF_8)
    }

    companion object {
        private const val KEY_ALIAS = "trustvault_field_encryption_key"
    }
}
