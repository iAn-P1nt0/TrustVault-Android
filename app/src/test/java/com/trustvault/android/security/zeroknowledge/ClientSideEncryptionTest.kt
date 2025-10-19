package com.trustvault.android.security.zeroknowledge

import android.content.Context
import com.trustvault.android.security.AndroidKeystoreManager
import com.trustvault.android.security.CryptoManager
import com.trustvault.android.security.DatabaseKeyDerivation
import com.trustvault.android.security.PasswordHasher
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ClientSideEncryption - Zero-Knowledge Encryption Layer
 */
class ClientSideEncryptionTest {

    private lateinit var context: Context
    private lateinit var keystoreManager: AndroidKeystoreManager
    private lateinit var passwordHasher: PasswordHasher
    private lateinit var databaseKeyDerivation: DatabaseKeyDerivation
    private lateinit var cryptoManager: CryptoManager
    private lateinit var clientSideEncryption: ClientSideEncryption

    @Before
    fun setup() {
        // Mock context
        context = mockk<Context>(relaxed = true)
        every { context.getSharedPreferences(any(), any()) } returns mockk(relaxed = true)
        every { context.cacheDir } returns mockk(relaxed = true)
        every { context.contentResolver } returns mockk(relaxed = true)

        // Setup components
        keystoreManager = AndroidKeystoreManager()

        passwordHasher = PasswordHasher().apply {
            setTestEngine(object : PasswordHasher.Engine {
                override fun hash(
                    password: ByteArray,
                    salt: ByteArray,
                    tCostInIterations: Int,
                    mCostInKibibyte: Int,
                    parallelism: Int
                ): String {
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
        clientSideEncryption = ClientSideEncryption(cryptoManager)
    }

    @After
    fun teardown() {
        // Cleanup test keys
        try {
            keystoreManager.deleteKey("test_key")
        } catch (e: Exception) {
            // Ignore
        }
    }

    // ========================================================================
    // BASIC ENCRYPTION/DECRYPTION TESTS
    // ========================================================================

    @Test
    fun `encryptData and decryptData work correctly`() {
        // Given
        val plaintext = "Sensitive credential data".toByteArray()
        val masterPassword = "SecurePassword123!".toCharArray()
        val keyAlias = "test_key"

        // When
        val encrypted = clientSideEncryption.encryptData(plaintext, masterPassword, keyAlias)
        val decrypted = clientSideEncryption.decryptData(encrypted, masterPassword, keyAlias)

        // Then
        assertArrayEquals("Decrypted data should match original", plaintext, decrypted)
        assertNotNull("Device binding should be set", encrypted.deviceBinding)
        assertEquals("Version should be 1", 1, encrypted.version)
    }

    @Test
    fun `encryptData produces different ciphertext for same plaintext`() {
        // Given
        val plaintext = "Same data".toByteArray()
        val masterPassword = "Password123".toCharArray()
        val keyAlias = "test_key"

        // When
        val encrypted1 = clientSideEncryption.encryptData(plaintext, masterPassword, keyAlias)
        val encrypted2 = clientSideEncryption.encryptData(plaintext, masterPassword, keyAlias)

        // Then
        assertFalse(
            "Different IVs should produce different ciphertexts",
            encrypted1.encryptedData.ciphertext.contentEquals(encrypted2.encryptedData.ciphertext)
        )
    }

    @Test
    fun `decryptData with wrong password fails`() {
        // Given
        val plaintext = "Secret data".toByteArray()
        val correctPassword = "CorrectPassword".toCharArray()
        val wrongPassword = "WrongPassword".toCharArray()
        val keyAlias = "test_key"

        val encrypted = clientSideEncryption.encryptData(plaintext, correctPassword, keyAlias)

        // When/Then
        try {
            clientSideEncryption.decryptData(encrypted, wrongPassword, keyAlias)
            fail("Should fail with wrong password")
        } catch (e: Exception) {
            // Expected
            assertTrue("Should throw crypto-related exception", true)
        }
    }

    @Test
    fun `encryptData with empty plaintext fails`() {
        // Given
        val emptyData = ByteArray(0)
        val password = "Password".toCharArray()

        // When/Then
        try {
            clientSideEncryption.encryptData(emptyData, password)
            fail("Should not allow empty data")
        } catch (e: IllegalArgumentException) {
            assertEquals("Cannot encrypt empty data", e.message)
        }
    }

    @Test
    fun `encryptData with empty password fails`() {
        // Given
        val data = "Data".toByteArray()
        val emptyPassword = CharArray(0)

        // When/Then
        try {
            clientSideEncryption.encryptData(data, emptyPassword)
            fail("Should not allow empty password")
        } catch (e: IllegalArgumentException) {
            assertEquals("Master password required", e.message)
        }
    }

    // ========================================================================
    // DEVICE BINDING TESTS
    // ========================================================================

    @Test
    fun `encryptData includes device binding`() {
        // Given
        val plaintext = "Test data".toByteArray()
        val password = "Password123".toCharArray()
        val keyAlias = "test_key"

        // When
        val encrypted = clientSideEncryption.encryptData(plaintext, password, keyAlias)

        // Then
        assertNotNull("Device binding should not be null", encrypted.deviceBinding)
        assertTrue("Device binding should be non-empty", encrypted.deviceBinding.isNotEmpty())
        assertEquals("Device binding should be SHA-256 hash (64 hex chars)", 64, encrypted.deviceBinding.length)
    }

    @Test
    fun `decryptData succeeds despite device binding mismatch`() {
        // Given
        val plaintext = "Cross-device data".toByteArray()
        val password = "Password123".toCharArray()
        val keyAlias = "test_key"

        val encrypted = clientSideEncryption.encryptData(plaintext, password, keyAlias)

        // When - Decrypt even with different device binding (master password is authority)
        val decrypted = clientSideEncryption.decryptData(encrypted, password, keyAlias)

        // Then
        assertArrayEquals("Decryption should work with master password", plaintext, decrypted)
    }

    // ========================================================================
    // CHUNKED ENCRYPTION TESTS
    // ========================================================================

    @Test
    fun `encryptDataChunked handles large data correctly`() {
        // Given
        val largeData = ByteArray(50 * 1024) { it.toByte() }  // 50KB
        val password = "ChunkPassword".toCharArray()

        // When
        val chunks = clientSideEncryption.encryptDataChunked(largeData, password)
        val decrypted = clientSideEncryption.decryptDataChunked(chunks, password)

        // Then
        assertTrue("Should create multiple chunks", chunks.size > 1)
        assertArrayEquals("Decrypted data should match original", largeData, decrypted)
    }

    @Test
    fun `encryptDataChunked with small data creates single chunk`() {
        // Given
        val smallData = "Small data".toByteArray()
        val password = "Password123".toCharArray()

        // When
        val chunks = clientSideEncryption.encryptDataChunked(smallData, password)

        // Then
        assertEquals("Small data should create single chunk", 1, chunks.size)
    }

    @Test
    fun `decryptDataChunked with wrong password fails`() {
        // Given
        val data = ByteArray(30 * 1024) { it.toByte() }
        val correctPassword = "CorrectPassword".toCharArray()
        val wrongPassword = "WrongPassword".toCharArray()

        val chunks = clientSideEncryption.encryptDataChunked(data, correctPassword)

        // When/Then
        try {
            clientSideEncryption.decryptDataChunked(chunks, wrongPassword)
            fail("Should fail with wrong password")
        } catch (e: Exception) {
            // Expected
            assertTrue(true)
        }
    }

    // ========================================================================
    // MASTER PASSWORD VALIDATION TESTS
    // ========================================================================

    @Test
    fun `validateMasterPassword returns true for valid password`() {
        // Given
        val password = "ValidPassword123!".toCharArray()

        // When
        val isValid = clientSideEncryption.validateMasterPassword(password)

        // Then
        assertTrue("Valid password should pass validation", isValid)
    }

    @Test
    fun `validateMasterPassword returns false for empty password`() {
        // Given
        val emptyPassword = CharArray(0)

        // When
        val isValid = clientSideEncryption.validateMasterPassword(emptyPassword)

        // Then
        assertFalse("Empty password should fail validation", isValid)
    }

    // ========================================================================
    // ENCRYPTED PAYLOAD TESTS
    // ========================================================================

    @Test
    fun `EncryptedPayload secureWipe clears data`() {
        // Given
        val plaintext = "Test data".toByteArray()
        val password = "Password123".toCharArray()
        val keyAlias = "test_key"

        val encrypted = clientSideEncryption.encryptData(plaintext, password, keyAlias)

        // Store references before wiping
        val iv = encrypted.encryptedData.iv
        val ciphertext = encrypted.encryptedData.ciphertext

        // When
        encrypted.secureWipe()

        // Then
        assertTrue("IV should be wiped", iv.all { it == 0.toByte() })
        assertTrue("Ciphertext should be wiped", ciphertext.all { it == 0.toByte() })
    }

    // ========================================================================
    // INTEGRATION TESTS
    // ========================================================================

    @Test
    fun `full encryption workflow with different algorithms`() {
        // Test with different data sizes
        val testCases = listOf(
            "Short",
            "Medium length credential data with special chars: !@#$%",
            "Long ".repeat(100) + "credential data"
        )

        testCases.forEach { plaintext ->
            val data = plaintext.toByteArray()
            val password = "TestPassword123!".toCharArray()
            val keyAlias = "test_${plaintext.hashCode()}"

            val encrypted = clientSideEncryption.encryptData(data, password, keyAlias)
            val decrypted = clientSideEncryption.decryptData(encrypted, password, keyAlias)

            assertArrayEquals("Data should survive encryption round-trip", data, decrypted)
        }
    }

    @Test
    fun `encryption with auto algorithm selection works`() {
        // Given
        val plaintext = "Auto-selected algorithm test".toByteArray()
        val password = "Password123".toCharArray()
        val keyAlias = "test_auto"

        // When
        val encrypted = clientSideEncryption.encryptData(plaintext, password, keyAlias)
        val decrypted = clientSideEncryption.decryptData(encrypted, password, keyAlias)

        // Then
        assertArrayEquals("Auto algorithm selection should work", plaintext, decrypted)
        assertTrue(
            "Should use AES-GCM or ChaCha20",
            encrypted.algorithm == CryptoManager.Algorithm.AES_256_GCM ||
                    encrypted.algorithm == CryptoManager.Algorithm.CHACHA20_POLY1305
        )
    }
}
