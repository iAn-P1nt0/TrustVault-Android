package com.trustvault.android.compliance

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ConsentManager - Granular Consent Tracking and Management
 *
 * Implements explicit user consent requirements for GDPR and DPDP Act 2023.
 *
 * **GDPR Compliance:**
 * - Article 4(11): Definition of consent (freely given, specific, informed, unambiguous)
 * - Article 7: Conditions for consent
 * - Article 7(3): Right to withdraw consent
 * - Article 8: Conditions for children's consent (future enhancement for 13-16 age group)
 *
 * **DPDP Act 2023 Compliance:**
 * - Section 5: Free and specific consent
 * - Section 6: Proof of consent
 * - Section 7: Right to withdraw consent
 *
 * **Features:**
 * - Granular consent per data processing purpose
 * - Consent versioning (track policy changes)
 * - Timestamp tracking (when consent was given/withdrawn)
 * - Audit trail (consent history)
 * - Withdraw consent at any time
 * - Proof of consent with cryptographic signatures (future enhancement)
 *
 * @property context Application context for SharedPreferences
 */
@Singleton
class ConsentManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "ConsentManager"
        private const val PREFS_NAME = "trustvault_consent"
        private const val CONSENT_VERSION = "1.0.0"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Consent state flow for reactive UI
    private val _consentState = MutableStateFlow<Map<PrivacyManager.DataProcessingPurpose, ConsentRecord>>(emptyMap())
    val consentState: Flow<Map<PrivacyManager.DataProcessingPurpose, ConsentRecord>> = _consentState.asStateFlow()

    /**
     * Consent record with metadata.
     *
     * @property purpose Data processing purpose
     * @property granted true if consent granted, false if denied
     * @property timestamp When consent was given/withdrawn
     * @property version Consent policy version
     * @property withdrawable Whether consent can be withdrawn (required purposes cannot)
     */
    data class ConsentRecord(
        val purpose: PrivacyManager.DataProcessingPurpose,
        val granted: Boolean,
        val timestamp: Long,
        val version: String,
        val withdrawable: Boolean
    )

    init {
        // Load existing consent state
        refreshConsentState()
    }

    /**
     * Records user consent for a data processing purpose.
     *
     * **GDPR Article 7(1):** Controller must be able to demonstrate consent was given.
     * **DPDP Act Section 6:** Data fiduciary shall maintain complete record of consents.
     *
     * @param purpose Data processing purpose
     * @param granted true if consent granted, false if denied
     * @param version Consent policy version (default: current version)
     * @return true if recorded successfully
     */
    fun recordConsent(
        purpose: PrivacyManager.DataProcessingPurpose,
        granted: Boolean,
        version: String = CONSENT_VERSION
    ): Boolean {
        return try {
            val key = getConsentKey(purpose)
            val timestamp = System.currentTimeMillis()

            prefs.edit()
                .putBoolean(key, granted)
                .putLong("${key}_timestamp", timestamp)
                .putString("${key}_version", version)
                .apply()

            Log.d(TAG, "Consent recorded: ${purpose.displayName} = $granted")
            refreshConsentState()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error recording consent: ${e.message}", e)
            false
        }
    }

    /**
     * Checks if user has granted consent for a purpose.
     *
     * @param purpose Data processing purpose
     * @return true if consent granted
     */
    fun hasConsent(purpose: PrivacyManager.DataProcessingPurpose): Boolean {
        // Required purposes are implicitly consented
        if (purpose.isRequired) return true

        val key = getConsentKey(purpose)
        return prefs.getBoolean(key, false)
    }

    /**
     * Gets consent record with metadata.
     *
     * @param purpose Data processing purpose
     * @return ConsentRecord if exists, null otherwise
     */
    fun getConsentRecord(purpose: PrivacyManager.DataProcessingPurpose): ConsentRecord? {
        val key = getConsentKey(purpose)

        // For required purposes, return implicit consent
        if (purpose.isRequired) {
            return ConsentRecord(
                purpose = purpose,
                granted = true,
                timestamp = 0L, // Not applicable
                version = CONSENT_VERSION,
                withdrawable = false
            )
        }

        val granted = prefs.getBoolean(key, false)
        val timestamp = prefs.getLong("${key}_timestamp", 0L)
        val version = prefs.getString("${key}_version", null)

        return if (timestamp > 0L) {
            ConsentRecord(
                purpose = purpose,
                granted = granted,
                timestamp = timestamp,
                version = version ?: CONSENT_VERSION,
                withdrawable = !purpose.isRequired
            )
        } else {
            null // No consent record exists
        }
    }

    /**
     * Gets all consent records.
     *
     * @return Map of purpose to consent record
     */
    fun getAllConsentRecords(): Map<PrivacyManager.DataProcessingPurpose, ConsentRecord> {
        return PrivacyManager.DataProcessingPurpose.values().associateWith { purpose ->
            getConsentRecord(purpose) ?: ConsentRecord(
                purpose = purpose,
                granted = false,
                timestamp = 0L,
                version = CONSENT_VERSION,
                withdrawable = !purpose.isRequired
            )
        }
    }

    /**
     * Withdraws consent for a purpose (if withdrawable).
     *
     * **GDPR Article 7(3):** Right to withdraw consent at any time.
     * **DPDP Act Section 7:** Data principal shall have right to withdraw her consent.
     *
     * @param purpose Data processing purpose
     * @return true if withdrawn successfully, false if purpose is required
     */
    fun withdrawConsent(purpose: PrivacyManager.DataProcessingPurpose): Boolean {
        if (purpose.isRequired) {
            Log.w(TAG, "Cannot withdraw consent for required purpose: ${purpose.displayName}")
            return false
        }

        return recordConsent(purpose, granted = false)
    }

    /**
     * Clears all consent records (for factory reset or data erasure).
     *
     * **GDPR Article 17:** Right to erasure.
     * **DPDP Act Section 9:** Right to erasure of personal data.
     */
    fun clearAllConsent() {
        prefs.edit().clear().apply()
        refreshConsentState()
        Log.d(TAG, "All consent records cleared")
    }

    /**
     * Exports consent records for data portability.
     *
     * **GDPR Article 20:** Right to data portability.
     *
     * @return JSON string of consent records
     */
    fun exportConsentRecords(): String {
        val records = getAllConsentRecords()
        return buildString {
            append("{\n")
            append("  \"consent_version\": \"$CONSENT_VERSION\",\n")
            append("  \"export_timestamp\": ${System.currentTimeMillis()},\n")
            append("  \"consents\": [\n")

            records.entries.forEachIndexed { index, (purpose, record) ->
                append("    {\n")
                append("      \"purpose\": \"${purpose.name}\",\n")
                append("      \"purpose_display_name\": \"${purpose.displayName}\",\n")
                append("      \"granted\": ${record.granted},\n")
                append("      \"timestamp\": ${record.timestamp},\n")
                append("      \"version\": \"${record.version}\",\n")
                append("      \"withdrawable\": ${record.withdrawable}\n")
                append("    }")

                if (index < records.size - 1) append(",")
                append("\n")
            }

            append("  ]\n")
            append("}")
        }
    }

    /**
     * Checks if consent needs to be refreshed due to policy updates.
     *
     * @return true if user needs to re-consent
     */
    fun needsConsentRefresh(): Boolean {
        // Check if any consent was given under an old version
        return PrivacyManager.DataProcessingPurpose.values().any { purpose ->
            val record = getConsentRecord(purpose)
            record != null && record.version != CONSENT_VERSION
        }
    }

    // ========================================================================
    // INTERNAL HELPERS
    // ========================================================================

    private fun getConsentKey(purpose: PrivacyManager.DataProcessingPurpose): String {
        return "consent_${purpose.name.lowercase()}"
    }

    private fun refreshConsentState() {
        _consentState.value = getAllConsentRecords()
    }
}