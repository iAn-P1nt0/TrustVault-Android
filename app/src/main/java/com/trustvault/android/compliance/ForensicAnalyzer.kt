package com.trustvault.android.compliance

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ForensicAnalyzer - Security Incident Investigation & Event Correlation
 *
 * Provides forensic analysis capabilities for security incident response and
 * compliance investigations.
 *
 * **ISO 27001 A.16.1.5:** Response to information security incidents
 * **ISO 27001 A.16.1.6:** Learning from information security incidents
 * **ISO 27001 A.16.1.7:** Collection of evidence
 *
 * **Capabilities:**
 * - Event correlation across time windows
 * - Timeline reconstruction
 * - Attack chain analysis
 * - Anomaly pattern detection
 * - Evidence collection and preservation
 * - Incident impact assessment
 *
 * @property context Application context
 * @property auditLogger Audit log access
 */
@Singleton
class ForensicAnalyzer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auditLogger: AuditLogger
) {

    companion object {
        private const val TAG = "ForensicAnalyzer"
    }

    /**
     * Event correlation result.
     */
    data class CorrelationResult(
        val correlationId: String,
        val timestamp: Long,
        val timeWindow: TimeWindow,
        val events: List<AuditLogger.AuditLogEntry>,
        val correlatedEvents: List<EventCluster>,
        val suspiciousPatterns: List<SuspiciousPattern>,
        val attackChain: List<AttackStage>?,
        val impactAssessment: ImpactAssessment
    )

    /**
     * Time window for analysis.
     */
    data class TimeWindow(
        val startTime: Long,
        val endTime: Long,
        val durationMs: Long
    ) {
        fun contains(timestamp: Long): Boolean {
            return timestamp in startTime..endTime
        }
    }

    /**
     * Cluster of related events.
     */
    data class EventCluster(
        val clusterId: String,
        val events: List<AuditLogger.AuditLogEntry>,
        val pattern: ClusterPattern,
        val confidence: Double, // 0.0 to 1.0
        val description: String
    )

    /**
     * Types of event cluster patterns.
     */
    enum class ClusterPattern(val displayName: String) {
        AUTHENTICATION_BURST("Authentication Burst"),
        DATA_ACCESS_SPIKE("Data Access Spike"),
        ENCRYPTION_ANOMALY("Encryption Anomaly"),
        PRIVILEGE_ESCALATION("Privilege Escalation Attempt"),
        DATA_EXFILTRATION("Data Exfiltration Pattern"),
        SYSTEM_RECONNAISSANCE("System Reconnaissance"),
        PERSISTENCE_ATTEMPT("Persistence Mechanism"),
        LATERAL_MOVEMENT("Lateral Movement"),
        IMPACT_STAGE("Impact/Destruction Stage")
    }

    /**
     * Suspicious activity pattern.
     */
    data class SuspiciousPattern(
        val patternId: String,
        val patternType: PatternType,
        val severity: Severity,
        val description: String,
        val evidenceEvents: List<String>, // Event IDs
        val indicators: Map<String, Any>
    )

    /**
     * Pattern types for anomaly detection.
     */
    enum class PatternType {
        TEMPORAL_ANOMALY,      // Unusual timing
        FREQUENCY_ANOMALY,     // Unusual frequency
        SEQUENCE_ANOMALY,      // Unusual sequence
        BEHAVIORAL_ANOMALY,    // Unusual behavior
        GEOGRAPHIC_ANOMALY     // Unusual location (if available)
    }

    /**
     * Severity levels.
     */
    enum class Severity {
        INFO,
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    /**
     * Attack chain stage (MITRE ATT&CK inspired).
     */
    data class AttackStage(
        val stage: AttackPhase,
        val events: List<AuditLogger.AuditLogEntry>,
        val tactics: List<String>,
        val description: String
    )

    /**
     * Attack phases (simplified MITRE ATT&CK).
     */
    enum class AttackPhase(val displayName: String) {
        RECONNAISSANCE("Reconnaissance"),
        INITIAL_ACCESS("Initial Access"),
        EXECUTION("Execution"),
        PERSISTENCE("Persistence"),
        PRIVILEGE_ESCALATION("Privilege Escalation"),
        DEFENSE_EVASION("Defense Evasion"),
        CREDENTIAL_ACCESS("Credential Access"),
        DISCOVERY("Discovery"),
        COLLECTION("Collection"),
        EXFILTRATION("Exfiltration"),
        IMPACT("Impact")
    }

    /**
     * Impact assessment of incident.
     */
    data class ImpactAssessment(
        val confidentialityImpact: Impact,
        val integrityImpact: Impact,
        val availabilityImpact: Impact,
        val affectedAssets: List<String>,
        val estimatedDataRecords: Int?,
        val overallSeverity: Severity
    )

    /**
     * CIA triad impact levels.
     */
    enum class Impact {
        NONE,      // No impact
        LOW,       // Limited impact
        MEDIUM,    // Moderate impact
        HIGH       // Severe impact
    }

    /**
     * Analyzes events within time window for correlations.
     *
     * @param startTime Window start timestamp
     * @param endTime Window end timestamp
     * @return Correlation analysis results
     */
    suspend fun analyzeTimeWindow(
        startTime: Long,
        endTime: Long
    ): CorrelationResult {
        val timeWindow = TimeWindow(
            startTime = startTime,
            endTime = endTime,
            durationMs = endTime - startTime
        )

        Log.d(TAG, "Starting forensic analysis for time window: ${formatTimestamp(startTime)} to ${formatTimestamp(endTime)}")

        // Get events in time window
        val events = auditLogger.getLogsByTimeRange(startTime, endTime)

        // Perform correlation analysis
        val clusters = correlateEvents(events)
        val patterns = detectSuspiciousPatterns(events)
        val attackChain = reconstructAttackChain(events, clusters)
        val impact = assessImpact(events, patterns)

        return CorrelationResult(
            correlationId = "correlation_${System.currentTimeMillis()}",
            timestamp = System.currentTimeMillis(),
            timeWindow = timeWindow,
            events = events,
            correlatedEvents = clusters,
            suspiciousPatterns = patterns,
            attackChain = attackChain,
            impactAssessment = impact
        )
    }

    /**
     * Analyzes recent activity (last N hours).
     */
    suspend fun analyzeRecentActivity(hours: Int = 24): CorrelationResult {
        val endTime = System.currentTimeMillis()
        val startTime = endTime - TimeUnit.HOURS.toMillis(hours.toLong())
        return analyzeTimeWindow(startTime, endTime)
    }

    /**
     * Reconstructs event timeline for incident investigation.
     */
    suspend fun reconstructTimeline(
        incidentId: String,
        startTime: Long,
        endTime: Long
    ): Timeline {
        val events = auditLogger.getLogsByTimeRange(startTime, endTime)

        val entries = events.map { event ->
            TimelineEntry(
                timestamp = event.timestamp,
                event = event,
                description = formatEventDescription(event),
                significance = assessEventSignificance(event)
            )
        }.sortedBy { it.timestamp }

        return Timeline(
            incidentId = incidentId,
            startTime = startTime,
            endTime = endTime,
            entries = entries,
            totalEvents = entries.size,
            criticalEvents = entries.count { it.significance == EventSignificance.CRITICAL }
        )
    }

    /**
     * Timeline of events.
     */
    data class Timeline(
        val incidentId: String,
        val startTime: Long,
        val endTime: Long,
        val entries: List<TimelineEntry>,
        val totalEvents: Int,
        val criticalEvents: Int
    )

    /**
     * Timeline entry.
     */
    data class TimelineEntry(
        val timestamp: Long,
        val event: AuditLogger.AuditLogEntry,
        val description: String,
        val significance: EventSignificance
    )

    /**
     * Event significance levels.
     */
    enum class EventSignificance {
        ROUTINE,     // Normal operations
        NOTABLE,     // Worth noting
        SUSPICIOUS,  // Potentially malicious
        CRITICAL     // Confirmed security incident
    }

    /**
     * Generates forensic report.
     */
    fun generateForensicReport(correlationResult: CorrelationResult): String {
        return buildString {
            appendLine("═══════════════════════════════════════════════════")
            appendLine("        FORENSIC ANALYSIS REPORT")
            appendLine("═══════════════════════════════════════════════════")
            appendLine()
            appendLine("ANALYSIS DETAILS:")
            appendLine("  Correlation ID: ${correlationResult.correlationId}")
            appendLine("  Analysis Timestamp: ${formatTimestamp(correlationResult.timestamp)}")
            appendLine("  Time Window: ${formatTimestamp(correlationResult.timeWindow.startTime)} to ${formatTimestamp(correlationResult.timeWindow.endTime)}")
            appendLine("  Window Duration: ${formatDuration(correlationResult.timeWindow.durationMs)}")
            appendLine("  Total Events Analyzed: ${correlationResult.events.size}")
            appendLine()

            appendLine("EVENT CORRELATION:")
            appendLine("  Event Clusters Found: ${correlationResult.correlatedEvents.size}")
            correlationResult.correlatedEvents.forEach { cluster ->
                appendLine("    - ${cluster.pattern.displayName}: ${cluster.events.size} events (${(cluster.confidence * 100).toInt()}% confidence)")
            }
            appendLine()

            appendLine("SUSPICIOUS PATTERNS:")
            appendLine("  Patterns Detected: ${correlationResult.suspiciousPatterns.size}")
            correlationResult.suspiciousPatterns.forEach { pattern ->
                appendLine("    - [${pattern.severity}] ${pattern.patternType}: ${pattern.description}")
            }
            appendLine()

            if (correlationResult.attackChain != null) {
                appendLine("ATTACK CHAIN RECONSTRUCTION:")
                correlationResult.attackChain.forEachIndexed { index, stage ->
                    appendLine("  ${index + 1}. ${stage.stage.displayName}")
                    appendLine("     Events: ${stage.events.size}")
                    appendLine("     Description: ${stage.description}")
                }
                appendLine()
            }

            appendLine("IMPACT ASSESSMENT:")
            val impact = correlationResult.impactAssessment
            appendLine("  Confidentiality: ${impact.confidentialityImpact}")
            appendLine("  Integrity: ${impact.integrityImpact}")
            appendLine("  Availability: ${impact.availabilityImpact}")
            appendLine("  Overall Severity: ${impact.overallSeverity}")
            if (impact.affectedAssets.isNotEmpty()) {
                appendLine("  Affected Assets:")
                impact.affectedAssets.forEach { asset ->
                    appendLine("    - $asset")
                }
            }
            appendLine()

            appendLine("═══════════════════════════════════════════════════")
            appendLine("Report Generated: ${formatTimestamp(System.currentTimeMillis())}")
            appendLine("═══════════════════════════════════════════════════")
        }
    }

    /**
     * Correlates events into meaningful clusters.
     */
    private fun correlateEvents(events: List<AuditLogger.AuditLogEntry>): List<EventCluster> {
        val clusters = mutableListOf<EventCluster>()

        // Authentication burst detection
        val authFailures = events.filter {
            it.event in listOf(
                AuditLogger.SecurityEvent.AUTH_LOGIN_FAILURE,
                AuditLogger.SecurityEvent.AUTH_BIOMETRIC_FAILURE
            )
        }

        if (authFailures.size >= 3) {
            clusters.add(
                EventCluster(
                    clusterId = "cluster_auth_burst",
                    events = authFailures,
                    pattern = ClusterPattern.AUTHENTICATION_BURST,
                    confidence = minOf(authFailures.size / 10.0, 1.0),
                    description = "${authFailures.size} failed authentication attempts detected"
                )
            )
        }

        // Data access spike detection
        val dataAccess = events.filter {
            it.event in listOf(
                AuditLogger.SecurityEvent.DATA_READ,
                AuditLogger.SecurityEvent.PRIVACY_DATA_EXPORTED
            )
        }

        if (dataAccess.size >= 5) {
            clusters.add(
                EventCluster(
                    clusterId = "cluster_data_access",
                    events = dataAccess,
                    pattern = ClusterPattern.DATA_ACCESS_SPIKE,
                    confidence = minOf(dataAccess.size / 20.0, 1.0),
                    description = "${dataAccess.size} data access operations in short timeframe"
                )
            )
        }

        // Encryption anomaly detection
        val encryptionFailures = events.filter {
            it.event == AuditLogger.SecurityEvent.SECURITY_ENCRYPTION_FAILURE
        }

        if (encryptionFailures.isNotEmpty()) {
            clusters.add(
                EventCluster(
                    clusterId = "cluster_encryption_anomaly",
                    events = encryptionFailures,
                    pattern = ClusterPattern.ENCRYPTION_ANOMALY,
                    confidence = 0.9,
                    description = "${encryptionFailures.size} encryption failures indicate system compromise"
                )
            )
        }

        return clusters
    }

    /**
     * Detects suspicious patterns in event stream.
     */
    private fun detectSuspiciousPatterns(events: List<AuditLogger.AuditLogEntry>): List<SuspiciousPattern> {
        val patterns = mutableListOf<SuspiciousPattern>()

        // Temporal anomaly: Operations outside normal hours
        val nightTimeEvents = events.filter { event ->
            val hour = Calendar.getInstance().apply { timeInMillis = event.timestamp }.get(Calendar.HOUR_OF_DAY)
            hour < 6 || hour > 22 // Between 10 PM and 6 AM
        }

        if (nightTimeEvents.isNotEmpty()) {
            patterns.add(
                SuspiciousPattern(
                    patternId = "pattern_temporal_anomaly",
                    patternType = PatternType.TEMPORAL_ANOMALY,
                    severity = Severity.MEDIUM,
                    description = "${nightTimeEvents.size} operations during unusual hours",
                    evidenceEvents = nightTimeEvents.map { it.id },
                    indicators = mapOf("night_time_events" to nightTimeEvents.size)
                )
            )
        }

        // Frequency anomaly: Rapid succession of events
        val rapidEvents = events.zipWithNext().filter { (a, b) ->
            b.timestamp - a.timestamp < 1000 // Less than 1 second apart
        }

        if (rapidEvents.size >= 5) {
            patterns.add(
                SuspiciousPattern(
                    patternId = "pattern_frequency_anomaly",
                    patternType = PatternType.FREQUENCY_ANOMALY,
                    severity = Severity.HIGH,
                    description = "Unusually rapid event succession suggests automated attack",
                    evidenceEvents = rapidEvents.flatMap { listOf(it.first.id, it.second.id) }.distinct(),
                    indicators = mapOf("rapid_event_pairs" to rapidEvents.size)
                )
            )
        }

        return patterns
    }

    /**
     * Reconstructs attack chain from events.
     */
    private fun reconstructAttackChain(
        events: List<AuditLogger.AuditLogEntry>,
        clusters: List<EventCluster>
    ): List<AttackStage>? {
        // Only reconstruct if we have evidence of attack
        if (clusters.isEmpty()) return null

        val stages = mutableListOf<AttackStage>()

        // Check for credential access phase
        val credentialAccess = events.filter {
            it.event in listOf(
                AuditLogger.SecurityEvent.AUTH_LOGIN_FAILURE,
                AuditLogger.SecurityEvent.AUTH_BIOMETRIC_FAILURE
            )
        }

        if (credentialAccess.isNotEmpty()) {
            stages.add(
                AttackStage(
                    stage = AttackPhase.CREDENTIAL_ACCESS,
                    events = credentialAccess,
                    tactics = listOf("Brute Force", "Credential Dumping"),
                    description = "Attacker attempting to gain authentication credentials"
                )
            )
        }

        // Check for collection phase
        val collection = events.filter {
            it.event == AuditLogger.SecurityEvent.DATA_READ
        }

        if (collection.isNotEmpty()) {
            stages.add(
                AttackStage(
                    stage = AttackPhase.COLLECTION,
                    events = collection,
                    tactics = listOf("Data from Local System"),
                    description = "Attacker collecting data for exfiltration"
                )
            )
        }

        // Check for exfiltration phase
        val exfiltration = events.filter {
            it.event == AuditLogger.SecurityEvent.DATA_EXPORTED
        }

        if (exfiltration.isNotEmpty()) {
            stages.add(
                AttackStage(
                    stage = AttackPhase.EXFILTRATION,
                    events = exfiltration,
                    tactics = listOf("Exfiltration Over Alternative Protocol"),
                    description = "Attacker exfiltrating collected data"
                )
            )
        }

        return if (stages.isNotEmpty()) stages else null
    }

    /**
     * Assesses impact of events.
     */
    private fun assessImpact(
        events: List<AuditLogger.AuditLogEntry>,
        patterns: List<SuspiciousPattern>
    ): ImpactAssessment {
        var confidentiality = Impact.NONE
        var integrity = Impact.NONE
        var availability = Impact.NONE
        val affectedAssets = mutableSetOf<String>()

        events.forEach { event ->
            when (event.event) {
                AuditLogger.SecurityEvent.DATA_EXPORTED,
                AuditLogger.SecurityEvent.DATA_READ -> {
                    confidentiality = maxOf(confidentiality, Impact.HIGH)
                    affectedAssets.add("credentials_database")
                }

                AuditLogger.SecurityEvent.SECURITY_INTEGRITY_VIOLATION,
                AuditLogger.SecurityEvent.DATA_UPDATED,
                AuditLogger.SecurityEvent.DATA_DELETED -> {
                    integrity = maxOf(integrity, Impact.MEDIUM)
                }

                AuditLogger.SecurityEvent.SECURITY_ENCRYPTION_FAILURE -> {
                    availability = maxOf(availability, Impact.MEDIUM)
                    affectedAssets.add("encryption_system")
                }

                else -> {}
            }
        }

        val overallSeverity = when {
            confidentiality == Impact.HIGH || integrity == Impact.HIGH -> Severity.CRITICAL
            patterns.any { it.severity == Severity.HIGH } -> Severity.HIGH
            confidentiality == Impact.MEDIUM || integrity == Impact.MEDIUM -> Severity.MEDIUM
            else -> Severity.LOW
        }

        return ImpactAssessment(
            confidentialityImpact = confidentiality,
            integrityImpact = integrity,
            availabilityImpact = availability,
            affectedAssets = affectedAssets.toList(),
            estimatedDataRecords = null,
            overallSeverity = overallSeverity
        )
    }

    /**
     * Formats event description for timeline.
     */
    private fun formatEventDescription(event: AuditLogger.AuditLogEntry): String {
        return "${event.event.name}: ${event.action} on ${event.resource}"
    }

    /**
     * Assesses event significance.
     */
    private fun assessEventSignificance(event: AuditLogger.AuditLogEntry): EventSignificance {
        return when (event.severity) {
            AuditLogger.EventSeverity.CRITICAL -> EventSignificance.CRITICAL
            AuditLogger.EventSeverity.ERROR -> EventSignificance.SUSPICIOUS
            AuditLogger.EventSeverity.WARNING -> EventSignificance.NOTABLE
            else -> EventSignificance.ROUTINE
        }
    }

    /**
     * Formats timestamp for display.
     */
    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        return sdf.format(Date(timestamp))
    }

    /**
     * Formats duration for display.
     */
    private fun formatDuration(durationMs: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(durationMs)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs) % 60
        return "${hours}h ${minutes}m"
    }
}
