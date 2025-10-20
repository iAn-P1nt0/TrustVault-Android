package com.trustvault.android.compliance

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SecurityEventMonitor - Real-Time Security Threat Detection & Alerting
 *
 * Implements ISO 27001 A.16 (Information Security Incident Management) and
 * GDPR Article 33 (Notification of personal data breach) requirements.
 *
 * **ISO 27001 A.16.1.1:** Event logging
 * **ISO 27001 A.16.1.4:** Assessment of security events
 * **ISO 27001 A.16.1.7:** Collection of evidence
 * **GDPR Article 33:** Breach notification within 72 hours
 *
 * **Threat Detection Categories:**
 * 1. Authentication anomalies (brute force, credential stuffing)
 * 2. Data access anomalies (unusual patterns, mass extraction)
 * 3. Encryption failures (key corruption, algorithm failures)
 * 4. System integrity violations (tampered logs, unauthorized changes)
 * 5. Privacy violations (consent bypassed, data accessed without permission)
 *
 * **Real-Time Monitoring:**
 * - Continuous event stream processing
 * - Pattern-based threat detection
 * - Configurable alert thresholds
 * - Automatic incident escalation
 * - Real-time notification delivery
 *
 * @property context Application context
 * @property auditLogger Security event logging
 */
@Singleton
class SecurityEventMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auditLogger: AuditLogger
) {

    companion object {
        private const val TAG = "SecurityEventMonitor"

        // Threat detection thresholds
        private const val MAX_FAILED_LOGINS_PER_HOUR = 5
        private const val MAX_FAILED_LOGINS_PER_DAY = 10
        private const val MAX_DATA_EXPORTS_PER_HOUR = 3
        private const val MAX_ENCRYPTION_FAILURES_PER_HOUR = 2
        private const val SUSPICIOUS_ACCESS_TIME_WINDOW_MS = 60000L // 1 minute

        // Time windows for analysis
        private const val ONE_HOUR_MS = 3600000L
        private const val ONE_DAY_MS = 86400000L
    }

    // Active monitoring state
    private val monitoringScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var isMonitoring = false

    // Event counters for threat detection
    private val failedLoginAttempts = ConcurrentHashMap<Long, Int>() // timestamp -> count
    private val dataExportAttempts = ConcurrentHashMap<Long, Int>()
    private val encryptionFailures = ConcurrentHashMap<Long, Int>()

    // Real-time threat alerts
    private val _securityAlerts = MutableSharedFlow<SecurityAlert>(
        replay = 10,
        extraBufferCapacity = 50
    )
    val securityAlerts: SharedFlow<SecurityAlert> = _securityAlerts.asSharedFlow()

    // Monitoring statistics
    private val _monitoringStats = MutableStateFlow(MonitoringStatistics())
    val monitoringStats: StateFlow<MonitoringStatistics> = _monitoringStats.asStateFlow()

    /**
     * Security alert with severity and details.
     */
    data class SecurityAlert(
        val id: String,
        val timestamp: Long,
        val severity: AlertSeverity,
        val category: ThreatCategory,
        val title: String,
        val description: String,
        val affectedResource: String?,
        val recommendedAction: String,
        val metadata: Map<String, Any> = emptyMap()
    )

    /**
     * Alert severity levels.
     */
    enum class AlertSeverity {
        LOW,      // Informational, no immediate action required
        MEDIUM,   // Suspicious activity, monitor closely
        HIGH,     // Likely security incident, investigate immediately
        CRITICAL  // Active security breach, immediate response required
    }

    /**
     * Threat categories.
     */
    enum class ThreatCategory(val displayName: String) {
        AUTHENTICATION_ANOMALY("Authentication Anomaly"),
        BRUTE_FORCE_ATTACK("Brute Force Attack"),
        DATA_EXFILTRATION("Data Exfiltration Attempt"),
        ENCRYPTION_FAILURE("Encryption System Failure"),
        LOG_TAMPERING("Audit Log Tampering"),
        UNAUTHORIZED_ACCESS("Unauthorized Access Attempt"),
        PRIVACY_VIOLATION("Privacy Policy Violation"),
        SYSTEM_INTEGRITY("System Integrity Violation"),
        SUSPICIOUS_BEHAVIOR("Suspicious User Behavior")
    }

    /**
     * Monitoring statistics.
     */
    data class MonitoringStatistics(
        val monitoringStartTime: Long = 0L,
        val eventsProcessed: Long = 0L,
        val alertsGenerated: Long = 0L,
        val criticalAlerts: Long = 0L,
        val lastAlertTime: Long = 0L,
        val activeThreats: Int = 0
    )

    /**
     * Starts real-time security monitoring.
     */
    fun startMonitoring() {
        if (isMonitoring) {
            Log.w(TAG, "Security monitoring already active")
            return
        }

        isMonitoring = true
        _monitoringStats.value = MonitoringStatistics(monitoringStartTime = System.currentTimeMillis())

        Log.i(TAG, "=== Security Event Monitoring STARTED ===")

        // Start event stream monitoring
        monitoringScope.launch {
            auditLogger.observeSecurityEvents()
                .collect { event ->
                    processSecurityEvent(event)
                }
        }

        // Start periodic cleanup of old event counters
        monitoringScope.launch {
            while (isActive && isMonitoring) {
                cleanupOldEventCounters()
                delay(TimeUnit.MINUTES.toMillis(5))
            }
        }

        auditLogger.logSecurityEvent(
            event = AuditLogger.SecurityEvent.SECURITY_MONITORING_STARTED,
            severity = AuditLogger.EventSeverity.INFO,
            action = "monitoring_started",
            resource = "security_monitor",
            result = AuditLogger.EventResult.SUCCESS
        )
    }

    /**
     * Stops security monitoring.
     */
    fun stopMonitoring() {
        if (!isMonitoring) {
            return
        }

        isMonitoring = false
        monitoringScope.coroutineContext.cancelChildren()

        Log.i(TAG, "=== Security Event Monitoring STOPPED ===")

        auditLogger.logSecurityEvent(
            event = AuditLogger.SecurityEvent.SECURITY_MONITORING_STOPPED,
            severity = AuditLogger.EventSeverity.INFO,
            action = "monitoring_stopped",
            resource = "security_monitor",
            result = AuditLogger.EventResult.SUCCESS
        )
    }

    /**
     * Processes incoming security event for threat detection.
     */
    private suspend fun processSecurityEvent(event: AuditLogger.AuditLogEntry) {
        // Update statistics
        _monitoringStats.value = _monitoringStats.value.copy(
            eventsProcessed = _monitoringStats.value.eventsProcessed + 1
        )

        // Analyze event for threats
        when (event.event) {
            AuditLogger.SecurityEvent.AUTH_LOGIN_FAILURE,
            AuditLogger.SecurityEvent.AUTH_BIOMETRIC_FAILURE -> {
                detectBruteForceAttack(event)
            }

            AuditLogger.SecurityEvent.DATA_EXPORTED -> {
                detectDataExfiltration(event)
            }

            AuditLogger.SecurityEvent.SECURITY_ENCRYPTION_FAILURE -> {
                detectEncryptionFailure(event)
            }

            AuditLogger.SecurityEvent.SECURITY_INTEGRITY_VIOLATION -> {
                detectLogTampering(event)
            }

            AuditLogger.SecurityEvent.PRIVACY_CONSENT_WITHDRAWN -> {
                detectPrivacyViolation(event)
            }

            AuditLogger.SecurityEvent.SECURITY_THREAT_DETECTED,
            AuditLogger.SecurityEvent.SECURITY_BREACH_DETECTED -> {
                escalateThreat(event)
            }

            else -> {
                // No specific threat pattern for this event
            }
        }
    }

    /**
     * Detects brute force authentication attacks.
     */
    private suspend fun detectBruteForceAttack(event: AuditLogger.AuditLogEntry) {
        val now = System.currentTimeMillis()
        val oneHourAgo = now - ONE_HOUR_MS
        val oneDayAgo = now - ONE_DAY_MS

        // Count recent failed attempts
        val failuresLastHour = failedLoginAttempts.entries
            .filter { it.key >= oneHourAgo }
            .sumOf { it.value }

        val failuresLastDay = failedLoginAttempts.entries
            .filter { it.key >= oneDayAgo }
            .sumOf { it.value }

        // Record this failure
        val hourBucket = now / ONE_HOUR_MS * ONE_HOUR_MS
        failedLoginAttempts[hourBucket] = failedLoginAttempts.getOrDefault(hourBucket, 0) + 1

        // Check thresholds
        when {
            failuresLastHour >= MAX_FAILED_LOGINS_PER_HOUR -> {
                raiseAlert(
                    severity = AlertSeverity.HIGH,
                    category = ThreatCategory.BRUTE_FORCE_ATTACK,
                    title = "Brute Force Attack Detected",
                    description = "$failuresLastHour failed login attempts in the last hour",
                    affectedResource = "authentication_system",
                    recommendedAction = "Lock account temporarily and review access logs",
                    metadata = mapOf(
                        "failures_last_hour" to failuresLastHour,
                        "threshold" to MAX_FAILED_LOGINS_PER_HOUR
                    )
                )
            }

            failuresLastDay >= MAX_FAILED_LOGINS_PER_DAY -> {
                raiseAlert(
                    severity = AlertSeverity.MEDIUM,
                    category = ThreatCategory.AUTHENTICATION_ANOMALY,
                    title = "Unusual Authentication Activity",
                    description = "$failuresLastDay failed login attempts in the last 24 hours",
                    affectedResource = "authentication_system",
                    recommendedAction = "Monitor authentication attempts and consider password change",
                    metadata = mapOf(
                        "failures_last_day" to failuresLastDay,
                        "threshold" to MAX_FAILED_LOGINS_PER_DAY
                    )
                )
            }
        }
    }

    /**
     * Detects potential data exfiltration attempts.
     */
    private suspend fun detectDataExfiltration(event: AuditLogger.AuditLogEntry) {
        val now = System.currentTimeMillis()
        val oneHourAgo = now - ONE_HOUR_MS

        // Count recent export attempts
        val exportsLastHour = dataExportAttempts.entries
            .filter { it.key >= oneHourAgo }
            .sumOf { it.value }

        // Record this export
        val hourBucket = now / ONE_HOUR_MS * ONE_HOUR_MS
        dataExportAttempts[hourBucket] = dataExportAttempts.getOrDefault(hourBucket, 0) + 1

        if (exportsLastHour >= MAX_DATA_EXPORTS_PER_HOUR) {
            raiseAlert(
                severity = AlertSeverity.HIGH,
                category = ThreatCategory.DATA_EXFILTRATION,
                title = "Potential Data Exfiltration Detected",
                description = "$exportsLastHour data export attempts in the last hour",
                affectedResource = "data_export_system",
                recommendedAction = "Review export logs and verify user intent. Consider data breach protocols.",
                metadata = mapOf(
                    "exports_last_hour" to exportsLastHour,
                    "threshold" to MAX_DATA_EXPORTS_PER_HOUR
                )
            )
        }
    }

    /**
     * Detects encryption system failures.
     */
    private suspend fun detectEncryptionFailure(event: AuditLogger.AuditLogEntry) {
        val now = System.currentTimeMillis()
        val oneHourAgo = now - ONE_HOUR_MS

        // Count recent encryption failures
        val failuresLastHour = encryptionFailures.entries
            .filter { it.key >= oneHourAgo }
            .sumOf { it.value }

        // Record this failure
        val hourBucket = now / ONE_HOUR_MS * ONE_HOUR_MS
        encryptionFailures[hourBucket] = encryptionFailures.getOrDefault(hourBucket, 0) + 1

        if (failuresLastHour >= MAX_ENCRYPTION_FAILURES_PER_HOUR) {
            raiseAlert(
                severity = AlertSeverity.CRITICAL,
                category = ThreatCategory.ENCRYPTION_FAILURE,
                title = "Critical Encryption System Failure",
                description = "$failuresLastHour encryption failures in the last hour",
                affectedResource = "encryption_system",
                recommendedAction = "IMMEDIATE ACTION: Stop data operations and investigate encryption integrity",
                metadata = mapOf(
                    "failures_last_hour" to failuresLastHour,
                    "threshold" to MAX_ENCRYPTION_FAILURES_PER_HOUR
                )
            )
        }
    }

    /**
     * Detects audit log tampering.
     */
    private suspend fun detectLogTampering(event: AuditLogger.AuditLogEntry) {
        raiseAlert(
            severity = AlertSeverity.CRITICAL,
            category = ThreatCategory.LOG_TAMPERING,
            title = "Audit Log Tampering Detected",
            description = "Integrity violation detected in audit logs",
            affectedResource = "audit_system",
            recommendedAction = "CRITICAL: Investigate system compromise. Audit logs have been tampered with.",
            metadata = mapOf(
                "event_id" to event.id,
                "event_timestamp" to event.timestamp
            )
        )
    }

    /**
     * Detects privacy policy violations.
     */
    private suspend fun detectPrivacyViolation(event: AuditLogger.AuditLogEntry) {
        raiseAlert(
            severity = AlertSeverity.HIGH,
            category = ThreatCategory.PRIVACY_VIOLATION,
            title = "Privacy Policy Violation",
            description = "Data processing occurred without proper consent",
            affectedResource = event.resource,
            recommendedAction = "Review consent records and halt unauthorized data processing",
            metadata = mapOf(
                "event_id" to event.id,
                "resource" to event.resource
            )
        )
    }

    /**
     * Escalates existing threat to higher severity.
     */
    private suspend fun escalateThreat(event: AuditLogger.AuditLogEntry) {
        raiseAlert(
            severity = AlertSeverity.CRITICAL,
            category = ThreatCategory.SYSTEM_INTEGRITY,
            title = "Security Breach Detected",
            description = "Critical security event requiring immediate attention",
            affectedResource = event.resource,
            recommendedAction = "URGENT: Initiate incident response protocol",
            metadata = mapOf(
                "original_event" to event.event.name,
                "event_id" to event.id
            )
        )
    }

    /**
     * Raises security alert and logs to audit system.
     */
    private suspend fun raiseAlert(
        severity: AlertSeverity,
        category: ThreatCategory,
        title: String,
        description: String,
        affectedResource: String?,
        recommendedAction: String,
        metadata: Map<String, Any> = emptyMap()
    ) {
        val alert = SecurityAlert(
            id = "alert_${System.currentTimeMillis()}_${(0..9999).random()}",
            timestamp = System.currentTimeMillis(),
            severity = severity,
            category = category,
            title = title,
            description = description,
            affectedResource = affectedResource,
            recommendedAction = recommendedAction,
            metadata = metadata
        )

        // Emit alert to subscribers
        _securityAlerts.emit(alert)

        // Update statistics
        _monitoringStats.value = _monitoringStats.value.copy(
            alertsGenerated = _monitoringStats.value.alertsGenerated + 1,
            criticalAlerts = if (severity == AlertSeverity.CRITICAL) {
                _monitoringStats.value.criticalAlerts + 1
            } else {
                _monitoringStats.value.criticalAlerts
            },
            lastAlertTime = alert.timestamp,
            activeThreats = _monitoringStats.value.activeThreats + 1
        )

        // Log to audit system
        auditLogger.logSecurityEvent(
            event = AuditLogger.SecurityEvent.SECURITY_ALERT_RAISED,
            severity = when (severity) {
                AlertSeverity.LOW -> AuditLogger.EventSeverity.INFO
                AlertSeverity.MEDIUM -> AuditLogger.EventSeverity.WARNING
                AlertSeverity.HIGH -> AuditLogger.EventSeverity.ERROR
                AlertSeverity.CRITICAL -> AuditLogger.EventSeverity.CRITICAL
            },
            action = "security_alert_raised",
            resource = affectedResource ?: "unknown",
            result = AuditLogger.EventResult.SUCCESS,
            metadata = mapOf(
                "alert_id" to alert.id,
                "category" to category.name,
                "title" to title,
                "description" to description
            ) + metadata
        )

        Log.w(TAG, "🚨 SECURITY ALERT [$severity]: $title - $description")
    }

    /**
     * Cleans up old event counters to prevent memory bloat.
     */
    private fun cleanupOldEventCounters() {
        val cutoffTime = System.currentTimeMillis() - ONE_DAY_MS

        failedLoginAttempts.entries.removeIf { it.key < cutoffTime }
        dataExportAttempts.entries.removeIf { it.key < cutoffTime }
        encryptionFailures.entries.removeIf { it.key < cutoffTime }

        Log.d(TAG, "Cleaned up old event counters")
    }

    /**
     * Gets recent security alerts.
     */
    suspend fun getRecentAlerts(limit: Int = 50): List<SecurityAlert> {
        // Note: This would typically query a persistent store
        // For now, alerts are only available from the flow
        return emptyList()
    }

    /**
     * Acknowledges and clears an alert.
     */
    fun acknowledgeAlert(alertId: String) {
        _monitoringStats.value = _monitoringStats.value.copy(
            activeThreats = maxOf(0, _monitoringStats.value.activeThreats - 1)
        )

        auditLogger.logSecurityEvent(
            event = AuditLogger.SecurityEvent.SECURITY_ALERT_ACKNOWLEDGED,
            severity = AuditLogger.EventSeverity.INFO,
            action = "alert_acknowledged",
            resource = "security_monitor",
            result = AuditLogger.EventResult.SUCCESS,
            metadata = mapOf("alert_id" to alertId)
        )

        Log.d(TAG, "Alert acknowledged: $alertId")
    }

    /**
     * Gets current monitoring status.
     */
    fun isMonitoringActive(): Boolean = isMonitoring
}
