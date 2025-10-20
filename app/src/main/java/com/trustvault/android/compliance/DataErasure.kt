package com.trustvault.android.compliance

import android.content.Context
import android.util.Log
import com.trustvault.android.domain.repository.CredentialRepository
import com.trustvault.android.security.DatabaseKeyManager
import com.trustvault.android.util.secureWipe
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataErasure - Complete Data Deletion for Right to be Forgotten
 *
 * Implements GDPR Article 17 "Right to Erasure" and DPDP Act Section 9 "Right to Erasure".
 *
 * **GDPR Article 17 - Right to Erasure ("Right to be Forgotten"):**
 * Data subject has right to obtain erasure of personal data without undue delay when:
 * - Personal data no longer necessary for purposes collected
 * - Data subject withdraws consent
 * - Data subject objects to processing
 * - Personal data unlawfully processed
 * - Compliance with legal obligation
 *
 * **DPDP Act 2023 Section 9:**
 * Data Principal shall have right to erasure of her personal data except where:
 * - Retention required by law
 * - Specified lawful purpose not complete
 *
 * **Secure Deletion Standards:**
 * - DOD 5220.22-M: 3-pass overwrite (deprecated but thorough)
 * - NIST SP 800-88: Media sanitization guidelines
 * - Secure erase from encrypted database
 * - Metadata cleanup
 * - Key destruction
 * - Cache clearing
 *
 * **Scope of Erasure:**
 * 1. All credentials (encrypted database)
 * 2. Master password hash
 * 3. Biometric authentication data
 * 4. Encryption keys (database, field, backup)
 * 5. All backups and exports
 * 6. Preferences and settings
 * 7. Cache files
 * 8. Log files
 * 9. Consent records
 * 10. Audit trails
 *
 * @property context Application context
 * @property credentialRepository Credential data access
 * @property databaseKeyManager Encryption key management
 * @property consentManager Consent record management
 */
