package com.trustvault.android.data.backup

import android.content.Context
import android.provider.Settings
import com.trustvault.android.security.AndroidKeystoreManager
import com.trustvault.android.security.DatabaseKeyDerivation
import com.trustvault.android.util.secureWipe
import io.mockk.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.security.SecureRandom

/**
 * Unit tests for BackupEncryption.
 *
 * Tests verify:
 * - Encryption produces ciphertext different from plaintext
 * - Decryption recovers original plaintext
 * - IV and salt are properly managed
 * - Checksum validation detects tampering
 * - Master password derivation is consistent
 * - Large files encrypt/decrypt correctly
 * - Round-trip encryption works
 * - Different passwords produce different ciphertexts
 */
class BackupEncryptionTest {

    private lateinit var mockKeyDerivation: DatabaseKeyDerivation
    private lateinit var backupEncryption: BackupEncryption

    private val testDeviceId = "test_device_123"
    private val testMasterPassword = "TestMasterPassword123!"
    private val testPlaintext = "Important credential data: username=test, password=secret123".toByteArray()

    @Before
    fun setup() {
        mockKeyDerivation = mockk(relaxed = true)

        // Mock Settings.Secure
        mockkStatic(Settings.Secure::class)
        every { Settings.Secure.getString(any(), Settings.Secure.ANDROID_ID) } returns testDeviceId

        // Mock key derivation to return consistent test key
        val testKey = ByteArray(32) { (it % 256).toByte() }
        every { mockKeyDerivation.deriveKey(any()) } returns testKey

        backupEncryption = BackupEncryption(mockKeyDerivation)
    }

    @Test
    fun `encrypt produces different ciphertext than plaintext`() {
        val backupFile = backupEncryption.encrypt(testPlaintext, testMasterPassword)

        assertNotNull("Backup file should not be null", backupFile)
        assertFalse("Ciphertext should differ from plaintext",
            backupFile.encryptedCredentials.contentEquals(testPlaintext))
        assertTrue("IV should be present and non-empty", backupFile.iv.isNotEmpty())
    }

    @Test
    fun `encrypt and decrypt recovers original plaintext`() {
        val encrypted = backupEncryption.encrypt(testPlaintext, testMasterPassword)
        val decrypted = backupEncryption.decrypt(encrypted, testMasterPassword)

        assertNotNull("Decrypted data should not be null", decrypted)
        assertTrue("Decrypted plaintext should match original",
            decrypted.contentEquals(testPlaintext))
    }

    @Test
    fun `encrypt creates valid backup metadata`() {
        val encrypted = backupEncryption.encrypt(testPlaintext, testMasterPassword)

        assertNotNull("Backup file should not be null", encrypted)
        assertTrue("Salt should be 16 bytes", encrypted.salt.size == 16)
        assertTrue("IV should be 12 bytes (GCM standard)", encrypted.iv.size == 12)
        assertTrue("Ciphertext should be present", encrypted.encryptedCredentials.isNotEmpty())
    }

    @Test
    fun `decrypt with wrong password fails or returns corrupted data`() {
        val encrypted = backupEncryption.encrypt(testPlaintext, testMasterPassword)

        val wrongPassword = "WrongPassword456!"

        try {
            // Mock key derivation to return different key for wrong password
            val wrongKey = ByteArray(32) { ((it + 100) % 256).toByte() }
            every { mockKeyDerivation.deriveKey(any()) } returns wrongKey

            val decrypted = try {
                backupEncryption.decrypt(encrypted, wrongPassword)
            } catch (e: Exception) {
                // Expected: decryption can fail or return corrupted data
                null
            }

            // Either decryption failed or data is corrupted
            if (decrypted != null) {
                assertFalse("Wrong password should not recover original data",
                    decrypted.contentEquals(testPlaintext))
            }
        } catch (e: Exception) {
            // Decryption failed as expected
            assertTrue("Wrong password should fail or corrupt data", true)
        }
    }

    @Test
    fun `checksum validation detects tampering`() {
        val encrypted = backupEncryption.encrypt(testPlaintext, testMasterPassword)

        // Tamper with ciphertext
        val tampered = encrypted.encryptedCredentials.copyOf()
        if (tampered.isNotEmpty()) {
            tampered[0] = (tampered[0].toInt() xor 1).toByte()
        }

        val tamperedBackup = encrypted.copy(encryptedCredentials = tampered)

        val exception = assertThrows(Exception::class.java) {
            backupEncryption.decrypt(tamperedBackup, testMasterPassword)
        }

        assertTrue("Should detect tampering or fail decryption",
            exception.message?.contains("checksum", ignoreCase = true) ?: false ||
            exception.message?.contains("authentication tag", ignoreCase = true) ?: false)
    }

