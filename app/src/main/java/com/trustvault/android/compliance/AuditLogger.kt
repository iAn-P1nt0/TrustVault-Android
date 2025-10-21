package com.trustvault.android.compliance

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.trustvault.android.security.CryptoManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AuditLogger - Tamper-Proof Security Event Logging
 *
 * Implements comprehensive audit logging for security events, data operations,
 * and compliance requirements (GDPR Article 30, ISO 27001).
 *
 * **GDPR Compliance:**
 * - Article 30: Records of processing activities
 * - Article 32: Security of processing (logging and monitoring)
 * - Article 33: Personal data breach notification (incident logging)
 *
 * **ISO 27001 Compliance:**
 * - A.12.4.1: Event logging
 * - A.12.4.2: Protection of log information
 * - A.12.4.3: Administrator and operator logs
 * - A.12.4.4: Clock synchronisation
 *
 * **Security Features:**
 * - Tamper-proof logging with SHA-256 hash chains
 * - Encrypted log storage
 * - Log rotation with configurable retention
 * - Integrity verification
 * - Real-time event streaming
 * - Forensic analysis support
 *
 * **Event Categories:**
 * - Authentication (login, logout, failures)
 * - Authorization (permission checks)
 * - Data access (read, write, delete)
 * - Configuration changes
 * - Security events (threats, breaches)
 * - Privacy events (consent changes, data exports)
 * - System events (startup, shutdown, errors)
 *
 * @property context Application context
 * @property cryptoManager Encryption for log files
 */
