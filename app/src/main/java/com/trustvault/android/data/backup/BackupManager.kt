package com.trustvault.android.data.backup

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.trustvault.android.domain.model.Credential
import com.trustvault.android.domain.repository.CredentialRepository
import com.trustvault.android.security.DatabaseKeyManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages encrypted backup creation, restoration, and lifecycle.
 *
 * Responsibilities:
 * - Create encrypted backups of all credentials
 * - Restore credentials from backup files
 * - Manage backup file lifecycle (storage, cleanup)
 * - Verify backup integrity
 * - Support multiple backup versions
 *
 * Backup Storage:
 * - Backups stored in app's cache directory (removed on uninstall)
 * - Future: Support external storage with proper permissions
 * - Future: Support cloud storage (WebDAV, Google Drive, etc.)
 *
 * Security:
 * - All backups encrypted with master-password-derived key
 * - Integrity verified with HMAC checksum
 * - Device binding metadata included
 * - No plaintext data persists to disk
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val credentialRepository: CredentialRepository,
    private val backupEncryption: BackupEncryption,
    private val databaseKeyManager: DatabaseKeyManager
) {

    companion object {
        private const val TAG = "BackupManager"
        private const val BACKUP_DIR = "backups"
        private const val BACKUP_FILE_EXTENSION = ".tvbackup"
        private const val MAX_BACKUP_VERSIONS = 10
    }

    private val backupDir: File
        get() = File(context.cacheDir, BACKUP_DIR).apply { mkdirs() }

    private val gson = Gson()

    /**
     * Creates an encrypted backup of all credentials.
     *
     * @param masterPassword Master password for backup encryption
     * @return Result with backup file path or error
     */
    suspend fun createBackup(masterPassword: String): Result<File> {
        return try {
            Log.d(TAG, "Starting backup creation...")

            // Get all credentials
            val credentials = credentialRepository.getAllCredentials().first()
            if (credentials.isEmpty()) {
                Log.w(TAG, "No credentials to backup")
                return Result.success(File(""))
            }

            // Create backup file
            val backupFile = File(backupDir, generateBackupFileName())

            // Convert credentials to JSON
            val credentialsJson = gson.toJson(credentials).toByteArray(Charsets.UTF_8)

            // Encrypt credentials
            val encrypted = backupEncryption.encrypt(credentialsJson, masterPassword)

            // Update metadata with actual credential count and size
            val metadata = encrypted.metadata.copy(
                credentialCount = credentials.size,
                backupSizeBytes = encrypted.encryptedCredentials.size.toLong()
            )

            // Write backup file
            writeBackupFile(backupFile, metadata, encrypted)

            Log.d(TAG, "Backup created successfully: ${backupFile.absolutePath}")

            // Clean old backups if exceeded limit
            cleanOldBackups()

            Result.success(backupFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating backup: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Restores credentials from a backup file.
     *
     * @param backupFile Backup file to restore
     * @param masterPassword Master password for decryption
     * @return Result with restored credentials or error
     */
    suspend fun restoreBackup(
        backupFile: File,
        masterPassword: String
    ): Result<BackupRestoreResult> {
        return try {
            Log.d(TAG, "Starting backup restore from: ${backupFile.name}")

            // SECURITY CONTROL: Validate backup file structure before processing
            when (val validationResult = BackupFileValidator.validateBackupFile(backupFile)) {
                is BackupFileValidator.ValidationResult.Valid -> {
                    Log.d(TAG, "Backup file validation passed")
                }
                is BackupFileValidator.ValidationResult.Invalid -> {
                    Log.e(TAG, "Backup file validation failed: ${validationResult.reason}")
                    return Result.failure(
                        IllegalArgumentException(
                            "Invalid backup file: ${validationResult.reason}. ${validationResult.details}"
                        )
                    )
                }
            }

            // Read backup file
            val backupFileData = readBackupFile(backupFile)
                ?: return Result.failure(Exception("Failed to read backup file"))

            // Decrypt credentials
            val decryptedJson = backupEncryption.decrypt(backupFileData, masterPassword)

            // Parse JSON to credentials
            val credentials = gson.fromJson(
                String(decryptedJson, Charsets.UTF_8),
                Array<Credential>::class.java
            ).toList()

            // Clear decrypted data
            decryptedJson.fill(0)

            if (credentials.isEmpty()) {
                return Result.failure(Exception("No credentials found in backup"))
            }

            // Import credentials into vault
            // Note: Existing credentials are not deleted - this merges with current vault
            var importedCount = 0
            for (credential in credentials) {
                try {
                    credentialRepository.insertCredential(credential)
                    importedCount++
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to import credential '${credential.title}': ${e.message}")
                }
            }

            Log.d(TAG, "Backup restored: $importedCount credentials imported")

            val result = BackupRestoreResult(
                success = true,
                credentialsRestored = importedCount,
                message = "Successfully restored $importedCount credentials"
            )

            Result.success(result)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Backup restore failed: ${e.message}")
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring backup: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Lists all available backups.
     *
     * @return List of backup files with metadata (only valid backups)
     */
    fun listBackups(): List<BackupInfo> {
        return try {
            backupDir.listFiles { file ->
                file.isFile && file.name.endsWith(BACKUP_FILE_EXTENSION)
            }?.mapNotNull { file ->
                try {
                    // SECURITY CONTROL: Validate backup file before listing
                    when (val validationResult = BackupFileValidator.validateBackupFile(file)) {
                        is BackupFileValidator.ValidationResult.Valid -> {
                            val metadata = readBackupMetadata(file)
                            if (metadata != null) {
                                BackupInfo(
                                    file = file,
                                    metadata = metadata
                                )
                            } else {
                                Log.w(TAG, "Failed to read metadata from ${file.name}")
                                null
                            }
                        }
                        is BackupFileValidator.ValidationResult.Invalid -> {
                            Log.w(TAG, "Skipping invalid backup ${file.name}: ${validationResult.reason}")
                            null
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to process backup ${file.name}: ${e.message}")
                    null
                }
            }?.sortedByDescending { it.metadata.timestamp } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error listing backups: ${e.message}")
            emptyList()
        }
    }

    /**
     * Deletes a backup file.
     *
     * @param backupFile Backup file to delete
     * @return true if deleted successfully
     */
    fun deleteBackup(backupFile: File): Boolean {
        return try {
            if (backupFile.delete()) {
                Log.d(TAG, "Backup deleted: ${backupFile.name}")
                true
            } else {
                Log.w(TAG, "Failed to delete backup: ${backupFile.name}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting backup: ${e.message}")
            false
        }
    }

    /**
     * Writes backup data to file.
     *
     * Format:
     * - Metadata JSON (with newline separator)
     * - Encrypted data (binary)
     * - IV and Salt (binary)
     */
    private fun writeBackupFile(
        file: File,
        metadata: BackupMetadata,
        encrypted: BackupFile
    ) {
        file.outputStream().use { output ->
            // Write metadata as JSON (for readability and validation)
            val metadataJson = gson.toJson(metadata).toByteArray(Charsets.UTF_8)
            output.write(metadataJson.size)
            output.write(metadataJson)

            // Write IV length and data
            output.write(encrypted.iv.size)
            output.write(encrypted.iv)

            // Write salt length and data
            output.write(encrypted.salt.size)
            output.write(encrypted.salt)

            // Write encrypted credentials
            output.write(encrypted.encryptedCredentials)

            output.flush()
        }
    }

    /**
     * Reads backup file and returns BackupFile object.
     */
    private fun readBackupFile(file: File): BackupFile? {
        return try {
            file.inputStream().use { input ->
                // Read metadata
                val metadataSize = input.read()
                val metadataBytes = ByteArray(metadataSize)
                input.read(metadataBytes)
                val metadata = gson.fromJson(
                    String(metadataBytes, Charsets.UTF_8),
                    BackupMetadata::class.java
                )

                // Read IV
                val ivSize = input.read()
                val iv = ByteArray(ivSize)
                input.read(iv)

                // Read salt
                val saltSize = input.read()
                val salt = ByteArray(saltSize)
                input.read(salt)

                // Read encrypted data
                val encrypted = input.readAllBytes()

                BackupFile(
                    metadata = metadata,
                    encryptedCredentials = encrypted,
                    iv = iv,
                    salt = salt
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading backup file: ${e.message}")
            null
        }
    }

    /**
     * Reads only metadata from a backup file without decrypting.
     */
    private fun readBackupMetadata(file: File): BackupMetadata? {
        return try {
            file.inputStream().use { input ->
                val metadataSize = input.read()
                val metadataBytes = ByteArray(metadataSize)
                input.read(metadataBytes)
                gson.fromJson(
                    String(metadataBytes, Charsets.UTF_8),
                    BackupMetadata::class.java
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading backup metadata: ${e.message}")
            null
        }
    }

    /**
     * Generates unique backup filename with timestamp.
     */
    private fun generateBackupFileName(): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "backup_$timestamp$BACKUP_FILE_EXTENSION"
    }

    /**
     * Removes old backups beyond MAX_BACKUP_VERSIONS.
     */
    private fun cleanOldBackups() {
        try {
            val backups = listBackups()
            if (backups.size > MAX_BACKUP_VERSIONS) {
                val toDelete = backups.drop(MAX_BACKUP_VERSIONS)
                toDelete.forEach { backup ->
                    deleteBackup(backup.file)
                }
                Log.d(TAG, "Cleaned ${toDelete.size} old backups")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error cleaning old backups: ${e.message}")
        }
    }
}

/**
 * Information about a backup file.
 */
data class BackupInfo(
    val file: File,
    val metadata: BackupMetadata
) {
    fun displayName(): String {
        return "Backup - ${metadata.formattedDate()} (${metadata.credentialCount} entries, ${metadata.formattedSize()})"
    }
}
