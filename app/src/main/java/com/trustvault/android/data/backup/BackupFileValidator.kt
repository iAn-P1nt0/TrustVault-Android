package com.trustvault.android.data.backup

import android.util.Log
import java.io.File
import java.io.InputStream

/**
 * Validates backup file structure and integrity before processing.
 *
 * Prevents crashes from malformed backup files by validating:
 * - File existence and readability
 * - File size constraints (min/max)
 * - Metadata structure and content
 * - Binary data lengths (IV, salt, encrypted data)
 * - Version compatibility
 * - Field value ranges
 *
 * Security:
 * - Prevents DoS via extremely large files
 * - Prevents crashes from malformed data
 * - Validates all size fields before allocation
 * - Enforces reasonable limits on all components
 *
 * OWASP References:
 * - A04:2021 Insecure Design - Input validation
 * - A05:2021 Security Misconfiguration - Safe defaults
 */
object BackupFileValidator {

    private const val TAG = "BackupFileValidator"

    // File size constraints
    private const val MIN_FILE_SIZE = 100L // Minimum viable backup (metadata + minimal encrypted data)
    private const val MAX_FILE_SIZE = 50L * 1024 * 1024 // 50 MB maximum (prevents DoS)

    // Component size constraints
    private const val MIN_METADATA_SIZE = 40 // Minimum JSON metadata
    private const val MAX_METADATA_SIZE = 255 // 255 bytes max (single byte size field)
    private const val EXPECTED_IV_SIZE = 12 // AES-GCM standard IV (96 bits)
    private const val EXPECTED_SALT_SIZE = 32 // 256-bit salt
    private const val MIN_ENCRYPTED_DATA_SIZE = 50 // Minimum encrypted credential data
    private const val MAX_ENCRYPTED_DATA_SIZE = 45L * 1024 * 1024 // 45 MB encrypted data max

    // Metadata field constraints
    private const val MIN_VERSION = 1
    private const val MAX_VERSION = 10 // Allow some future versions
    private const val MIN_CREDENTIAL_COUNT = 0
    private const val MAX_CREDENTIAL_COUNT = 100000 // Reasonable upper limit
    private const val MIN_KDF_ITERATIONS = 100000 // OWASP 2023 minimum
    private const val MAX_KDF_ITERATIONS = 10000000 // Reasonable upper limit
    private const val MIN_TIMESTAMP = 1577836800000L // 2020-01-01 (sanity check)

