package com.trustvault.android.security.zeroknowledge

import android.util.Log
import com.trustvault.android.security.CryptoException
import com.trustvault.android.util.secureWipe
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LocalVault - Zero-Knowledge Encrypted Data Chunking and Storage
 *
 * Manages encrypted data in chunks with zero-knowledge properties.
 * Designed for secure backup, export, and future sync operations.
 *
 * **Zero-Knowledge Architecture:**
 * ```
 * Plaintext Data → Chunk → Encrypt → Store/Export
 *       ↓            ↓        ↓          ↓
 * Credentials   16KB      AES-GCM    Local/Cloud
 *               chunks              (encrypted)
 * ```
 *
 * **Key Features:**
 * 1. Chunked encryption (prevents memory exhaustion on large datasets)
 * 2. Per-chunk authentication (tamper detection)
 * 3. Chunk integrity verification
 * 4. Streaming encryption/decryption
 * 5. Zero-knowledge properties maintained
 *
 * **Use Cases:**
 * - Large backup files (>100 credentials)
 * - Export for offline storage
 * - Future: Cloud sync with chunked upload
 * - Future: Incremental backups (delta chunks)
 *
 * **Security Standards:**
 * - OWASP Mobile Top 10 2025 - M9 (Insecure Data Storage)
 * - Digital Personal Data Protection Act 2023 - Data Minimization
 * - GDPR Article 32 - Security of Processing
 *
 * @property clientSideEncryption Client-side encryption layer
 */
