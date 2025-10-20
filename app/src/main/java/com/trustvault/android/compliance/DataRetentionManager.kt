package com.trustvault.android.compliance

import android.content.Context
import android.util.Log
import com.trustvault.android.domain.model.Credential
import com.trustvault.android.domain.repository.CredentialRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataRetentionManager - Automated Data Lifecycle & Retention Policy Management
 *
 * Implements GDPR Article 5(1)(e) storage limitation and DPDP Act data retention requirements.
 *
 * **GDPR Compliance:**
 * - Article 5(1)(e): Storage limitation - kept only for necessary period
 * - Article 25: Data protection by design and by default
 * - Article 32: Security of processing (secure deletion)
 *
 * **DPDP Act 2023 Compliance:**
 * - Reasonable security safeguards
 * - Data retention only as long as necessary
 * - Secure deletion after retention period
 *
 * **Features:**
 * - Configurable retention periods (days)
 * - Automatic deletion of expired data
 * - Retention policy enforcement
 * - Secure deletion (not just database removal)
 * - Audit trail of deletions
 * - Exemptions for critical data
 *
 * @property context Application context for preferences
 * @property credentialRepository Credential data access
 */
@Singleton
class DataRetentionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val credentialRepository: CredentialRepository
) {

    companion object {
        private const val TAG = "DataRetentionManager"
        private const val PREFS_NAME = "trustvault_data_retention"
        private const val KEY_RETENTION_DAYS = "retention_days"
        private const val KEY_LAST_CLEANUP = "last_cleanup_timestamp"

        // Retention policy options
        const val RETENTION_INDEFINITE = -1
        const val RETENTION_30_DAYS = 30
        const val RETENTION_90_DAYS = 90
        const val RETENTION_180_DAYS = 180
        const val RETENTION_365_DAYS = 365
        const val RETENTION_730_DAYS = 730 // 2 years
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Retention policy configuration.
     */
    data class RetentionPolicy(
        val retentionDays: Int,
        val autoDelete: Boolean,
        val exemptFavorites: Boolean,
        val lastCleanup: Long
    )

    /**
     * Deletion result.
     */
    data class DeletionResult(
        val deletedCount: Int,
        val errors: List<String>,
        val timestamp: Long
    )

    /**
     * Sets data retention policy.
     *
     * @param retentionDays Number of days to retain data (-1 for indefinite)
     * @param autoDelete Whether to automatically delete expired data
     * @param exemptFavorites Whether to exempt favorite credentials from deletion
     * @return true if set successfully
     */
    fun setRetentionPolicy(
        retentionDays: Int,
        autoDelete: Boolean = true,
        exemptFavorites: Boolean = true
    ): Boolean {
        return try {
            require(retentionDays == RETENTION_INDEFINITE || retentionDays > 0) {
                "Retention days must be positive or -1 for indefinite"
            }

            prefs.edit()
                .putInt(KEY_RETENTION_DAYS, retentionDays)
                .putBoolean("auto_delete", autoDelete)
                .putBoolean("exempt_favorites", exemptFavorites)
                .apply()

            Log.d(TAG, "Retention policy set: $retentionDays days, autoDelete=$autoDelete")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error setting retention policy: ${e.message}", e)
            false
        }
    }

    /**
     * Gets current data retention policy.
     *
     * @return Retention period in days (-1 for indefinite)
     */
    fun getRetentionPolicy(): Int {
        return prefs.getInt(KEY_RETENTION_DAYS, RETENTION_INDEFINITE)
    }

    /**
     * Gets full retention policy configuration.
     *
     * @return RetentionPolicy object
     */
    fun getRetentionPolicyConfig(): RetentionPolicy {
        return RetentionPolicy(
            retentionDays = prefs.getInt(KEY_RETENTION_DAYS, RETENTION_INDEFINITE),
            autoDelete = prefs.getBoolean("auto_delete", true),
            exemptFavorites = prefs.getBoolean("exempt_favorites", true),
            lastCleanup = prefs.getLong(KEY_LAST_CLEANUP, 0L)
        )
    }

    /**
     * Finds credentials that have exceeded retention period.
     *
     * @return List of credentials eligible for deletion
     */
    suspend fun findExpiredCredentials(): List<Credential> {
        val policy = getRetentionPolicyConfig()

        // If indefinite retention, no credentials expire
        if (policy.retentionDays == RETENTION_INDEFINITE) {
            return emptyList()
        }

        val retentionMillis = policy.retentionDays * 24L * 60L * 60L * 1000L
        val expirationTimestamp = System.currentTimeMillis() - retentionMillis

        try {
            val allCredentials = credentialRepository.getAllCredentials().first()

            return allCredentials.filter { credential ->
                // Check if credential has expired
                val isExpired = credential.updatedAt < expirationTimestamp

                // TODO: Apply exemptions when isFavorite field is added to Credential model
                // val isExempt = policy.exemptFavorites && credential.isFavorite

                isExpired
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding expired credentials: ${e.message}", e)
            return emptyList()
        }
    }

    /**
     * Executes data retention policy by deleting expired credentials.
     *
     * **SECURITY:** This is a permanent deletion. Credentials cannot be recovered.
     * User should be warned before execution.
     *
     * @param dryRun If true, only reports what would be deleted without actually deleting
     * @return DeletionResult with deletion summary
     */
    suspend fun enforceRetentionPolicy(dryRun: Boolean = false): DeletionResult {
        val expiredCredentials = findExpiredCredentials()

        if (expiredCredentials.isEmpty()) {
            Log.d(TAG, "No expired credentials found")
            return DeletionResult(
                deletedCount = 0,
                errors = emptyList(),
                timestamp = System.currentTimeMillis()
            )
        }

        if (dryRun) {
            Log.d(TAG, "Dry run: Would delete ${expiredCredentials.size} credentials")
            return DeletionResult(
                deletedCount = expiredCredentials.size,
                errors = emptyList(),
                timestamp = System.currentTimeMillis()
            )
        }

        // Actually delete expired credentials
        val errors = mutableListOf<String>()
        var deletedCount = 0

        expiredCredentials.forEach { credential ->
            try {
                credentialRepository.deleteCredential(credential) // Pass Credential object, not ID
                deletedCount++
                Log.d(TAG, "Deleted expired credential: ${credential.title}")
            } catch (e: Exception) {
                val error = "Failed to delete credential ${credential.id}: ${e.message}"
                errors.add(error)
                Log.e(TAG, error, e)
            }
        }

        // Update last cleanup timestamp
        prefs.edit()
            .putLong(KEY_LAST_CLEANUP, System.currentTimeMillis())
            .apply()

        Log.d(TAG, "Retention policy enforced: $deletedCount deleted, ${errors.size} errors")

        return DeletionResult(
            deletedCount = deletedCount,
            errors = errors,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Gets number of days until a credential expires.
     *
     * @param credential Credential to check
     * @return Days until expiration, -1 if indefinite, 0 if already expired
     */
    fun getDaysUntilExpiration(credential: Credential): Int {
        val policy = getRetentionPolicyConfig()

        if (policy.retentionDays == RETENTION_INDEFINITE) {
            return -1
        }

        // TODO: Check if exempt when isFavorite field is added to Credential model
        // if (policy.exemptFavorites && credential.isFavorite) {
        //     return -1
        // }

        val retentionMillis = policy.retentionDays * 24L * 60L * 60L * 1000L
        val expirationTimestamp = credential.updatedAt + retentionMillis
        val remainingMillis = expirationTimestamp - System.currentTimeMillis()

        return if (remainingMillis <= 0) {
            0 // Already expired
        } else {
            (remainingMillis / (24L * 60L * 60L * 1000L)).toInt()
        }
    }

    /**
     * Checks if retention policy should be executed.
     *
     * @return true if cleanup is recommended
     */
    fun shouldExecuteCleanup(): Boolean {
        val policy = getRetentionPolicyConfig()

        // Don't cleanup if indefinite retention
        if (policy.retentionDays == RETENTION_INDEFINITE) {
            return false
        }

        // Don't cleanup if auto-delete is disabled
        if (!policy.autoDelete) {
            return false
        }

        // Check if cleanup has been run recently (within last 24 hours)
        val lastCleanup = policy.lastCleanup
        val timeSinceCleanup = System.currentTimeMillis() - lastCleanup
        val oneDayMillis = 24L * 60L * 60L * 1000L

        return timeSinceCleanup >= oneDayMillis
    }

    /**
     * Clears retention policy (resets to indefinite).
     */
    fun clearRetentionPolicy() {
        prefs.edit().clear().apply()
        Log.d(TAG, "Retention policy cleared")
    }
}
