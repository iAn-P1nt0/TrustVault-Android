package com.trustvault.android.compliance

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataBreachManager - GDPR Article 33 Breach Notification System
 *
 * Implements GDPR Article 33 requirements for notifying supervisory authorities
 * of personal data breaches within 72 hours.
 *
 * **GDPR Article 33:**
 * - Notification to supervisory authority within 72 hours of becoming aware
 * - Description of nature of breach
 * - Contact details of Data Protection Officer (DPO)
 * - Likely consequences of the breach
 * - Measures taken or proposed to address the breach
 *
 * **GDPR Article 34:**
 * - Communication to data subjects when high risk to rights and freedoms
 *
 * **ISO 27001 A.16.1.4:**
 * - Assessment and decision on information security events
 *
 * **Breach Detection Triggers:**
 * - Encryption key compromise
 * - Unauthorized data access
 * - Data exfiltration detected
 * - Audit log tampering
 * - System integrity violations
 * - Multiple critical security alerts
 *
 * @property context Application context
 * @property auditLogger Security event logging
 * @property securityMonitor Real-time threat detection
 */
@Singleton
class DataBreachManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auditLogger: AuditLogger,
    private val securityMonitor: SecurityEventMonitor
) {

    companion object {
        private const val TAG = "DataBreachManager"
        private const val BREACH_RECORDS_FILE = "data_breach_records.json"

        // GDPR Article 33: 72-hour notification window
        private const val NOTIFICATION_DEADLINE_HOURS = 72L
        private const val NOTIFICATION_DEADLINE_MS = NOTIFICATION_DEADLINE_HOURS * 60 * 60 * 1000
    }

    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        .create()

    // Active breach notifications
    private val _activeBreaches = MutableStateFlow<List<BreachRecord>>(emptyList())
    val activeBreaches: StateFlow<List<BreachRecord>> = _activeBreaches.asStateFlow()

    init {
        loadBreachRecords()
    }

    /**
     * Data breach record.
     */
    data class BreachRecord(
        val id: String,
        val discoveryTimestamp: Long,
        val notificationDeadline: Long,
        val severity: BreachSeverity,
        val breachType: BreachType,
        val affectedDataCategories: List<String>,
        val affectedRecordCount: Int?,
        val description: String,
        val rootCause: String?,
        val containmentMeasures: List<String>,
        val mitigationActions: List<String>,
        val status: BreachStatus,
        val notifiedAt: Long?,
        val resolvedAt: Long?,
        val dpoContactDetails: String?,
        val likelyConsequences: String,
        val metadata: Map<String, String> = emptyMap()
    )

    /**
     * Breach severity levels.
     */
    enum class BreachSeverity {
        LOW,        // No significant risk to data subjects
        MEDIUM,     // Limited risk, monitoring required
        HIGH,       // Significant risk, notification likely required
        CRITICAL    // Severe risk, immediate notification required
    }

    /**
     * Types of data breaches.
     */
    enum class BreachType(val displayName: String) {
        UNAUTHORIZED_ACCESS("Unauthorized Access"),
        DATA_EXFILTRATION("Data Exfiltration"),
        ENCRYPTION_COMPROMISE("Encryption Key Compromise"),
        SYSTEM_INTRUSION("System Intrusion"),
        MALWARE_INFECTION("Malware Infection"),
        ACCIDENTAL_DISCLOSURE("Accidental Disclosure"),
        LOG_TAMPERING("Audit Log Tampering"),
        PHYSICAL_THEFT("Physical Device Theft"),
        INSIDER_THREAT("Insider Threat"),
        UNKNOWN("Unknown/Under Investigation")
    }

    /**
     * Breach notification status.
     */
    enum class BreachStatus {
        DETECTED,           // Breach detected, assessment ongoing
        CONFIRMED,          // Breach confirmed, preparing notification
        NOTIFIED,           // Authority notified within 72 hours
        LATE_NOTIFICATION,  // Authority notified after 72 hours
        CONTAINED,          // Breach contained, no notification required
        RESOLVED            // Breach fully resolved and closed
    }

    /**
     * Reports a potential data breach.
     *
     * GDPR Article 33: 72-hour clock starts from moment of "becoming aware"
     *
     * @param severity Severity assessment
     * @param breachType Type of breach
     * @param affectedDataCategories Categories of personal data affected
     * @param affectedRecordCount Estimated number of records affected
     * @param description Detailed description of the breach
     * @param rootCause Known or suspected root cause
     * @return Created breach record
     */
    fun reportBreach(
        severity: BreachSeverity,
        breachType: BreachType,
        affectedDataCategories: List<String>,
        affectedRecordCount: Int? = null,
        description: String,
        rootCause: String? = null
    ): BreachRecord {
        val now = System.currentTimeMillis()
        val deadline = now + NOTIFICATION_DEADLINE_MS

        val breach = BreachRecord(
            id = "breach_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}",
            discoveryTimestamp = now,
            notificationDeadline = deadline,
            severity = severity,
            breachType = breachType,
            affectedDataCategories = affectedDataCategories,
            affectedRecordCount = affectedRecordCount,
            description = description,
            rootCause = rootCause,
            containmentMeasures = emptyList(),
            mitigationActions = emptyList(),
            status = BreachStatus.DETECTED,
            notifiedAt = null,
            resolvedAt = null,
            dpoContactDetails = null,
            likelyConsequences = assessConsequences(severity, breachType, affectedDataCategories)
        )

        // Save breach record
        val currentBreaches = _activeBreaches.value.toMutableList()
        currentBreaches.add(breach)
        _activeBreaches.value = currentBreaches
        saveBreachRecords()

        // Log to audit system
        auditLogger.logSecurityEvent(
            event = AuditLogger.SecurityEvent.SECURITY_BREACH_DETECTED,
            severity = AuditLogger.EventSeverity.CRITICAL,
            action = "data_breach_reported",
            resource = "data_breach_manager",
            result = "success",
            metadata = mapOf(
                "breach_id" to breach.id,
                "severity" to severity.name,
                "breach_type" to breachType.name,
                "affected_categories" to affectedDataCategories.joinToString(","),
                "notification_deadline" to formatTimestamp(deadline)
            )
        )

        Log.e(TAG, "🚨 DATA BREACH REPORTED [${severity.name}]: ${breachType.displayName}")
        Log.e(TAG, "Notification deadline: ${formatTimestamp(deadline)} (72 hours)")

        return breach
    }

    /**
     * Updates breach record with containment measures.
     */
    fun recordContainmentMeasures(breachId: String, measures: List<String>) {
        updateBreachRecord(breachId) { breach ->
            breach.copy(
                containmentMeasures = breach.containmentMeasures + measures
            )
        }

        auditLogger.logSecurityEvent(
            event = AuditLogger.SecurityEvent.SECURITY_BREACH_CONTAINED,
            severity = AuditLogger.EventSeverity.INFO,
            action = "containment_measures_recorded",
            resource = "breach:$breachId",
            result = "success",
            metadata = mapOf(
                "measures_count" to measures.size.toString(),
                "measures" to measures.joinToString("; ")
            )
        )
    }

    /**
     * Updates breach record with mitigation actions.
     */
    fun recordMitigationActions(breachId: String, actions: List<String>) {
        updateBreachRecord(breachId) { breach ->
            breach.copy(
                mitigationActions = breach.mitigationActions + actions
            )
        }

        auditLogger.logSecurityEvent(
            event = AuditLogger.SecurityEvent.SECURITY_BREACH_MITIGATED,
            severity = AuditLogger.EventSeverity.INFO,
            action = "mitigation_actions_recorded",
            resource = "breach:$breachId",
            result = "success",
            metadata = mapOf(
                "actions_count" to actions.size.toString()
            )
        )
    }

    /**
     * Marks breach as notified to supervisory authority.
     *
     * GDPR Article 33: Record notification within 72 hours
     */
    fun markAsNotified(breachId: String, dpoContactDetails: String? = null) {
        val notificationTime = System.currentTimeMillis()

        updateBreachRecord(breachId) { breach ->
            val isLate = notificationTime > breach.notificationDeadline
            val status = if (isLate) BreachStatus.LATE_NOTIFICATION else BreachStatus.NOTIFIED

            breach.copy(
                status = status,
                notifiedAt = notificationTime,
                dpoContactDetails = dpoContactDetails
            )
        }

        auditLogger.logSecurityEvent(
            event = AuditLogger.SecurityEvent.PRIVACY_BREACH_NOTIFIED,
            severity = AuditLogger.EventSeverity.INFO,
            action = "breach_notification_sent",
            resource = "breach:$breachId",
            result = "success",
            metadata = mapOf(
                "notification_timestamp" to formatTimestamp(notificationTime),
                "dpo_contact" to (dpoContactDetails ?: "not_provided")
            )
        )
    }

    /**
     * Marks breach as contained (no notification required).
     */
    fun markAsContained(breachId: String, reason: String) {
        updateBreachRecord(breachId) { breach ->
            breach.copy(status = BreachStatus.CONTAINED)
        }

        auditLogger.logSecurityEvent(
            event = AuditLogger.SecurityEvent.SECURITY_BREACH_CONTAINED,
            severity = AuditLogger.EventSeverity.INFO,
            action = "breach_contained",
            resource = "breach:$breachId",
            result = "success",
            metadata = mapOf("reason" to reason)
        )
    }

    /**
     * Marks breach as fully resolved.
     */
    fun markAsResolved(breachId: String) {
        val resolutionTime = System.currentTimeMillis()

        updateBreachRecord(breachId) { breach ->
            breach.copy(
                status = BreachStatus.RESOLVED,
                resolvedAt = resolutionTime
            )
        }

        auditLogger.logSecurityEvent(
            event = AuditLogger.SecurityEvent.SECURITY_BREACH_RESOLVED,
            severity = AuditLogger.EventSeverity.INFO,
            action = "breach_resolved",
            resource = "breach:$breachId",
            result = "success",
            metadata = mapOf("resolution_timestamp" to formatTimestamp(resolutionTime))
        )
    }

    /**
     * Gets breach record by ID.
     */
    fun getBreachRecord(breachId: String): BreachRecord? {
        return _activeBreaches.value.find { it.id == breachId }
    }

    /**
     * Gets all breaches by status.
     */
    fun getBreachesByStatus(status: BreachStatus): List<BreachRecord> {
        return _activeBreaches.value.filter { it.status == status }
    }

    /**
     * Gets breaches approaching notification deadline.
     */
    fun getBreachesNearingDeadline(hoursRemaining: Long = 24): List<BreachRecord> {
        val cutoffTime = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(hoursRemaining)

        return _activeBreaches.value.filter { breach ->
            breach.status in listOf(BreachStatus.DETECTED, BreachStatus.CONFIRMED) &&
            breach.notificationDeadline <= cutoffTime
        }
    }

    /**
     * Generates GDPR Article 33 breach notification report.
     */
    fun generateBreachNotificationReport(breachId: String): String {
        val breach = getBreachRecord(breachId)
            ?: return "Error: Breach record not found"

        val hoursUntilDeadline = TimeUnit.MILLISECONDS.toHours(
            breach.notificationDeadline - System.currentTimeMillis()
        )

        return buildString {
            appendLine("═══════════════════════════════════════════════════")
            appendLine("   GDPR ARTICLE 33 - PERSONAL DATA BREACH NOTIFICATION")
            appendLine("═══════════════════════════════════════════════════")
            appendLine()
            appendLine("BREACH IDENTIFICATION:")
            appendLine("  Breach ID: ${breach.id}")
            appendLine("  Discovery Date: ${formatTimestamp(breach.discoveryTimestamp)}")
            appendLine("  Notification Deadline: ${formatTimestamp(breach.notificationDeadline)}")
            appendLine("  Hours Until Deadline: $hoursUntilDeadline hours")
            appendLine("  Current Status: ${breach.status.name}")
            appendLine()
            appendLine("NATURE OF THE BREACH:")
            appendLine("  Breach Type: ${breach.breachType.displayName}")
            appendLine("  Severity: ${breach.severity.name}")
            appendLine("  Description: ${breach.description}")
            if (breach.rootCause != null) {
                appendLine("  Root Cause: ${breach.rootCause}")
            }
            appendLine()
            appendLine("AFFECTED DATA:")
            appendLine("  Data Categories:")
            breach.affectedDataCategories.forEach { category ->
                appendLine("    - $category")
            }
            if (breach.affectedRecordCount != null) {
                appendLine("  Estimated Record Count: ${breach.affectedRecordCount}")
            }
            appendLine()
            appendLine("CONTACT DETAILS:")
            if (breach.dpoContactDetails != null) {
                appendLine("  DPO Contact: ${breach.dpoContactDetails}")
            } else {
                appendLine("  DPO Contact: TrustVault User (Local-only app, no DPO)")
            }
            appendLine()
            appendLine("LIKELY CONSEQUENCES:")
            appendLine("  ${breach.likelyConsequences}")
            appendLine()
            if (breach.containmentMeasures.isNotEmpty()) {
                appendLine("CONTAINMENT MEASURES TAKEN:")
                breach.containmentMeasures.forEach { measure ->
                    appendLine("  - $measure")
                }
                appendLine()
            }
            if (breach.mitigationActions.isNotEmpty()) {
                appendLine("MITIGATION ACTIONS:")
                breach.mitigationActions.forEach { action ->
                    appendLine("  - $action")
                }
                appendLine()
            }
            appendLine("═══════════════════════════════════════════════════")
            appendLine("Generated: ${formatTimestamp(System.currentTimeMillis())}")
            appendLine("═══════════════════════════════════════════════════")
        }
    }

    /**
     * Exports breach records to JSON for regulatory compliance.
     */
    fun exportBreachRecords(): String {
        return gson.toJson(
            mapOf(
                "export_timestamp" to System.currentTimeMillis(),
                "export_date" to formatTimestamp(System.currentTimeMillis()),
                "total_breaches" to _activeBreaches.value.size,
                "breach_records" to _activeBreaches.value
            )
        )
    }

    /**
     * Assesses likely consequences of breach.
     */
    private fun assessConsequences(
        severity: BreachSeverity,
        breachType: BreachType,
        affectedCategories: List<String>
    ): String {
        return when (severity) {
            BreachSeverity.CRITICAL -> "High risk to rights and freedoms of data subjects. " +
                    "Potential for identity theft, financial loss, reputational damage, or discrimination. " +
                    "Data subjects must be notified without undue delay (GDPR Article 34)."

            BreachSeverity.HIGH -> "Significant risk to data subjects. " +
                    "Potential for unauthorized access to sensitive personal data. " +
                    "Notification to supervisory authority required within 72 hours."

            BreachSeverity.MEDIUM -> "Limited risk to data subjects. " +
                    "Breach contained with appropriate security measures. " +
                    "Notification may be required depending on data sensitivity."

            BreachSeverity.LOW -> "Minimal risk to data subjects. " +
                    "Security measures prevented actual data compromise. " +
                    "Notification may not be required, but breach must be documented."
        }
    }

    /**
     * Updates breach record with transformation function.
     */
    private fun updateBreachRecord(breachId: String, transform: (BreachRecord) -> BreachRecord) {
        val currentBreaches = _activeBreaches.value.toMutableList()
        val index = currentBreaches.indexOfFirst { it.id == breachId }

        if (index != -1) {
            currentBreaches[index] = transform(currentBreaches[index])
            _activeBreaches.value = currentBreaches
            saveBreachRecords()
        }
    }

    /**
     * Saves breach records to persistent storage.
     */
    private fun saveBreachRecords() {
        try {
            val file = File(context.filesDir, BREACH_RECORDS_FILE)
            file.writeText(gson.toJson(_activeBreaches.value))
        } catch (e: Exception) {
            Log.e(TAG, "Error saving breach records", e)
        }
    }

    /**
     * Loads breach records from persistent storage.
     */
    private fun loadBreachRecords() {
        try {
            val file = File(context.filesDir, BREACH_RECORDS_FILE)
            if (file.exists()) {
                val json = file.readText()
                val breaches = gson.fromJson(json, Array<BreachRecord>::class.java).toList()
                _activeBreaches.value = breaches
                Log.d(TAG, "Loaded ${breaches.size} breach records")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading breach records", e)
        }
    }

    /**
     * Formats timestamp for display.
     */
    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US)
        return sdf.format(Date(timestamp))
    }
}
