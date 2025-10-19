package com.trustvault.android.security

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive unit tests for CryptoManager.
 *
 * Tests cover:
 * - AES-256-GCM encryption/decryption
 * - ChaCha20-Poly1305 fallback
 * - Password hashing with Argon2id
 * - Key derivation with PBKDF2
 * - Random number generation
 * - Algorithm selection
 * - Serialization/deserialization
 * - Error handling
 *
 * NOTE: Some tests are limited due to Android Keystore requirements.
 * Full integration tests should be run on device/emulator.
 */
class CryptoManagerTest {

    private lateinit var context: Context
    private lateinit var keystoreManager: AndroidKeystoreManager
    private lateinit var passwordHasher: PasswordHasher
    private lateinit var databaseKeyDerivation: DatabaseKeyDerivation
    private lateinit var cryptoManager: CryptoManager

    @Before
    fun setup() {
        // Mock context
        context = mockk<Context>(relaxed = true)
        every { context.getSharedPreferences(any(), any()) } returns mockk(relaxed = true)
        every { context.cacheDir } returns mockk(relaxed = true)
        every { context.contentResolver } returns mockk(relaxed = true)
        keystoreManager = AndroidKeystoreManager()

        // Setup PasswordHasher with mock engine to avoid native library issues in tests
        passwordHasher = PasswordHasher().apply {
            setTestEngine(object : PasswordHasher.Engine {
                override fun hash(
                    password: ByteArray,
                    salt: ByteArray,
                    tCostInIterations: Int,
                    mCostInKibibyte: Int,
                    parallelism: Int
                ): String {
                    // Simple hash for testing (not cryptographically secure)
                    val combined = password.contentToString() + salt.contentToString()
                    return "argon2id\$v=19\$m=$mCostInKibibyte,t=$tCostInIterations,p=$parallelism\$${combined.hashCode()}"
                }

                override fun verify(password: ByteArray, encoded: String): Boolean {
                    return encoded.contains(password.contentToString().hashCode().toString())
                }
            })
        }

        databaseKeyDerivation = DatabaseKeyDerivation(context, keystoreManager)
        cryptoManager = CryptoManager(context, keystoreManager, passwordHasher, databaseKeyDerivation)
    }