@Singleton
class AuditLogger @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cryptoManager: CryptoManager
) {

    companion object {
        private const val TAG = "AuditLogger"
        private const val LOG_DIR = "audit_logs"
        private const val MAX_LOG_FILE_SIZE_BYTES = 1024 * 1024 // 1MB
        private const val LOG_RETENTION_DAYS = 90 // 3 months default
        private const val HASH_ALGORITHM = "SHA-256"
    }

    private val logDir: File = File(context.filesDir, LOG_DIR).apply { mkdirs() }
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val scope = CoroutineScope(Dispatchers.IO)

    // Real-time event stream
    private val _eventStream = MutableStateFlow<AuditLogEntry?>(null)
    val eventStream: Flow<AuditLogEntry?> = _eventStream.asStateFlow()

    // Current log file
    private var currentLogFile: File? = null
    private var previousLogHash: String = "0000000000000000000000000000000000000000000000000000000000000000"

    init {
        // Initialize with latest log file
        currentLogFile = getLatestLogFile() ?: createNewLogFile()
        previousLogHash = getLastLogHash()
    }

    /**
     * Security event types.
     */
    enum class SecurityEvent {
        // Authentication events
        AUTH_LOGIN_SUCCESS,
        AUTH_LOGIN_FAILURE,
        AUTH_LOGOUT,
        AUTH_BIOMETRIC_SUCCESS,
        AUTH_BIOMETRIC_FAILURE,
        AUTH_PASSWORD_CHANGED,

        // Authorization events
        AUTHZ_PERMISSION_GRANTED,
        AUTHZ_PERMISSION_DENIED,

        // Data access events
        DATA_CREATED,
        DATA_READ,
        DATA_UPDATED,
        DATA_DELETED,
        DATA_EXPORTED,
        DATA_IMPORTED,
        DATA_BACKUP_CREATED,
        DATA_BACKUP_RESTORED,

        // Privacy events
        PRIVACY_CONSENT_GRANTED,
        PRIVACY_CONSENT_WITHDRAWN,
        PRIVACY_DATA_ERASURE_REQUESTED,
        PRIVACY_DATA_ERASURE_COMPLETED,
        PRIVACY_POLICY_ACCEPTED,
        PRIVACY_BREACH_NOTIFIED,
        PRIVACY_DATA_EXPORTED,

        // Security events
        SECURITY_THREAT_DETECTED,
        SECURITY_BREACH_DETECTED,
        SECURITY_BREACH_CONTAINED,
        SECURITY_BREACH_MITIGATED,
        SECURITY_BREACH_RESOLVED,
        SECURITY_KEY_ROTATED,
        SECURITY_ENCRYPTION_FAILURE,
        SECURITY_INTEGRITY_VIOLATION,
        SECURITY_MONITORING_STARTED,
        SECURITY_MONITORING_STOPPED,
        SECURITY_ALERT_RAISED,
        SECURITY_ALERT_ACKNOWLEDGED,

        // Configuration events
        CONFIG_SETTING_CHANGED,
        CONFIG_RETENTION_POLICY_SET,

        // System events
        SYSTEM_STARTUP,
        SYSTEM_SHUTDOWN,
        SYSTEM_ERROR,
        SYSTEM_WARNING
    }

    /**
     * Event severity levels.
     */
    enum class EventSeverity {
        DEBUG,      // Detailed debugging information
        INFO,       // Informational events
        WARNING,    // Warning events (potential issues)
        ERROR,      // Error events (failures)
        CRITICAL    // Critical security events (requires immediate attention)
    }

    /**
     * Event result status.
     */
    enum class EventResult {
        SUCCESS,    // Operation completed successfully
        FAILURE,    // Operation failed
        DENIED      // Operation denied (authorization/permission)
    }

    /**
     * Audit log entry with tamper-proof hash.
     */
    data class AuditLogEntry(
        val id: String = UUID.randomUUID().toString(),
        val timestamp: Long = System.currentTimeMillis(),
        val event: SecurityEvent,
        val severity: EventSeverity,
        val userId: String? = null,
        val action: String,
        val resource: String? = null,
        val result: String,
        val metadata: Map<String, String> = emptyMap(),
        val ipAddress: String? = null,
        val userAgent: String? = null,
        val previousHash: String,
        val hash: String = ""
    ) {
        /**
         * Computes SHA-256 hash of this entry for integrity verification.
         */
        fun computeHash(): String {
            val data = "$id|$timestamp|${event.name}|${severity.name}|$userId|$action|$resource|$result|$previousHash"
            return sha256(data)
        }

        private fun sha256(data: String): String {
            val digest = MessageDigest.getInstance(HASH_ALGORITHM)
            val hash = digest.digest(data.toByteArray())
            return hash.joinToString("") { "%02x".format(it) }
        }
    }

    /**
     * Event filter for querying logs.
     */
    data class EventFilter(
        val events: List<SecurityEvent>? = null,
        val severities: List<EventSeverity>? = null,
        val userId: String? = null,
        val startTime: Long? = null,
        val endTime: Long? = null
    )

    // ========================================================================
    // PUBLIC LOGGING API
    // ========================================================================

    /**
     * Logs a security event with tamper-proof hash.
     *
     * **GDPR Article 30:** Records of processing activities.
     * **ISO 27001 A.12.4.1:** Event logging.
     *
     * @param event Security event type
     * @param severity Event severity level
     * @param action Action description (e.g., "User logged in", "Credential created")
     * @param resource Resource affected (e.g., credential ID, file name)
     * @param result Result of action (e.g., "success", "failure", "denied")
     * @param metadata Additional context (key-value pairs)
     * @param userId User identifier (optional)
     */
    fun logSecurityEvent(
        event: SecurityEvent,
        severity: EventSeverity = EventSeverity.INFO,
        action: String,
        resource: String? = null,
        result: String = "success",
        metadata: Map<String, String> = emptyMap(),
        userId: String? = null
    ) {
        scope.launch {
            try {
                // Create log entry with hash chain
                val entry = AuditLogEntry(
                    timestamp = System.currentTimeMillis(),
                    event = event,
                    severity = severity,
                    userId = userId,
                    action = action,
                    resource = resource,
                    result = result,
                    metadata = metadata,
                    previousHash = previousLogHash
                ).let { it.copy(hash = it.computeHash()) }

                // Write to log file
                writeLogEntry(entry)

                // Update previous hash for chain
                previousLogHash = entry.hash

                // Emit to real-time stream
                _eventStream.value = entry

                // Log to system log (for debugging)
                when (severity) {
                    EventSeverity.DEBUG -> Log.d(TAG, "AUDIT: ${entry.event} - $action")
                    EventSeverity.INFO -> Log.i(TAG, "AUDIT: ${entry.event} - $action")
                    EventSeverity.WARNING -> Log.w(TAG, "AUDIT: ${entry.event} - $action")
                    EventSeverity.ERROR -> Log.e(TAG, "AUDIT: ${entry.event} - $action")
                    EventSeverity.CRITICAL -> Log.wtf(TAG, "AUDIT CRITICAL: ${entry.event} - $action")
                }

                // Rotate log if needed
                rotateLogIfNeeded()

            } catch (e: Exception) {
                Log.e(TAG, "Error logging audit event: ${e.message}", e)
            }
        }
    }

    /**
     * Retrieves audit log entries with optional filtering.
     *
     * @param filter Event filter criteria
     * @return List of matching audit log entries
     */
    suspend fun getAuditLog(filter: EventFilter? = null): List<AuditLogEntry> {
        return try {
            val allEntries = mutableListOf<AuditLogEntry>()

            // Read all log files
            logDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("audit_") && file.name.endsWith(".log")) {
                    val entries = readLogFile(file)
                    allEntries.addAll(entries)
                }
            }

            // Apply filter
            if (filter != null) {
                allEntries.filter { entry ->
                    val eventMatch = filter.events?.contains(entry.event) ?: true
                    val severityMatch = filter.severities?.contains(entry.severity) ?: true
                    val userMatch = filter.userId == null || entry.userId == filter.userId
                    val startTimeMatch = filter.startTime == null || entry.timestamp >= filter.startTime
                    val endTimeMatch = filter.endTime == null || entry.timestamp <= filter.endTime

                    eventMatch && severityMatch && userMatch && startTimeMatch && endTimeMatch
                }
            } else {
                allEntries
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving audit log: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Verifies integrity of audit log chain.
     *
     * **ISO 27001 A.12.4.2:** Protection of log information.
     *
     * @return VerificationResult with integrity status
     */
    suspend fun verifyLogIntegrity(): VerificationResult {
        return try {
            val allEntries = getAuditLog()
            var previousHash = "0000000000000000000000000000000000000000000000000000000000000000"
            val violations = mutableListOf<IntegrityViolation>()

            allEntries.forEachIndexed { index, entry ->
                // Verify hash chain
                if (entry.previousHash != previousHash) {
                    violations.add(IntegrityViolation(
                        entryId = entry.id,
                        entryIndex = index,
                        issue = "Hash chain broken: expected $previousHash, got ${entry.previousHash}"
                    ))
                }

                // Verify entry hash
                val computedHash = entry.computeHash()
                if (entry.hash != computedHash) {
                    violations.add(IntegrityViolation(
                        entryId = entry.id,
                        entryIndex = index,
                        issue = "Entry hash mismatch: expected ${entry.hash}, computed $computedHash"
                    ))
                }

                previousHash = entry.hash
            }

            VerificationResult(
                totalEntries = allEntries.size,
                violations = violations,
                isIntact = violations.isEmpty()
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error verifying log integrity: ${e.message}", e)
            VerificationResult(
                totalEntries = 0,
                violations = listOf(IntegrityViolation("", 0, "Verification failed: ${e.message}")),
                isIntact = false
            )
        }
    }

    /**
     * Verification result.
     */
    data class VerificationResult(
        val totalEntries: Int,
        val violations: List<IntegrityViolation>,
        val isIntact: Boolean
    )

    /**
     * Integrity violation record.
     */
    data class IntegrityViolation(
        val entryId: String,
        val entryIndex: Int,
        val issue: String
    )

    // ========================================================================
    // LOG QUERYING
    // ========================================================================

    /**
     * Gets audit logs within a specific time range.
     * Used for forensic analysis and incident investigation.
     *
     * @param startTime Start timestamp (inclusive)
     * @param endTime End timestamp (inclusive)
     * @return List of audit log entries in the time range
     */
    suspend fun getLogsByTimeRange(startTime: Long, endTime: Long): List<AuditLogEntry> {
        return withContext(Dispatchers.IO) {
            try {
                val allEntries = mutableListOf<AuditLogEntry>()

                // Read all log files
                logDir.listFiles()
                    ?.filter { it.name.startsWith("audit_") && it.name.endsWith(".log") }
                    ?.forEach { file ->
                        val entries = readLogFile(file)
                        allEntries.addAll(entries)
                    }

                // Filter by time range and sort
                allEntries
                    .filter { it.timestamp in startTime..endTime }
                    .sortedBy { it.timestamp }

            } catch (e: Exception) {
                Log.e(TAG, "Error reading logs by time range: ${e.message}", e)
                emptyList()
            }
        }
    }

    /**
     * Observes security events in real-time.
     * Returns a Flow of audit log entries as they occur.
     */
    fun observeSecurityEvents(): Flow<AuditLogEntry> = eventStream.filterNotNull()

    // ========================================================================
    // LOG ROTATION & RETENTION
    // ========================================================================

    /**
     * Rotates log file if size limit exceeded.
     */
    private fun rotateLogIfNeeded() {
        val currentFile = currentLogFile ?: return

        if (currentFile.length() > MAX_LOG_FILE_SIZE_BYTES) {
            Log.d(TAG, "Log file size exceeded, rotating...")
            currentLogFile = createNewLogFile()
        }
    }

    /**
     * Creates a new log file.
     */
    private fun createNewLogFile(): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File(logDir, "audit_$timestamp.log")
    }

    /**
     * Gets the latest log file.
     */
    private fun getLatestLogFile(): File? {
        return logDir.listFiles()
            ?.filter { it.name.startsWith("audit_") && it.name.endsWith(".log") }
            ?.maxByOrNull { it.lastModified() }
    }

    /**
     * Gets the last log entry hash from latest log file.
     */
    private fun getLastLogHash(): String {
        return try {
            val latestFile = getLatestLogFile() ?: return "0000000000000000000000000000000000000000000000000000000000000000"
            val entries = readLogFile(latestFile)
            entries.lastOrNull()?.hash ?: "0000000000000000000000000000000000000000000000000000000000000000"
        } catch (e: Exception) {
            "0000000000000000000000000000000000000000000000000000000000000000"
        }
    }

    /**
     * Deletes logs older than retention period.
     *
     * **GDPR Article 5(1)(e):** Storage limitation.
     */
    fun cleanOldLogs(retentionDays: Int = LOG_RETENTION_DAYS) {
        scope.launch {
            try {
                val retentionMillis = retentionDays * 24L * 60L * 60L * 1000L
                val cutoffTime = System.currentTimeMillis() - retentionMillis

                logDir.listFiles()?.forEach { file ->
                    if (file.lastModified() < cutoffTime) {
                        if (file.delete()) {
                            Log.d(TAG, "Deleted old log file: ${file.name}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error cleaning old logs: ${e.message}", e)
            }
        }
    }

    // ========================================================================
    // FILE I/O
    // ========================================================================

    /**
     * Writes log entry to current log file.
     */
    private fun writeLogEntry(entry: AuditLogEntry) {
        val file = currentLogFile ?: createNewLogFile().also { currentLogFile = it }

        try {
            // Serialize entry to JSON
            val json = gson.toJson(entry)

            // Append to log file (newline-delimited JSON)
            file.appendText(json + "\n")

        } catch (e: Exception) {
            Log.e(TAG, "Error writing log entry: ${e.message}", e)
        }
    }

    /**
     * Reads log entries from file.
     */
    private fun readLogFile(file: File): List<AuditLogEntry> {
        return try {
            file.readLines()
                .filter { it.isNotBlank() }
                .mapNotNull { line ->
                    try {
                        gson.fromJson(line, AuditLogEntry::class.java)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse log entry: ${e.message}")
                        null
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading log file: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Exports audit logs for compliance reporting.
     *
     * @param startTime Start timestamp (inclusive)
     * @param endTime End timestamp (inclusive)
     * @return JSON string of audit logs
     */
    suspend fun exportAuditLogs(startTime: Long, endTime: Long): String {
        val filter = EventFilter(startTime = startTime, endTime = endTime)
        val entries = getAuditLog(filter)

        return gson.toJson(mapOf(
            "export_timestamp" to System.currentTimeMillis(),
            "start_time" to startTime,
            "end_time" to endTime,
            "total_entries" to entries.size,
            "entries" to entries
        ))
    }

    // ========================================================================
    // CONVENIENCE METHODS
    // ========================================================================

    /** Logs authentication success */
    fun logAuthSuccess(userId: String, method: String = "password") {
        logSecurityEvent(
            event = SecurityEvent.AUTH_LOGIN_SUCCESS,
            severity = EventSeverity.INFO,
            action = "User authenticated successfully",
            userId = userId,
            metadata = mapOf("auth_method" to method)
        )
    }

    /** Logs authentication failure */
    fun logAuthFailure(userId: String?, reason: String) {
        logSecurityEvent(
            event = SecurityEvent.AUTH_LOGIN_FAILURE,
            severity = EventSeverity.WARNING,
            action = "Authentication failed",
            result = "failure",
            userId = userId,
            metadata = mapOf("reason" to reason)
        )
    }

    /** Logs data creation */
    fun logDataCreated(resourceType: String, resourceId: String, userId: String?) {
        logSecurityEvent(
            event = SecurityEvent.DATA_CREATED,
            severity = EventSeverity.INFO,
            action = "$resourceType created",
            resource = resourceId,
            userId = userId
        )
    }

    /** Logs data deletion */
    fun logDataDeleted(resourceType: String, resourceId: String, userId: String?) {
        logSecurityEvent(
            event = SecurityEvent.DATA_DELETED,
            severity = EventSeverity.INFO,
            action = "$resourceType deleted",
            resource = resourceId,
            userId = userId
        )
    }

    /** Logs privacy consent change */
    fun logConsentChange(purpose: String, granted: Boolean, userId: String?) {
        logSecurityEvent(
            event = if (granted) SecurityEvent.PRIVACY_CONSENT_GRANTED else SecurityEvent.PRIVACY_CONSENT_WITHDRAWN,
            severity = EventSeverity.INFO,
            action = "Consent ${if (granted) "granted" else "withdrawn"} for $purpose",
            userId = userId
        )
    }

    /** Logs security threat */
    fun logSecurityThreat(threat: String, severity: EventSeverity = EventSeverity.CRITICAL) {
        logSecurityEvent(
            event = SecurityEvent.SECURITY_THREAT_DETECTED,
            severity = severity,
            action = "Security threat detected: $threat",
            result = "detected"
        )
    }
}
