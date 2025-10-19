package com.trustvault.android.data.backup

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Unit tests for BackupFileValidator.
 *
 * Tests validation of backup file structure and metadata to ensure:
 * - Malformed files are rejected
 * - Valid files are accepted
 * - Size constraints are enforced
 * - Metadata validation works correctly
 */
class BackupFileValidatorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val gson = Gson()

    // Helper function to create valid test metadata
    private fun createValidMetadata(): BackupMetadata {
        return BackupMetadata(
            version = 1,
            timestamp = 1700000000000L, // Fixed timestamp to keep JSON size consistent
            deviceId = "test-dev",
            credentialCount = 5,
            encryptionAlgorithm = "AES-256-GCM",
            keyDerivationAlgorithm = "PBKDF2-HMAC-SHA256",
            keyDerivationIterations = 600000,
            appVersion = "1.0",
            androidApiLevel = 31,
            backupType = BackupMetadata.BackupType.MANUAL,
            backupSizeBytes = 1024,
            notes = ""
        )
    }

    // Helper function to create a valid backup file
    private fun createValidBackupFile(): File {
        val file = tempFolder.newFile("valid_backup.tvbackup")

        val metadata = createValidMetadata()
        val metadataJson = gson.toJson(metadata).toByteArray(Charsets.UTF_8)
        val iv = ByteArray(12) { it.toByte() } // 12 bytes IV
        val salt = ByteArray(32) { it.toByte() } // 32 bytes salt
        val encryptedData = ByteArray(256) { it.toByte() } // 256 bytes encrypted data

        file.outputStream().use { output ->
            // Write metadata size and data
            output.write(metadataJson.size)
            output.write(metadataJson)

            // Write IV size and data
            output.write(iv.size)
            output.write(iv)

            // Write salt size and data
            output.write(salt.size)
            output.write(salt)

            // Write encrypted data
            output.write(encryptedData)
        }

        return file
    }

    @Test
    fun `validateBackupFile accepts valid file`() {
        // Given
        val validFile = createValidBackupFile()

        // When
        val result = BackupFileValidator.validateBackupFile(validFile)

        // Then
        when (result) {
            is BackupFileValidator.ValidationResult.Valid -> {
                // Success
                assertTrue("Valid file should pass validation", true)
            }
            is BackupFileValidator.ValidationResult.Invalid -> {
                fail("Valid file was rejected: ${result.reason}. Details: ${result.details}")
            }
        }
    }

    @Test
    fun `validateBackupFile rejects non-existent file`() {
        // Given
        val nonExistentFile = File("non_existent_file.tvbackup")

        // When
        val result = BackupFileValidator.validateBackupFile(nonExistentFile)

        // Then
        assertTrue("Non-existent file should fail validation", result is BackupFileValidator.ValidationResult.Invalid)
        if (result is BackupFileValidator.ValidationResult.Invalid) {
            assertEquals("File does not exist", result.reason)
        }
    }

    @Test
    fun `validateBackupFile rejects directory`() {
        // Given
        val directory = tempFolder.newFolder("test_dir")

        // When
        val result = BackupFileValidator.validateBackupFile(directory)

        // Then
        assertTrue("Directory should fail validation", result is BackupFileValidator.ValidationResult.Invalid)
        if (result is BackupFileValidator.ValidationResult.Invalid) {
            assertEquals("Path is not a file", result.reason)
        }
    }

    @Test
    fun `validateBackupFile rejects file too small`() {
        // Given
        val tinyFile = tempFolder.newFile("tiny.tvbackup")
        tinyFile.writeBytes(ByteArray(50)) // Less than MIN_FILE_SIZE (100 bytes)

        // When
        val result = BackupFileValidator.validateBackupFile(tinyFile)

        // Then
        assertTrue("Tiny file should fail validation", result is BackupFileValidator.ValidationResult.Invalid)
        if (result is BackupFileValidator.ValidationResult.Invalid) {
            assertEquals("File too small to be valid backup", result.reason)
        }
    }

    @Test
    fun `validateBackupFile rejects metadata size too large`() {
        // Given
        val file = tempFolder.newFile("large_metadata.tvbackup")
        file.outputStream().use { output ->
            // Write metadata size as 255 (max single byte, but > MAX_METADATA_SIZE)
            output.write(255)
            // Write dummy data to make file size valid
            output.write(ByteArray(300))
        }

        // When
        val result = BackupFileValidator.validateBackupFile(file)

        // Then
        assertTrue("File with large metadata should fail", result is BackupFileValidator.ValidationResult.Invalid)
        if (result is BackupFileValidator.ValidationResult.Invalid) {
            assertEquals("Metadata size exceeds limit", result.reason)
        }
    }

    @Test
    fun `validateBackupFile rejects metadata size too small`() {
        // Given
        val file = tempFolder.newFile("small_metadata.tvbackup")
        file.outputStream().use { output ->
            // Write metadata size as 20 (less than MIN_METADATA_SIZE of 40)
            output.write(20)
            // Write dummy data
            output.write(ByteArray(300))
        }

        // When
        val result = BackupFileValidator.validateBackupFile(file)

        // Then
        assertTrue("File with small metadata should fail", result is BackupFileValidator.ValidationResult.Invalid)
        if (result is BackupFileValidator.ValidationResult.Invalid) {
            assertEquals("Metadata size too small", result.reason)
        }
    }

    @Test
    fun `validateBackupFile rejects invalid IV size`() {
        // Given
        val file = tempFolder.newFile("invalid_iv.tvbackup")
        val metadata = createValidMetadata()
        val metadataJson = gson.toJson(metadata).toByteArray(Charsets.UTF_8)

        file.outputStream().use { output ->
            // Write valid metadata
            output.write(metadataJson.size)
            output.write(metadataJson)

            // Write incorrect IV size (16 instead of 12)
            output.write(16)
            output.write(ByteArray(16))

            // Write rest of data
            output.write(32) // salt size
            output.write(ByteArray(32))
            output.write(ByteArray(100)) // encrypted data
        }

        // When
        val result = BackupFileValidator.validateBackupFile(file)

        // Then
        assertTrue("File with invalid IV size should fail", result is BackupFileValidator.ValidationResult.Invalid)
        if (result is BackupFileValidator.ValidationResult.Invalid) {
            assertEquals("Invalid IV size", result.reason)
        }
    }

    @Test
    fun `validateBackupFile rejects invalid salt size`() {
        // Given
        val file = tempFolder.newFile("invalid_salt.tvbackup")
        val metadata = createValidMetadata()
        val metadataJson = gson.toJson(metadata).toByteArray(Charsets.UTF_8)

        file.outputStream().use { output ->
            // Write valid metadata
            output.write(metadataJson.size)
            output.write(metadataJson)

            // Write valid IV
            output.write(12)
            output.write(ByteArray(12))

            // Write incorrect salt size (16 instead of 32)
            output.write(16)
            output.write(ByteArray(16))

            // Write encrypted data
            output.write(ByteArray(100))
        }

        // When
        val result = BackupFileValidator.validateBackupFile(file)

        // Then
        assertTrue("File with invalid salt size should fail", result is BackupFileValidator.ValidationResult.Invalid)
        if (result is BackupFileValidator.ValidationResult.Invalid) {
            assertEquals("Invalid salt size", result.reason)
        }
    }

    @Test
    fun `validateBackupFile rejects encrypted data too small`() {
        // Given
        val file = tempFolder.newFile("small_encrypted.tvbackup")
        val metadata = createValidMetadata()
        val metadataJson = gson.toJson(metadata).toByteArray(Charsets.UTF_8)

        file.outputStream().use { output ->
            // Write valid metadata
            output.write(metadataJson.size)
            output.write(metadataJson)

            // Write valid IV
            output.write(12)
            output.write(ByteArray(12))

            // Write valid salt
            output.write(32)
            output.write(ByteArray(32))

            // Write tiny encrypted data (less than MIN_ENCRYPTED_DATA_SIZE)
            output.write(ByteArray(10))
        }

        // When
        val result = BackupFileValidator.validateBackupFile(file)

        // Then
        assertTrue("File with small encrypted data should fail", result is BackupFileValidator.ValidationResult.Invalid)
        if (result is BackupFileValidator.ValidationResult.Invalid) {
            assertEquals("Encrypted data too small", result.reason)
        }
    }

    @Test
    fun `validateBackupFile rejects unsupported version`() {
        // Given
        val file = tempFolder.newFile("unsupported_version.tvbackup")
        val metadata = createValidMetadata().copy(version = 99)
        val metadataJson = gson.toJson(metadata).toByteArray(Charsets.UTF_8)

        file.outputStream().use { output ->
            // Write metadata with unsupported version
            output.write(metadataJson.size)
            output.write(metadataJson)

            // Write valid IV, salt, and encrypted data
            output.write(12)
            output.write(ByteArray(12))
            output.write(32)
            output.write(ByteArray(32))
            output.write(ByteArray(100))
        }

        // When
        val result = BackupFileValidator.validateBackupFile(file)

        // Then
        assertTrue("File with unsupported version should fail", result is BackupFileValidator.ValidationResult.Invalid)
        if (result is BackupFileValidator.ValidationResult.Invalid) {
            assertEquals("Unsupported backup version", result.reason)
        }
    }

    @Test
    fun `validateBackupFile rejects negative credential count`() {
        // Given
        val file = tempFolder.newFile("negative_count.tvbackup")
        val metadata = createValidMetadata().copy(credentialCount = -1)
        val metadataJson = gson.toJson(metadata).toByteArray(Charsets.UTF_8)

        file.outputStream().use { output ->
            output.write(metadataJson.size)
            output.write(metadataJson)
            output.write(12)
            output.write(ByteArray(12))
            output.write(32)
            output.write(ByteArray(32))
            output.write(ByteArray(100))
        }

        // When
        val result = BackupFileValidator.validateBackupFile(file)

        // Then
        assertTrue("File with negative credential count should fail", result is BackupFileValidator.ValidationResult.Invalid)
        if (result is BackupFileValidator.ValidationResult.Invalid) {
            assertEquals("Invalid credential count", result.reason)
        }
    }

    @Test
    fun `validateBackupFile rejects excessive credential count`() {
        // Given
        val file = tempFolder.newFile("excessive_count.tvbackup")
        val metadata = createValidMetadata().copy(credentialCount = 200000)
        val metadataJson = gson.toJson(metadata).toByteArray(Charsets.UTF_8)

        file.outputStream().use { output ->
            output.write(metadataJson.size)
            output.write(metadataJson)
            output.write(12)
            output.write(ByteArray(12))
            output.write(32)
            output.write(ByteArray(32))
            output.write(ByteArray(100))
        }

        // When
        val result = BackupFileValidator.validateBackupFile(file)

        // Then
        assertTrue("File with excessive credential count should fail", result is BackupFileValidator.ValidationResult.Invalid)
        if (result is BackupFileValidator.ValidationResult.Invalid) {
            assertEquals("Credential count exceeds limit", result.reason)
        }
    }

    @Test
    fun `validateBackupFile rejects insufficient KDF iterations`() {
        // Given
        val file = tempFolder.newFile("weak_kdf.tvbackup")
        val metadata = createValidMetadata().copy(keyDerivationIterations = 50000)
        val metadataJson = gson.toJson(metadata).toByteArray(Charsets.UTF_8)

        file.outputStream().use { output ->
            output.write(metadataJson.size)
            output.write(metadataJson)
            output.write(12)
            output.write(ByteArray(12))
            output.write(32)
            output.write(ByteArray(32))
            output.write(ByteArray(100))
        }

        // When
        val result = BackupFileValidator.validateBackupFile(file)

        // Then
        assertTrue("File with weak KDF iterations should fail", result is BackupFileValidator.ValidationResult.Invalid)
        if (result is BackupFileValidator.ValidationResult.Invalid) {
            assertEquals("KDF iterations below minimum", result.reason)
        }
    }

    @Test
    fun `validateBackupFile rejects excessive KDF iterations`() {
        // Given
        val file = tempFolder.newFile("excessive_kdf.tvbackup")
        val metadata = createValidMetadata().copy(keyDerivationIterations = 20000000)
        val metadataJson = gson.toJson(metadata).toByteArray(Charsets.UTF_8)

        file.outputStream().use { output ->
            output.write(metadataJson.size)
            output.write(metadataJson)
            output.write(12)
            output.write(ByteArray(12))
            output.write(32)
            output.write(ByteArray(32))
            output.write(ByteArray(100))
        }

        // When
        val result = BackupFileValidator.validateBackupFile(file)

        // Then
        assertTrue("File with excessive KDF iterations should fail", result is BackupFileValidator.ValidationResult.Invalid)
        if (result is BackupFileValidator.ValidationResult.Invalid) {
            assertEquals("KDF iterations exceed maximum", result.reason)
        }
    }

    @Test
    fun `validateBackupFile rejects blank device ID`() {
        // Given
        val file = tempFolder.newFile("blank_device.tvbackup")
        val metadata = createValidMetadata().copy(deviceId = "")
        val metadataJson = gson.toJson(metadata).toByteArray(Charsets.UTF_8)

        file.outputStream().use { output ->
            output.write(metadataJson.size)
            output.write(metadataJson)
            output.write(12)
            output.write(ByteArray(12))
            output.write(32)
            output.write(ByteArray(32))
            output.write(ByteArray(100))
        }

        // When
        val result = BackupFileValidator.validateBackupFile(file)

        // Then
        assertTrue("File with blank device ID should fail", result is BackupFileValidator.ValidationResult.Invalid)
        if (result is BackupFileValidator.ValidationResult.Invalid) {
            assertEquals("Invalid device ID", result.reason)
        }
    }

    @Test
    fun `validateBackupFile rejects unsupported encryption algorithm`() {
        // Given
        val file = tempFolder.newFile("wrong_encryption.tvbackup")
        val metadata = createValidMetadata().copy(encryptionAlgorithm = "AES-128-CBC")
        val metadataJson = gson.toJson(metadata).toByteArray(Charsets.UTF_8)

        file.outputStream().use { output ->
            output.write(metadataJson.size)
            output.write(metadataJson)
            output.write(12)
            output.write(ByteArray(12))
            output.write(32)
            output.write(ByteArray(32))
            output.write(ByteArray(100))
        }

        // When
        val result = BackupFileValidator.validateBackupFile(file)

        // Then
        assertTrue("File with wrong encryption algorithm should fail", result is BackupFileValidator.ValidationResult.Invalid)
        if (result is BackupFileValidator.ValidationResult.Invalid) {
            assertEquals("Unsupported encryption algorithm", result.reason)
        }
    }

    @Test
    fun `validateBackupFile rejects unsupported KDF algorithm`() {
        // Given
        val file = tempFolder.newFile("wrong_kdf.tvbackup")
        val metadata = createValidMetadata().copy(keyDerivationAlgorithm = "PBKDF2-SHA1")
        val metadataJson = gson.toJson(metadata).toByteArray(Charsets.UTF_8)

        file.outputStream().use { output ->
            output.write(metadataJson.size)
            output.write(metadataJson)
            output.write(12)
            output.write(ByteArray(12))
            output.write(32)
            output.write(ByteArray(32))
            output.write(ByteArray(100))
        }

        // When
        val result = BackupFileValidator.validateBackupFile(file)

        // Then
        assertTrue("File with wrong KDF algorithm should fail", result is BackupFileValidator.ValidationResult.Invalid)
        if (result is BackupFileValidator.ValidationResult.Invalid) {
            assertEquals("Unsupported key derivation algorithm", result.reason)
        }
    }

    @Test
    fun `validateBackupFile rejects invalid JSON in metadata`() {
        // Given
        val file = tempFolder.newFile("invalid_json.tvbackup")

        file.outputStream().use { output ->
            // Write valid size but invalid JSON
            val invalidJson = "{invalid json}".toByteArray(Charsets.UTF_8)
            output.write(invalidJson.size)
            output.write(invalidJson)
            // Pad file to valid size
            output.write(ByteArray(300))
        }

        // When
        val result = BackupFileValidator.validateBackupFile(file)

        // Then
        assertTrue("File with invalid JSON should fail", result is BackupFileValidator.ValidationResult.Invalid)
        if (result is BackupFileValidator.ValidationResult.Invalid) {
            assertEquals("Invalid JSON in metadata", result.reason)
        }
    }

    @Test
    fun `validateMetadata accepts valid metadata object`() {
        // Given
        val validMetadata = createValidMetadata()

        // When
        val result = BackupFileValidator.validateMetadata(validMetadata)

        // Then
        assertTrue("Valid metadata should pass validation", result is BackupFileValidator.ValidationResult.Valid)
    }

    @Test
    fun `validateMetadata rejects invalid metadata object`() {
        // Given
        val invalidMetadata = createValidMetadata().copy(
            version = 0,  // Invalid version
            deviceId = "" // Invalid device ID
        )

        // When
        val result = BackupFileValidator.validateMetadata(invalidMetadata)

        // Then
        assertTrue("Invalid metadata should fail validation", result is BackupFileValidator.ValidationResult.Invalid)
        if (result is BackupFileValidator.ValidationResult.Invalid) {
            assertEquals("Metadata validation failed", result.reason)
        }
    }

    @Test
    fun `validateBackupFile handles truncated metadata`() {
        // Given
        val file = tempFolder.newFile("truncated_metadata.tvbackup")

        file.outputStream().use { output ->
            val metadata = createValidMetadata()
            val metadataJson = gson.toJson(metadata).toByteArray(Charsets.UTF_8)

            // Write metadata size but only half the data
            output.write(metadataJson.size)
            output.write(metadataJson.copyOf(metadataJson.size / 2))
        }

        // When
        val result = BackupFileValidator.validateBackupFile(file)

        // Then
        assertTrue("File with truncated metadata should fail", result is BackupFileValidator.ValidationResult.Invalid)
        if (result is BackupFileValidator.ValidationResult.Invalid) {
            assertEquals("Incomplete metadata", result.reason)
        }
    }

    @Test
    fun `validateBackupFile handles truncated IV`() {
        // Given
        val file = tempFolder.newFile("truncated_iv.tvbackup")
        val metadata = createValidMetadata()
        val metadataJson = gson.toJson(metadata).toByteArray(Charsets.UTF_8)

        file.outputStream().use { output ->
            // Write valid metadata
            output.write(metadataJson.size)
            output.write(metadataJson)

            // Write IV size but only half the IV data
            output.write(12)
            output.write(ByteArray(6)) // Only 6 bytes instead of 12
        }

        // When
        val result = BackupFileValidator.validateBackupFile(file)

        // Then
        assertTrue("File with truncated IV should fail", result is BackupFileValidator.ValidationResult.Invalid)
        if (result is BackupFileValidator.ValidationResult.Invalid) {
            assertEquals("Incomplete IV data", result.reason)
        }
    }

    @Test
    fun `validateBackupFile handles truncated salt`() {
        // Given
        val file = tempFolder.newFile("truncated_salt.tvbackup")
        val metadata = createValidMetadata()
        val metadataJson = gson.toJson(metadata).toByteArray(Charsets.UTF_8)

        file.outputStream().use { output ->
            // Write valid metadata
            output.write(metadataJson.size)
            output.write(metadataJson)

            // Write valid IV
            output.write(12)
            output.write(ByteArray(12))

            // Write salt size but only half the salt data
            output.write(32)
            output.write(ByteArray(16)) // Only 16 bytes instead of 32
        }

        // When
        val result = BackupFileValidator.validateBackupFile(file)

        // Then
        assertTrue("File with truncated salt should fail", result is BackupFileValidator.ValidationResult.Invalid)
        if (result is BackupFileValidator.ValidationResult.Invalid) {
            assertEquals("Incomplete salt data", result.reason)
        }
    }
}