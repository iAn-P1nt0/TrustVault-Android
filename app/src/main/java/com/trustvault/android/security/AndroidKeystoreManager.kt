package com.trustvault.android.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages encryption keys using Android Keystore system.
 * Uses hardware-backed keys when available for maximum security.
 */
@Singleton
class AndroidKeystoreManager @Inject constructor() {

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    /**
     * Generates or retrieves a SecretKey from Android Keystore.
     * The key is hardware-backed when supported by the device.
     */
    fun getOrCreateKey(keyAlias: String): SecretKey {
        if (keyStore.containsAlias(keyAlias)) {
            return keyStore.getKey(keyAlias, null) as SecretKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        val keySpec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false) // Key is always available
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(keySpec)
        return keyGenerator.generateKey()
    }

    /**
     * Encrypts data using AES-GCM with a key from Android Keystore.
     * Returns encrypted data with IV prepended.
     */
    fun encrypt(keyAlias: String, data: ByteArray): ByteArray {
        val key = getOrCreateKey(keyAlias)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        
        val iv = cipher.iv
        val encrypted = cipher.doFinal(data)
        
        // Prepend IV to encrypted data
        return iv + encrypted
    }

    /**
     * Decrypts data using AES-GCM with a key from Android Keystore.
     * Expects IV to be prepended to encrypted data.
     */
    fun decrypt(keyAlias: String, encryptedDataWithIv: ByteArray): ByteArray {
        val key = getOrCreateKey(keyAlias)
        
        // Extract IV (first 12 bytes) and encrypted data
        val iv = encryptedDataWithIv.copyOfRange(0, IV_SIZE)
        val encryptedData = encryptedDataWithIv.copyOfRange(IV_SIZE, encryptedDataWithIv.size)
        
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(TAG_SIZE, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        
        return cipher.doFinal(encryptedData)
    }

    /**
     * Deletes a key from the keystore.
     */
    fun deleteKey(keyAlias: String) {
        if (keyStore.containsAlias(keyAlias)) {
            keyStore.deleteEntry(keyAlias)
        }
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12 // GCM standard IV size
        private const val TAG_SIZE = 128 // GCM authentication tag size in bits
    }
}
