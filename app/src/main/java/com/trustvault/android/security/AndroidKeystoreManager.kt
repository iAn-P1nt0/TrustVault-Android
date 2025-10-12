package com.trustvault.android.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages encryption keys using Android Keystore system.
 * Uses StrongBox-backed keys when available for maximum hardware security.
 * Falls back gracefully to standard hardware-backed keys.
 *
 * Security Features:
 * - StrongBox support (Android 9+) for tamper-resistant key storage
 * - Hardware-backed keys with secure element
 * - User authentication requirement support
 * - Automatic fallback to standard keystore
 */
@Singleton
class AndroidKeystoreManager @Inject constructor() {

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    /**
     * Generates or retrieves a SecretKey from Android Keystore.
     * Attempts to use StrongBox hardware (Android 9+) for maximum security,
     * falls back to standard hardware-backed keys if unavailable.
     */
    fun getOrCreateKey(
        keyAlias: String,
        requireUserAuthentication: Boolean = false,
        userAuthenticationValiditySeconds: Int = 30
    ): SecretKey {
        if (keyStore.containsAlias(keyAlias)) {
            return keyStore.getKey(keyAlias, null) as SecretKey
        }

        return createKey(keyAlias, requireUserAuthentication, userAuthenticationValiditySeconds)
    }

    /**
     * Creates a new key with StrongBox support if available.
     * OWASP Mobile Best Practice: Use hardware-backed keys with StrongBox
     * when available for maximum tamper resistance.
     */
    private fun createKey(
        keyAlias: String,
        requireUserAuthentication: Boolean,
        userAuthenticationValiditySeconds: Int
    ): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        // Try StrongBox first (Android 9+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val keySpec = buildKeySpec(
                    keyAlias,
                    requireUserAuthentication,
                    userAuthenticationValiditySeconds,
                    useStrongBox = true
                )
                keyGenerator.init(keySpec)
                val key = keyGenerator.generateKey()

                // Verify StrongBox was actually used
                if (isKeyInStrongBox(key)) {
                    Log.d(TAG, "StrongBox key created successfully for alias: $keyAlias")
                    return key
                } else {
                    Log.w(TAG, "StrongBox requested but not available, falling back...")
                    keyStore.deleteEntry(keyAlias)
                }
            } catch (e: Exception) {
                Log.w(TAG, "StrongBox not available, falling back to standard keystore: ${e.message}")
                keyStore.deleteEntry(keyAlias)
            }
        }

        // Fallback to standard hardware-backed keystore
        val keySpec = buildKeySpec(
            keyAlias,
            requireUserAuthentication,
            userAuthenticationValiditySeconds,
            useStrongBox = false
        )
        keyGenerator.init(keySpec)
        val key = keyGenerator.generateKey()
        Log.d(TAG, "Standard hardware-backed key created for alias: $keyAlias")
        return key
    }

    /**
     * Builds KeyGenParameterSpec with appropriate security settings.
     */
    private fun buildKeySpec(
        keyAlias: String,
        requireUserAuthentication: Boolean,
        userAuthenticationValiditySeconds: Int,
        useStrongBox: Boolean
    ): KeyGenParameterSpec {
        val builder = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)

        // StrongBox configuration (Android 9+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && useStrongBox) {
            builder.setIsStrongBoxBacked(true)
        }

        // User authentication configuration
        if (requireUserAuthentication) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+: Use timeout-based authentication
                builder.setUserAuthenticationParameters(
                    userAuthenticationValiditySeconds,
                    KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
                )
            } else {
                // Android 8-10: Legacy authentication
                @Suppress("DEPRECATION")
                builder.setUserAuthenticationRequired(true)
                    .setUserAuthenticationValidityDurationSeconds(userAuthenticationValiditySeconds)
            }
        } else {
            builder.setUserAuthenticationRequired(false)
        }

        return builder.build()
    }

    /**
     * Verifies if a key is actually backed by StrongBox hardware.
     * Android 9+ only.
     */
    private fun isKeyInStrongBox(key: SecretKey): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val factory = SecretKeyFactory.getInstance(key.algorithm, ANDROID_KEYSTORE)
                val keyInfo = factory.getKeySpec(key, KeyInfo::class.java) as KeyInfo
                @Suppress("NewApi") // We check API level above
                return keyInfo.isInsideSecureHardware && keyInfo.securityLevel == KeyProperties.SECURITY_LEVEL_STRONGBOX
            } catch (e: Exception) {
                Log.e(TAG, "Error checking StrongBox status: ${e.message}")
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Android 9-11: Use deprecated isInsideSecureHardware
            try {
                val factory = SecretKeyFactory.getInstance(key.algorithm, ANDROID_KEYSTORE)
                val keyInfo = factory.getKeySpec(key, KeyInfo::class.java) as KeyInfo
                @Suppress("DEPRECATION")
                return keyInfo.isInsideSecureHardware
            } catch (e: Exception) {
                Log.e(TAG, "Error checking hardware backing: ${e.message}")
            }
        }
        return false
    }

    /**
     * Gets security information about a key.
     * Returns info about hardware backing and StrongBox usage.
     */
    fun getKeySecurityInfo(keyAlias: String): KeySecurityInfo? {
        if (!keyStore.containsAlias(keyAlias)) {
            return null
        }

        return try {
            val key = keyStore.getKey(keyAlias, null) as SecretKey
            val factory = SecretKeyFactory.getInstance(key.algorithm, ANDROID_KEYSTORE)
            val keyInfo = factory.getKeySpec(key, KeyInfo::class.java) as KeyInfo

            val isStrongBoxBacked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                @Suppress("NewApi") // We check API level above
                keyInfo.securityLevel == KeyProperties.SECURITY_LEVEL_STRONGBOX
            } else {
                false
            }

            @Suppress("DEPRECATION")
            val isHardwareBacked = keyInfo.isInsideSecureHardware

            KeySecurityInfo(
                isInsideSecureHardware = isHardwareBacked,
                isStrongBoxBacked = isStrongBoxBacked,
                userAuthenticationRequired = keyInfo.isUserAuthenticationRequired
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting key security info: ${e.message}")
            null
        }
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
        private const val TAG = "AndroidKeystoreManager"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12 // GCM standard IV size
        private const val TAG_SIZE = 128 // GCM authentication tag size in bits
    }
}

/**
 * Data class containing security information about a keystore key.
 */
data class KeySecurityInfo(
    val isInsideSecureHardware: Boolean,
    val isStrongBoxBacked: Boolean,
    val userAuthenticationRequired: Boolean
)
