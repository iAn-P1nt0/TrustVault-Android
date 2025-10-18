package com.trustvault.android.security

import android.security.keystore.KeyGenParameterSpec
import io.mockk.MockKException
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * In-memory mock Keystore for testing cryptographic operations without
 * requiring actual Android Keystore access in unit tests.
 *
 * SECURITY NOTE: This is test-only code. Never use in production.
 * Android Keystore provides hardware backing and security boundaries
 * that this mock cannot replicate.
 *
 * Features:
 * - Generates AES-256 keys in-memory
 * - Performs AES-GCM encryption/decryption
 * - Tracks key lifecycle (creation, deletion, existence)
 * - Prevents operations on non-existent keys
 *
 * Limitations (acceptable for testing):
 * - No hardware backing (software-only keys)
 * - No security boundaries enforced
 * - No key expiration or rotation
 * - No access control or biometric requirements
 *
 * Usage:
 * ```kotlin
 * val mockKeystore = MockAndroidKeystore()
 * mockKeystore.generateKey("test_key_1")
 * val cipher = mockKeystore.getCipher("test_key_1", Cipher.ENCRYPT_MODE)
 * ```
 */
class MockAndroidKeystore {
    private val keyStore = mutableMapOf<String, SecretKey>()
    private val random = SecureRandom()

    /**
     * Generate a new AES-256 key with the given alias.
     *
     * @param alias Unique identifier for the key
     * @throws IllegalArgumentException if key already exists
     */
    fun generateKey(alias: String) {
        if (keyStore.containsKey(alias)) {
            throw IllegalArgumentException("Key with alias '$alias' already exists")
        }

        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(256, random)
        val key = keyGenerator.generateKey()
        keyStore[alias] = key
    }

    /**
     * Get a cipher initialized with the key at given alias.
     *
     * @param alias Key identifier
     * @param opMode Cipher.ENCRYPT_MODE or Cipher.DECRYPT_MODE
     * @param iv Initialization vector (required for GCM mode)
     * @return Configured Cipher instance
     * @throws IllegalArgumentException if key doesn't exist
     */
    fun getCipher(alias: String, opMode: Int, iv: ByteArray? = null): Cipher {
        val key = keyStore[alias] ?: throw IllegalArgumentException(
            "Key with alias '$alias' not found. Available keys: ${keyStore.keys}"
        )

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")

        return when (opMode) {
            Cipher.ENCRYPT_MODE -> {
                val generatedIv = ByteArray(12)
                random.nextBytes(generatedIv)
                cipher.init(opMode, key, GCMParameterSpec(128, generatedIv))
                cipher
            }
            Cipher.DECRYPT_MODE -> {
                val decryptIv = iv ?: throw IllegalArgumentException(
                    "IV required for decryption"
                )
                cipher.init(opMode, key, GCMParameterSpec(128, decryptIv))
                cipher
            }
            else -> throw IllegalArgumentException("Unsupported cipher mode: $opMode")
        }
    }

    /**
     * Check if key exists.
     */
    fun keyExists(alias: String): Boolean = keyStore.containsKey(alias)

    /**
     * Delete a key.
     */
    fun deleteKey(alias: String) {
        keyStore.remove(alias)
    }

    /**
     * List all key aliases.
     */
    fun listAliases(): Set<String> = keyStore.keys

    /**
     * Clear all stored keys.
     */
    fun clearAllKeys() {
        keyStore.clear()
    }
}

/**
 * Test helper for cryptographic operations using mock keystore.
 * Provides encrypt/decrypt utilities with IV management.
 */
class MockCryptoHelper(private val mockKeystore: MockAndroidKeystore = MockAndroidKeystore()) {

    /**
     * Encrypt data with specified key.
     *
     * Returns Pair(ciphertext, iv)
     *
     * @param keyAlias Key identifier
     * @param plaintext Data to encrypt
     * @return Pair of encrypted data and initialization vector
     */
    fun encrypt(keyAlias: String, plaintext: ByteArray): Pair<ByteArray, ByteArray> {
        val cipher = mockKeystore.getCipher(keyAlias, Cipher.ENCRYPT_MODE)
        val ciphertext = cipher.doFinal(plaintext)
        val iv = cipher.iv
        return Pair(ciphertext, iv)
    }

    /**
     * Decrypt data with specified key.
     *
     * @param keyAlias Key identifier
     * @param ciphertext Encrypted data
     * @param iv Initialization vector
     * @return Decrypted plaintext
     */
    fun decrypt(keyAlias: String, ciphertext: ByteArray, iv: ByteArray): ByteArray {
        val cipher = mockKeystore.getCipher(keyAlias, Cipher.DECRYPT_MODE, iv)
        return cipher.doFinal(ciphertext)
    }

    /**
     * Round-trip test: encrypt then decrypt data.
     *
     * @param keyAlias Key identifier
     * @param plaintext Original data
     * @return True if decrypt result matches original
     */
    fun testRoundTrip(keyAlias: String, plaintext: ByteArray): Boolean {
        val (ciphertext, iv) = encrypt(keyAlias, plaintext)
        val decrypted = decrypt(keyAlias, ciphertext, iv)
        return plaintext.contentEquals(decrypted)
    }

    /**
     * Get underlying mock keystore for direct access.
     */
    fun getKeystore(): MockAndroidKeystore = mockKeystore
}