    /**
     * Validation result with detailed error information.
     */
    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val reason: String, val details: String = "") : ValidationResult()
    }

    /**
     * Validates a backup file before attempting to read it.
     *
     * @param file Backup file to validate
     * @return ValidationResult indicating success or specific failure reason
     */
    fun validateBackupFile(file: File): ValidationResult {
        // Check file existence
        if (!file.exists()) {
            return ValidationResult.Invalid("File does not exist", file.absolutePath)
        }

        // Check file is actually a file (not directory)
        if (!file.isFile) {
            return ValidationResult.Invalid("Path is not a file", file.absolutePath)
        }

        // Check file is readable
        if (!file.canRead()) {
            return ValidationResult.Invalid("File is not readable", file.absolutePath)
        }

        // Check file size constraints
        val fileSize = file.length()
        if (fileSize < MIN_FILE_SIZE) {
            return ValidationResult.Invalid(
                "File too small to be valid backup",
                "Size: $fileSize bytes, minimum: $MIN_FILE_SIZE bytes"
            )
        }

        if (fileSize > MAX_FILE_SIZE) {
            return ValidationResult.Invalid(
                "File exceeds maximum size limit",
                "Size: $fileSize bytes, maximum: $MAX_FILE_SIZE bytes"
            )
        }

        // Validate file structure
        return validateFileStructure(file)
    }

    /**
     * Validates the internal structure of a backup file.
     */
    private fun validateFileStructure(file: File): ValidationResult {
        return try {
            file.inputStream().use { input ->
                validateInputStream(input)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error validating file structure: ${e.message}")
            ValidationResult.Invalid("Error reading file structure", e.message ?: "Unknown error")
        }
    }

    /**
     * Validates backup file structure from input stream.
     */
    private fun validateInputStream(input: InputStream): ValidationResult {
        // Read and validate metadata size
        val metadataSize = input.read()
        if (metadataSize == -1) {
            return ValidationResult.Invalid("Unexpected end of file", "Cannot read metadata size")
        }

        if (metadataSize < MIN_METADATA_SIZE) {
            return ValidationResult.Invalid(
                "Metadata size too small",
                "Size: $metadataSize bytes, minimum: $MIN_METADATA_SIZE bytes"
            )
        }

        if (metadataSize > MAX_METADATA_SIZE) {
            return ValidationResult.Invalid(
                "Metadata size exceeds limit",
                "Size: $metadataSize bytes, maximum: $MAX_METADATA_SIZE bytes"
            )
        }

        // Read metadata bytes
        val metadataBytes = ByteArray(metadataSize)
        val metadataBytesRead = input.read(metadataBytes)
        if (metadataBytesRead != metadataSize) {
            return ValidationResult.Invalid(
                "Incomplete metadata",
                "Expected $metadataSize bytes, read $metadataBytesRead bytes"
            )
        }

        // Validate metadata content
        val metadataValidation = validateMetadataContent(metadataBytes)
        if (metadataValidation !is ValidationResult.Valid) {
            return metadataValidation
        }

        // Read and validate IV size
        val ivSize = input.read()
        if (ivSize == -1) {
            return ValidationResult.Invalid("Unexpected end of file", "Cannot read IV size")
        }

        if (ivSize != EXPECTED_IV_SIZE) {
            return ValidationResult.Invalid(
                "Invalid IV size",
                "Expected $EXPECTED_IV_SIZE bytes, got $ivSize bytes"
            )
        }

        // Read IV bytes
        val ivBytes = ByteArray(ivSize)
        val ivBytesRead = input.read(ivBytes)
        if (ivBytesRead != ivSize) {
            return ValidationResult.Invalid(
                "Incomplete IV data",
                "Expected $ivSize bytes, read $ivBytesRead bytes"
            )
        }

        // Read and validate salt size
        val saltSize = input.read()
        if (saltSize == -1) {
            return ValidationResult.Invalid("Unexpected end of file", "Cannot read salt size")
        }

        if (saltSize != EXPECTED_SALT_SIZE) {
            return ValidationResult.Invalid(
                "Invalid salt size",
                "Expected $EXPECTED_SALT_SIZE bytes, got $saltSize bytes"
            )
        }

        // Read salt bytes
        val saltBytes = ByteArray(saltSize)
        val saltBytesRead = input.read(saltBytes)
        if (saltBytesRead != saltSize) {
            return ValidationResult.Invalid(
                "Incomplete salt data",
                "Expected $saltSize bytes, read $saltBytesRead bytes"
            )
        }

        // Read remaining encrypted data and validate size
        val encryptedBytes = input.readAllBytes()
        if (encryptedBytes.size < MIN_ENCRYPTED_DATA_SIZE) {
            return ValidationResult.Invalid(
                "Encrypted data too small",
                "Size: ${encryptedBytes.size} bytes, minimum: $MIN_ENCRYPTED_DATA_SIZE bytes"
            )
        }

        if (encryptedBytes.size > MAX_ENCRYPTED_DATA_SIZE) {
            return ValidationResult.Invalid(
                "Encrypted data exceeds limit",
                "Size: ${encryptedBytes.size} bytes, maximum: $MAX_ENCRYPTED_DATA_SIZE bytes"
            )
        }

        return ValidationResult.Valid
    }

    /**
     * Validates metadata JSON content.
     */
    private fun validateMetadataContent(metadataBytes: ByteArray): ValidationResult {
        return try {
            val metadataJson = String(metadataBytes, Charsets.UTF_8)

            // Parse JSON to verify structure
            val metadata = com.google.gson.Gson().fromJson(
                metadataJson,
                BackupMetadata::class.java
            )

            // Validate version
            if (metadata.version < MIN_VERSION || metadata.version > MAX_VERSION) {
                return ValidationResult.Invalid(
                    "Unsupported backup version",
                    "Version ${metadata.version} is not supported (supported: $MIN_VERSION-$MAX_VERSION)"
                )
            }

            // Validate credential count
            if (metadata.credentialCount < MIN_CREDENTIAL_COUNT) {
                return ValidationResult.Invalid(
                    "Invalid credential count",
                    "Count cannot be negative: ${metadata.credentialCount}"
                )
            }

            if (metadata.credentialCount > MAX_CREDENTIAL_COUNT) {
                return ValidationResult.Invalid(
                    "Credential count exceeds limit",
                    "Count: ${metadata.credentialCount}, maximum: $MAX_CREDENTIAL_COUNT"
                )
            }

            // Validate KDF iterations
            if (metadata.keyDerivationIterations < MIN_KDF_ITERATIONS) {
                return ValidationResult.Invalid(
                    "KDF iterations below minimum",
                    "Iterations: ${metadata.keyDerivationIterations}, minimum: $MIN_KDF_ITERATIONS"
                )
            }

            if (metadata.keyDerivationIterations > MAX_KDF_ITERATIONS) {
                return ValidationResult.Invalid(
                    "KDF iterations exceed maximum",
                    "Iterations: ${metadata.keyDerivationIterations}, maximum: $MAX_KDF_ITERATIONS"
                )
            }

            // Validate timestamp (sanity check)
            if (metadata.timestamp < MIN_TIMESTAMP) {
                return ValidationResult.Invalid(
                    "Invalid timestamp",
                    "Timestamp ${metadata.timestamp} is too old (before 2020)"
                )
            }

            val now = System.currentTimeMillis()
            if (metadata.timestamp > now + 86400000) { // Allow 1 day in future for clock skew
                return ValidationResult.Invalid(
                    "Invalid timestamp",
                    "Timestamp ${metadata.timestamp} is in the future"
                )
            }

            // Validate device ID
            if (metadata.deviceId.isBlank()) {
                return ValidationResult.Invalid(
                    "Invalid device ID",
                    "Device ID cannot be blank"
                )
            }

            // Validate encryption algorithm
            if (metadata.encryptionAlgorithm != "AES-256-GCM") {
                return ValidationResult.Invalid(
                    "Unsupported encryption algorithm",
                    "Algorithm '${metadata.encryptionAlgorithm}' is not supported"
                )
            }

            // Validate key derivation algorithm
            if (metadata.keyDerivationAlgorithm != "PBKDF2-HMAC-SHA256") {
                return ValidationResult.Invalid(
                    "Unsupported key derivation algorithm",
                    "Algorithm '${metadata.keyDerivationAlgorithm}' is not supported"
                )
            }

            // Validate backup size if specified
            if (metadata.backupSizeBytes < 0) {
                return ValidationResult.Invalid(
                    "Invalid backup size",
                    "Size cannot be negative: ${metadata.backupSizeBytes}"
                )
            }

            ValidationResult.Valid
        } catch (e: com.google.gson.JsonSyntaxException) {
            ValidationResult.Invalid("Invalid JSON in metadata", e.message ?: "JSON parse error")
        } catch (e: Exception) {
            ValidationResult.Invalid("Error parsing metadata", e.message ?: "Unknown error")
        }
    }

    /**
     * Validates backup metadata object after parsing.
     * Additional validation beyond what's in the metadata itself.
     */
    fun validateMetadata(metadata: BackupMetadata): ValidationResult {
        // Use the built-in validate() method
        if (!metadata.validate()) {
            return ValidationResult.Invalid(
                "Metadata validation failed",
                "Version: ${metadata.version}, Credentials: ${metadata.credentialCount}, " +
                "Iterations: ${metadata.keyDerivationIterations}, DeviceID: ${metadata.deviceId}"
            )
        }

        return ValidationResult.Valid
    }
}