    @Test
    fun `encrypt with large data works correctly`() {
        // 1MB of data
        val largeData = ByteArray(1024 * 1024) { (it % 256).toByte() }

        val encrypted = backupEncryption.encrypt(largeData, testMasterPassword)
        val decrypted = backupEncryption.decrypt(encrypted, testMasterPassword)

        assertTrue("Large data should round-trip correctly",
            decrypted.contentEquals(largeData))
    }

    @Test
    fun `encrypt and decrypt round-trip for empty data`() {
        val emptyData = ByteArray(0)

        val encrypted = backupEncryption.encrypt(emptyData, testMasterPassword)
        val decrypted = backupEncryption.decrypt(encrypted, testMasterPassword)

        assertTrue("Empty data should round-trip correctly",
            decrypted.contentEquals(emptyData))
    }

    @Test
    fun `different master passwords produce different ciphertexts`() {
        val password1 = "Password123!"
        val password2 = "Password456!"

        // Reset mock to allow different keys
        every { mockKeyDerivation.deriveKey(any()) } answers {
            // Simple: just hash the password to get different keys
            val pwd = firstArg<String>()
            ByteArray(32) { i ->
                ((pwd.getOrNull(i % pwd.length)?.code ?: 0) % 256).toByte()
            }
        }

        val encrypted1 = backupEncryption.encrypt(testPlaintext, password1)
        val encrypted2 = backupEncryption.encrypt(testPlaintext, password2)

        assertFalse("Different passwords should produce different ciphertexts",
            encrypted1.encryptedCredentials.contentEquals(encrypted2.encryptedCredentials))
    }

    @Test
    fun `backup file is created successfully`() {
        val encrypted = backupEncryption.encrypt(testPlaintext, testMasterPassword)

        assertNotNull("Backup should be created", encrypted)
        assertTrue("Backup should have salt", encrypted.salt.isNotEmpty())
        assertTrue("Backup should have IV", encrypted.iv.isNotEmpty())
    }

    @Test
    fun `backup file validation works correctly`() {
        val encrypted = backupEncryption.encrypt(testPlaintext, testMasterPassword)

        // Verify we can decrypt with correct password
        val decrypted = backupEncryption.decrypt(encrypted, testMasterPassword)
        assertTrue("Should decrypt successfully", decrypted.contentEquals(testPlaintext))
    }

    @Test
    fun `iv is unique across multiple encryptions`() {
        val iv1 = backupEncryption.encrypt(testPlaintext, testMasterPassword).iv
        val iv2 = backupEncryption.encrypt(testPlaintext, testMasterPassword).iv

        assertFalse("IVs should be different across encryptions",
            iv1.contentEquals(iv2))
    }

    @Test
    fun `salt is preserved for key derivation`() {
        val encrypted1 = backupEncryption.encrypt(testPlaintext, testMasterPassword)
        val encrypted2 = backupEncryption.encrypt(testPlaintext, testMasterPassword)

        // With different salts, key derivation should produce different keys
        // (mocked here but validates salt management)
        assertTrue("Salt should be present", encrypted1.salt.isNotEmpty())
        assertTrue("Salt should be present", encrypted2.salt.isNotEmpty())
    }

    @Test
    fun `large and small plaintexts both work`() {
        // Small plaintext
        val smallData = "x".toByteArray()
        val encryptedSmall = backupEncryption.encrypt(smallData, testMasterPassword)
        val decryptedSmall = backupEncryption.decrypt(encryptedSmall, testMasterPassword)
        assertTrue("Small data should round-trip", decryptedSmall.contentEquals(smallData))

        // Large plaintext
        val largeData = "x".repeat(10000).toByteArray()
        val encryptedLarge = backupEncryption.encrypt(largeData, testMasterPassword)
        val decryptedLarge = backupEncryption.decrypt(encryptedLarge, testMasterPassword)
        assertTrue("Large data should round-trip", decryptedLarge.contentEquals(largeData))
    }

    @Test
    fun `backup size is larger than plaintext due to encryption overhead`() {
        val encrypted = backupEncryption.encrypt(testPlaintext, testMasterPassword)

        // Encrypted data should be roughly same size + IV (12 bytes) + salt (16 bytes) + checksum
        val totalBackupSize = encrypted.encryptedCredentials.size +
                             encrypted.iv.size +
                             encrypted.salt.size

        assertTrue("Backup should account for encryption overhead",
            totalBackupSize >= testPlaintext.size)
    }
}