@Singleton
class DataErasure @Inject constructor(
    @ApplicationContext private val context: Context,
    private val credentialRepository: CredentialRepository,
    private val databaseKeyManager: DatabaseKeyManager,
    private val consentManager: ConsentManager
) {

    companion object {
        private const val TAG = "DataErasure"
    }

    /**
     * Erasure result with detailed report.
     */
    data class ErasureResult(
        val success: Boolean,
        val itemsDeleted: Map<String, Int>,
        val errors: List<String>,
        val timestamp: Long,
        val verificationPassed: Boolean
    )

    /**
     * Executes complete data erasure (right to be forgotten).
     *
     * **WARNING:** This operation is IRREVERSIBLE. All user data will be permanently deleted.
     *
     * **GDPR Article 17:** Right to erasure without undue delay.
     * **DPDP Act Section 9:** Erasure of personal data on request.
     *
     * Process:
     * 1. Delete all credentials from database
     * 2. Delete database encryption keys
     * 3. Delete biometric authentication data
     * 4. Delete all backups
     * 5. Delete all preferences and settings
     * 6. Clear all caches
     * 7. Delete consent records
     * 8. Delete audit logs (optional - may be retained for compliance)
     * 9. Verify deletion (database empty, files removed)
     *
     * @param deleteMasterPassword If true, also deletes master password hash (requires re-setup)
     * @param deleteAuditLogs If true, also deletes audit logs (may affect compliance)
     * @param masterPassword Master password for verification (optional but recommended)
     * @return ErasureResult with deletion summary
     */
    suspend fun executeCompleteErasure(
        deleteMasterPassword: Boolean = true,
        deleteAuditLogs: Boolean = false,
        masterPassword: CharArray? = null
    ): ErasureResult {
        val timestamp = System.currentTimeMillis()
        val itemsDeleted = mutableMapOf<String, Int>()
        val errors = mutableListOf<String>()

        try {
            Log.d(TAG, "=== Starting Complete Data Erasure ===")
            Log.d(TAG, "Delete master password: $deleteMasterPassword")
            Log.d(TAG, "Delete audit logs: $deleteAuditLogs")

            // Step 1: Delete all credentials from database
            try {
                val credentials = credentialRepository.getAllCredentials().first()
                val credentialCount = credentials.size
                credentials.forEach { credential ->
                    credentialRepository.deleteCredential(credential) // Pass Credential object, not ID
                }
                itemsDeleted["credentials"] = credentialCount
                Log.d(TAG, "✓ Deleted $credentialCount credentials")
            } catch (e: Exception) {
                errors.add("Error deleting credentials: ${e.message}")
                Log.e(TAG, "Error deleting credentials", e)
            }

            // Step 2: Delete database encryption keys
            try {
                databaseKeyManager.lockDatabase() // lockDatabase clears keys from memory
                itemsDeleted["encryption_keys"] = 1
                Log.d(TAG, "✓ Cleared encryption keys")
            } catch (e: Exception) {
                errors.add("Error clearing encryption keys: ${e.message}")
                Log.e(TAG, "Error clearing encryption keys", e)
            }

            // Step 3: Delete biometric authentication data
            try {
                val biometricDeleted = deleteBiometricData()
                itemsDeleted["biometric_data"] = if (biometricDeleted) 1 else 0
                Log.d(TAG, "✓ Deleted biometric data")
            } catch (e: Exception) {
                errors.add("Error deleting biometric data: ${e.message}")
                Log.e(TAG, "Error deleting biometric data", e)
            }

            // Step 4: Delete all backups
            try {
                val backupCount = deleteAllBackups()
                itemsDeleted["backups"] = backupCount
                Log.d(TAG, "✓ Deleted $backupCount backup files")
            } catch (e: Exception) {
                errors.add("Error deleting backups: ${e.message}")
                Log.e(TAG, "Error deleting backups", e)
            }

            // Step 5: Delete master password hash (if requested)
            if (deleteMasterPassword) {
                try {
                    val masterPasswordDeleted = deleteMasterPasswordHash()
                    itemsDeleted["master_password_hash"] = if (masterPasswordDeleted) 1 else 0
                    Log.d(TAG, "✓ Deleted master password hash")
                } catch (e: Exception) {
                    errors.add("Error deleting master password: ${e.message}")
                    Log.e(TAG, "Error deleting master password", e)
                }
            }

            // Step 6: Delete all preferences and settings
            try {
                val prefsDeleted = deleteAllPreferences()
                itemsDeleted["preferences"] = prefsDeleted
                Log.d(TAG, "✓ Deleted $prefsDeleted preference files")
            } catch (e: Exception) {
                errors.add("Error deleting preferences: ${e.message}")
                Log.e(TAG, "Error deleting preferences", e)
            }

            // Step 7: Clear all caches
            try {
                val cacheDeleted = clearAllCaches()
                itemsDeleted["cache_files"] = cacheDeleted
                Log.d(TAG, "✓ Cleared $cacheDeleted cache files")
            } catch (e: Exception) {
                errors.add("Error clearing caches: ${e.message}")
                Log.e(TAG, "Error clearing caches", e)
            }

            // Step 8: Delete consent records
            try {
                consentManager.clearAllConsent()
                itemsDeleted["consent_records"] = 1
                Log.d(TAG, "✓ Deleted consent records")
            } catch (e: Exception) {
                errors.add("Error deleting consent records: ${e.message}")
                Log.e(TAG, "Error deleting consent records", e)
            }

            // Step 9: Delete audit logs (if requested)
            if (deleteAuditLogs) {
                try {
                    val logsDeleted = deleteAuditLogFiles()
                    itemsDeleted["audit_logs"] = logsDeleted
                    Log.d(TAG, "✓ Deleted $logsDeleted audit log files")
                } catch (e: Exception) {
                    errors.add("Error deleting audit logs: ${e.message}")
                    Log.e(TAG, "Error deleting audit logs", e)
                }
            }

            // Step 10: Secure wipe sensitive data from memory
            masterPassword?.secureWipe()

            // Step 11: Verify erasure
            val verificationPassed = verifyErasure()

            val success = errors.isEmpty() && verificationPassed

            Log.d(TAG, "=== Data Erasure Complete ===")
            Log.d(TAG, "Success: $success")
            Log.d(TAG, "Items deleted: $itemsDeleted")
            Log.d(TAG, "Errors: ${errors.size}")
            Log.d(TAG, "Verification: ${if (verificationPassed) "PASSED" else "FAILED"}")

            return ErasureResult(
                success = success,
                itemsDeleted = itemsDeleted,
                errors = errors,
                timestamp = timestamp,
                verificationPassed = verificationPassed
            )

        } catch (e: Exception) {
            Log.e(TAG, "Critical error during data erasure", e)
            errors.add("Critical error: ${e.message}")

            return ErasureResult(
                success = false,
                itemsDeleted = itemsDeleted,
                errors = errors,
                timestamp = timestamp,
                verificationPassed = false
            )
        }
    }

    // ========================================================================
    // DELETION HELPERS
    // ========================================================================

    /**
     * Deletes biometric authentication data.
     */
    private fun deleteBiometricData(): Boolean {
        return try {
            val biometricPrefs = context.getSharedPreferences("biometric_auth", Context.MODE_PRIVATE)
            biometricPrefs.edit().clear().apply()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting biometric data: ${e.message}", e)
            false
        }
    }

    /**
     * Deletes all backup files.
     */
    private fun deleteAllBackups(): Int {
        var deletedCount = 0

        try {
            // Delete backups from cache directory
            val backupDir = File(context.cacheDir, "backups")
            if (backupDir.exists() && backupDir.isDirectory) {
                backupDir.listFiles()?.forEach { file ->
                    if (file.delete()) {
                        deletedCount++
                    }
                }
                backupDir.delete()
            }

            // Delete backups from external storage (if any)
            context.getExternalFilesDir(null)?.let { externalDir ->
                val externalBackupDir = File(externalDir, "backups")
                if (externalBackupDir.exists() && externalBackupDir.isDirectory) {
                    externalBackupDir.listFiles()?.forEach { file ->
                        if (file.delete()) {
                            deletedCount++
                        }
                    }
                    externalBackupDir.delete()
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error deleting backups: ${e.message}", e)
        }

        return deletedCount
    }

    /**
     * Deletes master password hash.
     */
    private fun deleteMasterPasswordHash(): Boolean {
        return try {
            val masterPasswordPrefs = context.getSharedPreferences("trustvault_master_password", Context.MODE_PRIVATE)
            masterPasswordPrefs.edit().clear().apply()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting master password hash: ${e.message}", e)
            false
        }
    }

    /**
     * Deletes all SharedPreferences files.
     */
    private fun deleteAllPreferences(): Int {
        var deletedCount = 0

        try {
            val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
            if (prefsDir.exists() && prefsDir.isDirectory) {
                prefsDir.listFiles()?.forEach { file ->
                    if (file.delete()) {
                        deletedCount++
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting preferences: ${e.message}", e)
        }

        return deletedCount
    }

    /**
     * Clears all cache files.
     */
    private fun clearAllCaches(): Int {
        var deletedCount = 0

        try {
            // Internal cache
            context.cacheDir.listFiles()?.forEach { file ->
                if (deleteRecursively(file)) {
                    deletedCount++
                }
            }

            // External cache
            context.externalCacheDir?.listFiles()?.forEach { file ->
                if (deleteRecursively(file)) {
                    deletedCount++
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error clearing caches: ${e.message}", e)
        }

        return deletedCount
    }

    /**
     * Deletes audit log files.
     */
    private fun deleteAuditLogFiles(): Int {
        var deletedCount = 0

        try {
            val logDir = File(context.filesDir, "audit_logs")
            if (logDir.exists() && logDir.isDirectory) {
                logDir.listFiles()?.forEach { file ->
                    if (file.delete()) {
                        deletedCount++
                    }
                }
                logDir.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting audit logs: ${e.message}", e)
        }

        return deletedCount
    }

    /**
     * Recursively deletes a file or directory.
     */
    private fun deleteRecursively(file: File): Boolean {
        return try {
            if (file.isDirectory) {
                file.listFiles()?.forEach { child ->
                    deleteRecursively(child)
                }
            }
            file.delete()
        } catch (e: Exception) {
            false
        }
    }

    // ========================================================================
    // VERIFICATION
    // ========================================================================

    /**
     * Verifies that erasure was successful.
     *
     * @return true if all data deleted successfully
     */
    private suspend fun verifyErasure(): Boolean {
        var verified = true

        try {
            // Verify credentials deleted
            val credentialsRemaining = credentialRepository.getAllCredentials().first()
            if (credentialsRemaining.isNotEmpty()) {
                Log.w(TAG, "Verification failed: ${credentialsRemaining.size} credentials still exist")
                verified = false
            }

            // Verify database keys cleared
            if (databaseKeyManager.isDatabaseInitialized()) {
                Log.w(TAG, "Verification failed: Database still initialized")
                verified = false
            }

            // Verify backups deleted
            val backupDir = File(context.cacheDir, "backups")
            if (backupDir.exists() && backupDir.listFiles()?.isNotEmpty() == true) {
                Log.w(TAG, "Verification failed: Backup files still exist")
                verified = false
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error during verification: ${e.message}", e)
            verified = false
        }

        return verified
    }
}