    @After
    fun teardown() {
        // Clean up any test keys
        try {
            keystoreManager.deleteKey("test_aes_key")
            keystoreManager.deleteKey("test_chacha_key")
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }

    // ========================================================================
    // AES-256-GCM ENCRYPTION TESTS
    // ========================================================================

    @Test
    fun `encrypt and decrypt with AES-GCM succeeds`() {
        // Given
        val plaintext = "Hello, TrustVault!".toByteArray()
        val keyAlias = "test_aes_key"

        // When
        val encrypted = cryptoManager.encrypt(
            plaintext,
            CryptoManager.Algorithm.AES_256_GCM,
            keyAlias
        )
        val decrypted = cryptoManager.decrypt(encrypted, keyAlias)

        // Then
        assertArrayEquals("Decrypted data should match original", plaintext, decrypted)
        assertEquals("Algorithm should be AES-256-GCM", CryptoManager.Algorithm.AES_256_GCM, encrypted.algorithm)
        assertEquals("Version should be current", CryptoManager.CRYPTO_VERSION, encrypted.version)
        assertEquals("IV should be 12 bytes for GCM", 12, encrypted.iv.size)
    }

    @Test
    fun `encrypt produces different ciphertext for same plaintext`() {
        // Given
        val plaintext = "Same plaintext".toByteArray()
        val keyAlias = "test_aes_key"

        // When
        val encrypted1 = cryptoManager.encrypt(plaintext, CryptoManager.Algorithm.AES_256_GCM, keyAlias)
        val encrypted2 = cryptoManager.encrypt(plaintext, CryptoManager.Algorithm.AES_256_GCM, keyAlias)

        // Then
        assertFalse("IVs should be different", encrypted1.iv.contentEquals(encrypted2.iv))
        assertFalse("Ciphertexts should be different", encrypted1.ciphertext.contentEquals(encrypted2.ciphertext))
    }

    @Test
    fun `decrypt with wrong key alias fails`() {
        // Given
        val plaintext = "Secret data".toByteArray()
        val encrypted = cryptoManager.encrypt(plaintext, CryptoManager.Algorithm.AES_256_GCM, "key1")

        // When/Then
        try {
            cryptoManager.decrypt(encrypted, "wrong_key")
            fail("Should throw exception with wrong key")
        } catch (e: Exception) {
            // Expected - decryption should fail with wrong key
            assertTrue("Should fail with crypto-related exception", true)
        }
    }

    @Test
    fun `encryptString and decryptString work correctly`() {
        // Given
        val plaintext = "Test string with special chars: 🔐 !@#$%"
        val keyAlias = "test_string_key"

        // When
        val encrypted = cryptoManager.encryptString(plaintext, CryptoManager.Algorithm.AES_256_GCM, keyAlias)
        val decrypted = cryptoManager.decryptString(encrypted, keyAlias)

        // Then
        assertEquals("Decrypted string should match original", plaintext, decrypted)
    }

    @Test
    fun `encrypt empty plaintext fails`() {
        // When/Then
        try {
            cryptoManager.encrypt(ByteArray(0), CryptoManager.Algorithm.AES_256_GCM, "test_key")
            fail("Should not allow empty plaintext")
        } catch (e: IllegalArgumentException) {
            assertEquals("Plaintext cannot be empty", e.message)
        }
    }

    // ========================================================================
    // CHACHA20-POLY1305 TESTS
    // ========================================================================

    @Test
    fun `encrypt and decrypt with ChaCha20 succeeds on Android 9+`() {
        // Given
        val plaintext = "ChaCha20 test data".toByteArray()
        val keyAlias = "test_chacha_key"

        // When
        try {
            val encrypted = cryptoManager.encrypt(
                plaintext,
                CryptoManager.Algorithm.CHACHA20_POLY1305,
                keyAlias
            )
            val decrypted = cryptoManager.decrypt(encrypted, keyAlias)

            // Then
            assertArrayEquals("Decrypted data should match original", plaintext, decrypted)
            assertEquals("Algorithm should be ChaCha20-Poly1305",
                CryptoManager.Algorithm.CHACHA20_POLY1305, encrypted.algorithm)
            assertEquals("Nonce should be 12 bytes", 12, encrypted.iv.size)
        } catch (e: CryptoException) {
            // ChaCha20 support varies by device, may not be available in test environment
            assertTrue("If ChaCha20 fails, it should be with appropriate exception",
                e.message?.contains("ChaCha20") == true)
        }
    }

    @Test
    fun `ChaCha20 fails gracefully on Android 8`() {
        // Given
        val plaintext = "Test data".toByteArray()

        // When/Then
        try {
            cryptoManager.encrypt(plaintext, CryptoManager.Algorithm.CHACHA20_POLY1305, "test_key")
            fail("ChaCha20 should not be available on Android 8")
        } catch (e: CryptoException) {
            assertTrue("Should indicate ChaCha20 requires Android 9+",
                e.message?.contains("Android 9") == true || e.message?.contains("not supported") == true)
        }
    }

    // ========================================================================
    // AUTO ALGORITHM SELECTION TESTS
    // ========================================================================

    @Test
    fun `AUTO algorithm selection chooses appropriate algorithm`() {
        // Given
        val plaintext = "Auto selection test".toByteArray()
        val keyAlias = "test_auto_key"

        // When
        val encrypted = cryptoManager.encrypt(plaintext, CryptoManager.Algorithm.AUTO, keyAlias)
        val decrypted = cryptoManager.decrypt(encrypted, keyAlias)

        // Then
        assertArrayEquals("Auto-selected algorithm should work correctly", plaintext, decrypted)
        assertTrue("Should select AES-GCM or ChaCha20",
            encrypted.algorithm == CryptoManager.Algorithm.AES_256_GCM ||
            encrypted.algorithm == CryptoManager.Algorithm.CHACHA20_POLY1305)
    }

    // ========================================================================
    // PASSWORD HASHING TESTS
    // ========================================================================

    @Test
    fun `hashPassword creates verifiable hash`() {
        // Given
        val password = "SecurePassword123!".toCharArray()

        // When
        val hash = cryptoManager.hashPassword(password)

        // Then
        assertNotNull("Hash should not be null", hash)
        assertTrue("Hash should contain algorithm info", hash.contains("argon2id"))
        assertTrue("Hash should contain parameters", hash.contains("m=65536") && hash.contains("t=3") && hash.contains("p=4"))
    }

    @Test
    fun `verifyPassword accepts correct password`() {
        // Given
        val password = "CorrectPassword".toCharArray()
        val hash = cryptoManager.hashPassword(password)

        // When
        val isValid = cryptoManager.verifyPassword(password, hash)

        // Then
        assertTrue("Correct password should verify successfully", isValid)
    }

    @Test
    fun `verifyPassword rejects incorrect password`() {
        // Given
        val correctPassword = "CorrectPassword".toCharArray()
        val wrongPassword = "WrongPassword".toCharArray()
        val hash = cryptoManager.hashPassword(correctPassword)

        // When
        val isValid = cryptoManager.verifyPassword(wrongPassword, hash)

        // Then
        assertFalse("Wrong password should not verify", isValid)
    }

    @Test
    fun `hashPassword produces different hashes for same password`() {
        // Given
        val password = "SamePassword".toCharArray()

        // When
        val hash1 = cryptoManager.hashPassword(password)
        val hash2 = cryptoManager.hashPassword(password)

        // Then
        assertNotEquals("Different salts should produce different hashes", hash1, hash2)
    }

    // ========================================================================
    // KEY DERIVATION TESTS
    // ========================================================================

    @Test
    fun `deriveKey produces 256-bit key`() {
        // Given
        val password = "MasterPassword123".toCharArray()

        // When
        val key = cryptoManager.deriveKey(password)

        // Then
        assertEquals("Key should be 256 bits (32 bytes)", 32, key.size)

        // Cleanup
        key.fill(0)
    }

    @Test
    fun `deriveKey produces consistent key for same password`() {
        // Given
        val password = "ConsistentPassword".toCharArray()

        // When
        val key1 = cryptoManager.deriveKey(password)
        val key2 = cryptoManager.deriveKey(password)

        // Then
        assertArrayEquals("Same password should produce same key", key1, key2)

        // Cleanup
        key1.fill(0)
        key2.fill(0)
    }

    @Test
    fun `deriveKey produces different keys for different passwords`() {
        // Given
        val password1 = "Password1".toCharArray()
        val password2 = "Password2".toCharArray()

        // When
        val key1 = cryptoManager.deriveKey(password1)
        val key2 = cryptoManager.deriveKey(password2)

        // Then
        assertFalse("Different passwords should produce different keys", key1.contentEquals(key2))

        // Cleanup
        key1.fill(0)
        key2.fill(0)
    }

    // ========================================================================
    // RANDOM NUMBER GENERATION TESTS
    // ========================================================================

    @Test
    fun `generateRandomBytes produces requested size`() {
        // Given
        val sizes = listOf(16, 32, 64, 128)

        // When/Then
        sizes.forEach { size ->
            val random = cryptoManager.generateRandomBytes(size)
            assertEquals("Should generate exactly $size bytes", size, random.size)
        }
    }

    @Test
    fun `generateRandomBytes produces different values`() {
        // When
        val random1 = cryptoManager.generateRandomBytes(32)
        val random2 = cryptoManager.generateRandomBytes(32)

        // Then
        assertFalse("Consecutive calls should produce different random values",
            random1.contentEquals(random2))
    }

    @Test
    fun `generateRandomBytes fails with invalid size`() {
        // When/Then
        try {
            cryptoManager.generateRandomBytes(0)
            fail("Should not allow zero size")
        } catch (e: IllegalArgumentException) {
            assertEquals("Size must be positive", e.message)
        }

        try {
            cryptoManager.generateRandomBytes(-1)
            fail("Should not allow negative size")
        } catch (e: IllegalArgumentException) {
            assertEquals("Size must be positive", e.message)
        }
    }

    @Test
    fun `generateIV produces correct size for AES-GCM`() {
        // When
        val iv = cryptoManager.generateIV(CryptoManager.Algorithm.AES_256_GCM)

        // Then
        assertEquals("AES-GCM IV should be 12 bytes", 12, iv.size)
    }

    @Test
    fun `generateIV produces correct size for ChaCha20`() {
        // When
        val nonce = cryptoManager.generateIV(CryptoManager.Algorithm.CHACHA20_POLY1305)

        // Then
        assertEquals("ChaCha20 nonce should be 12 bytes", 12, nonce.size)
    }

    // ========================================================================
    // SERIALIZATION TESTS
    // ========================================================================

    @Test
    fun `serialization round-trip preserves data`() {
        // Given
        val plaintext = "Serialization test data with special chars: 🔐".toByteArray()
        val keyAlias = "test_serialize_key"

        // When
        val encrypted = cryptoManager.encrypt(plaintext, CryptoManager.Algorithm.AES_256_GCM, keyAlias)
        val serialized = cryptoManager.encryptString(String(plaintext, Charsets.UTF_8),
            CryptoManager.Algorithm.AES_256_GCM, keyAlias)
        val deserialized = cryptoManager.decryptString(serialized, keyAlias)

        // Then
        assertEquals("Deserialized data should match original",
            String(plaintext, Charsets.UTF_8), deserialized)
    }

    // ========================================================================
    // ENCRYPTED DATA TESTS
    // ========================================================================

    @Test
    fun `EncryptedData secureWipe clears sensitive data`() {
        // Given
        val iv = ByteArray(12) { it.toByte() }
        val ciphertext = ByteArray(32) { it.toByte() }
        val encrypted = CryptoManager.EncryptedData(
            algorithm = CryptoManager.Algorithm.AES_256_GCM,
            version = 1,
            iv = iv,
            ciphertext = ciphertext
        )

        // When
        encrypted.secureWipe()

        // Then
        assertTrue("IV should be wiped", iv.all { it == 0.toByte() })
        assertTrue("Ciphertext should be wiped", ciphertext.all { it == 0.toByte() })
    }

    @Test
    fun `EncryptedData equals works correctly`() {
        // Given
        val data1 = CryptoManager.EncryptedData(
            algorithm = CryptoManager.Algorithm.AES_256_GCM,
            version = 1,
            iv = byteArrayOf(1, 2, 3),
            ciphertext = byteArrayOf(4, 5, 6)
        )
        val data2 = CryptoManager.EncryptedData(
            algorithm = CryptoManager.Algorithm.AES_256_GCM,
            version = 1,
            iv = byteArrayOf(1, 2, 3),
            ciphertext = byteArrayOf(4, 5, 6)
        )
        val data3 = CryptoManager.EncryptedData(
            algorithm = CryptoManager.Algorithm.AES_256_GCM,
            version = 1,
            iv = byteArrayOf(1, 2, 3),
            ciphertext = byteArrayOf(7, 8, 9)
        )

        // Then
        assertEquals("Same data should be equal", data1, data2)
        assertNotEquals("Different ciphertext should not be equal", data1, data3)
    }

    // ========================================================================
    // ERROR HANDLING TESTS
    // ========================================================================

    @Test
    fun `decrypt with unsupported version fails gracefully`() {
        // Given
        val futureVersion = CryptoManager.EncryptedData(
            algorithm = CryptoManager.Algorithm.AES_256_GCM,
            version = 999,
            iv = ByteArray(12),
            ciphertext = ByteArray(32)
        )

        // When/Then
        try {
            cryptoManager.decrypt(futureVersion, "test_key")
            fail("Should reject future version")
        } catch (e: CryptoException) {
            assertTrue("Should indicate unsupported version",
                e.message?.contains("Unsupported encryption version") == true)
        }
    }

    @Test
    fun `decrypt without key alias fails`() {
        // Given
        val plaintext = "Test data".toByteArray()
        val encrypted = cryptoManager.encrypt(plaintext, CryptoManager.Algorithm.AES_256_GCM, "test_key")

        // When/Then
        try {
            cryptoManager.decrypt(encrypted, null)
            fail("Should require key alias for decryption")
        } catch (e: CryptoException) {
            assertTrue("Should indicate missing key",
                e.message?.contains("Cannot decrypt without key") == true)
        }
    }

    // ========================================================================
    // INTEGRATION TESTS
    // ========================================================================

    @Test
    fun `full encryption workflow with multiple algorithms`() {
        // Test that we can encrypt with one algorithm and the system handles it correctly
        val testData = listOf(
            "Short text",
            "Longer text with multiple sentences and special characters: !@#$%^&*()",
            "Unicode: こんにちは世界 🌍🔐",
            "Numbers and symbols: 1234567890 ~`-_=+[]{}\\|;:'\",.<>?/"
        )

        testData.forEach { plaintext ->
            val keyAlias = "test_${plaintext.hashCode()}"
            val encrypted = cryptoManager.encryptString(plaintext, CryptoManager.Algorithm.AUTO, keyAlias)
            val decrypted = cryptoManager.decryptString(encrypted, keyAlias)

            assertEquals("Data should survive encryption round-trip", plaintext, decrypted)
        }
    }
}