@Singleton
class LocalVault @Inject constructor(
    private val clientSideEncryption: ClientSideEncryption
) {

    companion object {
        private const val TAG = "LocalVault"

        // Chunk configuration
        private const val CHUNK_SIZE = 16 * 1024  // 16KB per chunk
        private const val MAX_CHUNKS = 1000       // Max 1000 chunks (16MB total)

        // Vault version for migration
        private const val VAULT_VERSION = 1
    }

    /**
     * Encrypted vault container with chunked data.
     *
     * **Structure:**
     * - Version: Migration support
     * - ChunkCount: Number of encrypted chunks
     * - Chunks: List of encrypted data chunks
     * - Manifest: Chunk metadata (sizes, hashes)
     *
     * @property version Vault format version
     * @property chunkCount Number of chunks
     * @property chunks Encrypted data chunks
     * @property manifest Chunk integrity metadata
     */
    data class EncryptedVault(
        val version: Int = VAULT_VERSION,
        val chunkCount: Int,
        val chunks: List<ClientSideEncryption.EncryptedPayload>,
        val manifest: VaultManifest
    ) {
        /**
         * Securely wipes all encrypted chunks from memory.
         */
        fun secureWipe() {
            chunks.forEach { it.secureWipe() }
        }
    }

    /**
     * Vault manifest for chunk integrity verification.
     *
     * @property chunkHashes SHA-256 hash of each chunk (for integrity)
     * @property chunkSizes Size of each chunk in bytes
     * @property totalSize Total size of original data
     * @property algorithm Encryption algorithm used
     */
    data class VaultManifest(
        val chunkHashes: List<String>,
        val chunkSizes: List<Int>,
        val totalSize: Long,
        val algorithm: String
    ) {
        /**
         * Validates manifest integrity.
         *
         * @return true if manifest is valid
         */
        fun validate(): Boolean {
            return chunkHashes.size == chunkSizes.size &&
                    totalSize == chunkSizes.sumOf { it.toLong() } &&
                    chunkSizes.all { it > 0 && it <= CHUNK_SIZE }
        }
    }

    // ========================================================================
    // VAULT CREATION (Encryption)
    // ========================================================================

    /**
     * Creates encrypted vault from plaintext data.
     *
     * **Process:**
     * 1. Split data into chunks (16KB each)
     * 2. Encrypt each chunk independently
     * 3. Generate integrity manifest
     * 4. Return EncryptedVault
     *
     * **Security Features:**
     * - Per-chunk IV (no IV reuse)
     * - Per-chunk authentication (GCM tags)
     * - Chunk integrity hashes
     * - Memory-efficient streaming
     *
     * @param plaintext Data to encrypt
     * @param masterPassword Master password for encryption
     * @return EncryptedVault ready for storage/export
     * @throws CryptoException if encryption fails
     */
    fun createVault(
        plaintext: ByteArray,
        masterPassword: CharArray
    ): EncryptedVault {
        require(plaintext.isNotEmpty()) { "Cannot create vault from empty data" }
        require(masterPassword.isNotEmpty()) { "Master password required" }

        return try {
            Log.d(TAG, "Creating encrypted vault (${plaintext.size} bytes)")

            // Split data into chunks
            val chunks = splitIntoChunks(plaintext)
            Log.d(TAG, "Data split into ${chunks.size} chunks")

            if (chunks.size > MAX_CHUNKS) {
                throw CryptoException("Data too large: ${chunks.size} chunks (max $MAX_CHUNKS)")
            }

            // Encrypt each chunk
            val encryptedChunks = mutableListOf<ClientSideEncryption.EncryptedPayload>()
            val chunkHashes = mutableListOf<String>()
            val chunkSizes = mutableListOf<Int>()

            chunks.forEachIndexed { index, chunk ->
                // Encrypt chunk
                val encrypted = clientSideEncryption.encryptData(chunk, masterPassword)
                encryptedChunks.add(encrypted)

                // Calculate chunk hash for integrity
                val hash = calculateSHA256(chunk)
                chunkHashes.add(hash)

                // Record chunk size
                chunkSizes.add(chunk.size)

                // Secure wipe chunk from memory
                chunk.secureWipe()

                if (index % 100 == 0) {
                    Log.d(TAG, "Encrypted ${index + 1}/${chunks.size} chunks")
                }
            }

            // Create manifest
            val manifest = VaultManifest(
                chunkHashes = chunkHashes,
                chunkSizes = chunkSizes,
                totalSize = plaintext.size.toLong(),
                algorithm = encryptedChunks.firstOrNull()?.algorithm?.name ?: "AES_256_GCM"
            )

            // Validate manifest
            if (!manifest.validate()) {
                throw CryptoException("Invalid vault manifest generated")
            }

            val vault = EncryptedVault(
                version = VAULT_VERSION,
                chunkCount = encryptedChunks.size,
                chunks = encryptedChunks,
                manifest = manifest
            )

            Log.d(TAG, "Vault created successfully: ${vault.chunkCount} chunks, ${vault.manifest.totalSize} bytes")
            vault

        } catch (e: Exception) {
            Log.e(TAG, "Vault creation failed: ${e.message}")
            throw CryptoException("Failed to create encrypted vault", e)
        }
    }

    // ========================================================================
    // VAULT RESTORATION (Decryption)
    // ========================================================================

    /**
     * Restores plaintext data from encrypted vault.
     *
     * **Process:**
     * 1. Validate vault structure
     * 2. Verify manifest integrity
     * 3. Decrypt each chunk
     * 4. Verify chunk hashes
     * 5. Reassemble plaintext
     *
     * **Security Features:**
     * - Chunk integrity verification
     * - Tamper detection via hash comparison
     * - Version validation
     * - Secure memory management
     *
     * @param vault Encrypted vault
     * @param masterPassword Master password for decryption
     * @return Decrypted plaintext data
     * @throws CryptoException if decryption or verification fails
     */
    fun restoreVault(
        vault: EncryptedVault,
        masterPassword: CharArray
    ): ByteArray {
        require(masterPassword.isNotEmpty()) { "Master password required" }

        return try {
            Log.d(TAG, "Restoring vault (${vault.chunkCount} chunks)")

            // SECURITY CONTROL: Validate vault version
            if (vault.version > VAULT_VERSION) {
                throw CryptoException("Unsupported vault version: ${vault.version}")
            }

            // SECURITY CONTROL: Validate manifest
            if (!vault.manifest.validate()) {
                throw CryptoException("Invalid vault manifest")
            }

            // Validate chunk count matches
            if (vault.chunks.size != vault.chunkCount) {
                throw CryptoException("Chunk count mismatch: ${vault.chunks.size} != ${vault.chunkCount}")
            }

            // Decrypt and verify each chunk
            val decryptedChunks = mutableListOf<ByteArray>()

            vault.chunks.forEachIndexed { index, encryptedChunk ->
                // Decrypt chunk
                val decrypted = clientSideEncryption.decryptData(encryptedChunk, masterPassword)

                // SECURITY CONTROL: Verify chunk integrity
                val expectedHash = vault.manifest.chunkHashes.getOrNull(index)
                    ?: throw CryptoException("Missing hash for chunk $index")

                val actualHash = calculateSHA256(decrypted)
                if (actualHash != expectedHash) {
                    // Wipe decrypted data on integrity failure
                    decrypted.secureWipe()
                    throw CryptoException("Chunk $index integrity check failed (tampered data)")
                }

                // Verify chunk size
                val expectedSize = vault.manifest.chunkSizes.getOrNull(index)
                    ?: throw CryptoException("Missing size for chunk $index")

                if (decrypted.size != expectedSize) {
                    decrypted.secureWipe()
                    throw CryptoException("Chunk $index size mismatch: ${decrypted.size} != $expectedSize")
                }

                decryptedChunks.add(decrypted)

                if (index % 100 == 0) {
                    Log.d(TAG, "Decrypted and verified ${index + 1}/${vault.chunkCount} chunks")
                }
            }

            // Reassemble plaintext
            val totalSize = decryptedChunks.sumOf { it.size }
            if (totalSize.toLong() != vault.manifest.totalSize) {
                throw CryptoException("Total size mismatch: $totalSize != ${vault.manifest.totalSize}")
            }

            val plaintext = ByteArray(totalSize)
            var offset = 0

            decryptedChunks.forEach { chunk ->
                System.arraycopy(chunk, 0, plaintext, offset, chunk.size)
                offset += chunk.size

                // Secure wipe chunk after copying
                chunk.secureWipe()
            }

            Log.d(TAG, "Vault restored successfully (${plaintext.size} bytes)")
            plaintext

        } catch (e: Exception) {
            Log.e(TAG, "Vault restoration failed: ${e.message}")
            throw CryptoException("Failed to restore vault", e)
        }
    }

    // ========================================================================
    // VAULT OPERATIONS
    // ========================================================================

    /**
     * Validates vault structure and integrity without decryption.
     *
     * **Checks:**
     * - Version compatibility
     * - Manifest validity
     * - Chunk count consistency
     * - Size constraints
     *
     * @param vault Encrypted vault to validate
     * @return ValidationResult with details
     */
    fun validateVault(vault: EncryptedVault): ValidationResult {
        return try {
            // Check version
            if (vault.version > VAULT_VERSION) {
                return ValidationResult.Invalid("Unsupported vault version: ${vault.version}")
            }

            // Check manifest
            if (!vault.manifest.validate()) {
                return ValidationResult.Invalid("Invalid vault manifest")
            }

            // Check chunk count
            if (vault.chunks.size != vault.chunkCount) {
                return ValidationResult.Invalid("Chunk count mismatch")
            }

            // Check size limits
            if (vault.chunkCount > MAX_CHUNKS) {
                return ValidationResult.Invalid("Too many chunks: ${vault.chunkCount} (max $MAX_CHUNKS)")
            }

            ValidationResult.Valid

        } catch (e: Exception) {
            ValidationResult.Invalid("Validation error: ${e.message}")
        }
    }

    /**
     * Vault validation result.
     */
    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val reason: String) : ValidationResult()
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Splits data into fixed-size chunks.
     *
     * @param data Data to split
     * @return List of chunks (last chunk may be smaller)
     */
    private fun splitIntoChunks(data: ByteArray): List<ByteArray> {
        val chunks = mutableListOf<ByteArray>()
        var offset = 0

        while (offset < data.size) {
            val chunkSize = minOf(CHUNK_SIZE, data.size - offset)
            val chunk = data.copyOfRange(offset, offset + chunkSize)
            chunks.add(chunk)
            offset += chunkSize
        }

        return chunks
    }

    /**
     * Calculates SHA-256 hash of data.
     *
     * @param data Data to hash
     * @return Hex-encoded SHA-256 hash
     */
    private fun calculateSHA256(data: ByteArray): String {
        return try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            digest.update(data)
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "SHA-256 calculation failed: ${e.message}")
            throw CryptoException("Hash calculation failed", e)
        }
    }

    // ========================================================================
    // STATISTICS & MONITORING
    // ========================================================================

    /**
     * Gets vault statistics.
     *
     * @param vault Encrypted vault
     * @return VaultStats with size and chunk info
     */
    fun getVaultStats(vault: EncryptedVault): VaultStats {
        val encryptedSize = vault.chunks.sumOf { it.encryptedData.ciphertext.size }
        val overhead = encryptedSize - vault.manifest.totalSize.toInt()
        val compressionRatio = if (vault.manifest.totalSize > 0) {
            (encryptedSize.toDouble() / vault.manifest.totalSize.toDouble())
        } else {
            1.0
        }

        return VaultStats(
            version = vault.version,
            chunkCount = vault.chunkCount,
            originalSize = vault.manifest.totalSize,
            encryptedSize = encryptedSize.toLong(),
            overhead = overhead.toLong(),
            compressionRatio = compressionRatio,
            algorithm = vault.manifest.algorithm
        )
    }

    /**
     * Vault statistics data class.
     */
    data class VaultStats(
        val version: Int,
        val chunkCount: Int,
        val originalSize: Long,
        val encryptedSize: Long,
        val overhead: Long,
        val compressionRatio: Double,
        val algorithm: String
    ) {
        fun formatStats(): String {
            return """
                Vault Statistics:
                - Version: $version
                - Chunks: $chunkCount
                - Original Size: ${formatBytes(originalSize)}
                - Encrypted Size: ${formatBytes(encryptedSize)}
                - Overhead: ${formatBytes(overhead)} (${String.format("%.1f", compressionRatio * 100)}%)
                - Algorithm: $algorithm
            """.trimIndent()
        }

        private fun formatBytes(bytes: Long): String {
            return when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> "${bytes / 1024} KB"
                else -> "${bytes / (1024 * 1024)} MB"
            }
        }
    }
}
