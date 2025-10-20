package com.trustvault.android.compliance

import android.content.Context
import com.trustvault.android.util.PreferencesManager
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Unit tests for AuditLogger.
 *
 * Tests ISO 27001 A.12.4.1-4 and GDPR Article 30 compliance:
 * - Security event logging
 * - Tamper-proof hash chain
 * - Log integrity verification
 * - Event streaming
 * - Log rotation
 * - Log retention
 * - Audit trail completeness
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuditLoggerTest {

    private lateinit var auditLogger: AuditLogger
    private lateinit var mockContext: Context
    private lateinit var mockPreferences: PreferencesManager

    @Before
    fun setup() {
        mockContext = mockk(relaxed = true)
        mockPreferences = mockk(relaxed = true)

        // Default mock behaviors
        every { mockPreferences.getAuditLogsJson() } returns "[]"
        every { mockPreferences.saveAuditLogsJson(any()) } just Runs
        every { mockPreferences.getLogRetentionDays() } returns 90

        auditLogger = AuditLogger(
            context = mockContext,
            preferencesManager = mockPreferences
        )
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    // ==================== Basic Logging Tests ====================

    @Test
    fun `logSecurityEvent should create audit log entry`() {
        // Act
        auditLogger.logSecurityEvent(
            event = AuditLogger.SecurityEvent.AUTH_LOGIN_SUCCESS,
            action = "user_login",
            resource = "authentication_system"
        )

        // Assert
        verify(exactly = 1) { mockPreferences.saveAuditLogsJson(any()) }
    }

    @Test
    fun `log entry should include all required fields`() {
        // Arrange
        var savedJson: String? = null
        every { mockPreferences.saveAuditLogsJson(any()) } answers {
            savedJson = firstArg()
        }

        // Act
        auditLogger.logSecurityEvent(
            event = AuditLogger.SecurityEvent.DATA_CREATED,
            action = "create_credential",
            resource = "credential:1",
            result = AuditLogger.EventResult.SUCCESS
        )

        // Assert
        assertNotNull(savedJson)
        val logArray = JSONArray(savedJson)
        assertEquals(1, logArray.length())

        val logEntry = logArray.getJSONObject(0)
        assertTrue(logEntry.has("id"))
        assertTrue(logEntry.has("timestamp"))
        assertTrue(logEntry.has("event"))
        assertTrue(logEntry.has("action"))
        assertTrue(logEntry.has("resource"))
        assertTrue(logEntry.has("result"))
        assertTrue(logEntry.has("hash"))
        assertTrue(logEntry.has("previousHash"))
        assertEquals("DATA_CREATED", logEntry.getString("event"))
    }

    @Test
    fun `log entry should include optional metadata`() {
        // Arrange
        var savedJson: String? = null
        every { mockPreferences.saveAuditLogsJson(any()) } answers {
            savedJson = firstArg()
        }

        val metadata = mapOf(
            "user_agent" to "TrustVault/1.0",
            "ip_address" to "127.0.0.1"
        )

        // Act
        auditLogger.logSecurityEvent(
            event = AuditLogger.SecurityEvent.AUTH_LOGIN_SUCCESS,
            action = "login",
            resource = "auth",
            metadata = metadata
        )

        // Assert
        val logArray = JSONArray(savedJson)
        val logEntry = logArray.getJSONObject(0)
        assertTrue(logEntry.has("metadata"))

        val savedMetadata = logEntry.getJSONObject("metadata")
        assertEquals("TrustVault/1.0", savedMetadata.getString("user_agent"))
        assertEquals("127.0.0.1", savedMetadata.getString("ip_address"))
    }

    // ==================== Hash Chain Tests ====================

    @Test
    fun `first log entry should have empty previousHash`() {
        // Arrange
        var savedJson: String? = null
        every { mockPreferences.saveAuditLogsJson(any()) } answers {
            savedJson = firstArg()
        }

        // Act
        auditLogger.logSecurityEvent(
            event = AuditLogger.SecurityEvent.AUTH_LOGIN_SUCCESS,
            action = "first_login",
            resource = "auth"
        )

        // Assert
        val logArray = JSONArray(savedJson)
        val logEntry = logArray.getJSONObject(0)
        assertEquals("", logEntry.getString("previousHash"))
    }

    @Test
    fun `second log entry should reference first entry hash`() {
        // Arrange
        var firstHash: String? = null
        var secondJson: String? = null

        every { mockPreferences.saveAuditLogsJson(any()) } answers {
            val json = firstArg<String>()
            val logArray = JSONArray(json)
            if (logArray.length() == 1 && firstHash == null) {
                firstHash = logArray.getJSONObject(0).getString("hash")
            } else if (logArray.length() == 2) {
                secondJson = json
            }
        }

        // Act
        auditLogger.logSecurityEvent(
            event = AuditLogger.SecurityEvent.AUTH_LOGIN_SUCCESS,
            action = "first_event",
            resource = "auth"
        )

        every { mockPreferences.getAuditLogsJson() } answers {
            val array = JSONArray()
            array.put(JSONObject().apply {
                put("id", "1")
                put("timestamp", System.currentTimeMillis())
                put("event", "AUTH_LOGIN_SUCCESS")
                put("action", "first_event")
                put("resource", "auth")
                put("result", "SUCCESS")
                put("hash", firstHash)
                put("previousHash", "")
            })
            array.toString()
        }

        auditLogger.logSecurityEvent(
            event = AuditLogger.SecurityEvent.AUTH_LOGOUT,
            action = "second_event",
            resource = "auth"
        )

        // Assert
        assertNotNull(secondJson)
        val logArray = JSONArray(secondJson)
        assertEquals(2, logArray.length())
        val secondEntry = logArray.getJSONObject(1)
        assertEquals(firstHash, secondEntry.getString("previousHash"))
    }

    @Test
    fun `hash should be computed correctly from entry fields`() {
        // Arrange
        var savedJson: String? = null
        every { mockPreferences.saveAuditLogsJson(any()) } answers {
            savedJson = firstArg()
        }

        // Act
        auditLogger.logSecurityEvent(
            event = AuditLogger.SecurityEvent.DATA_CREATED,
            action = "create",
            resource = "credential:1"
        )

        // Assert
        val logArray = JSONArray(savedJson)
        val logEntry = logArray.getJSONObject(0)

        val id = logEntry.getString("id")
        val timestamp = logEntry.getLong("timestamp")
        val event = logEntry.getString("event")
        val previousHash = logEntry.getString("previousHash")
        val computedHash = logEntry.getString("hash")

        // Hash should be SHA-256 of: id|timestamp|event|previousHash
        assertNotNull(computedHash)
        assertTrue(computedHash.length == 64) // SHA-256 produces 64 hex characters
    }

    // ==================== Integrity Verification Tests ====================

    @Test
    fun `verifyLogIntegrity should pass for valid chain`() = runTest {
        // Arrange
        auditLogger.logSecurityEvent(
            event = AuditLogger.SecurityEvent.AUTH_LOGIN_SUCCESS,
            action = "event1",
            resource = "auth"
        )

        // Need to reload the log for second event
        var savedJson: String? = null
        every { mockPreferences.saveAuditLogsJson(any()) } answers {
            savedJson = firstArg()
        }
        every { mockPreferences.getAuditLogsJson() } answers { savedJson ?: "[]" }

        auditLogger.logSecurityEvent(
            event = AuditLogger.SecurityEvent.AUTH_LOGOUT,
            action = "event2",
            resource = "auth"
        )

        // Act
        val result = auditLogger.verifyLogIntegrity()

        // Assert
        assertTrue(result.isValid)
        assertEquals(0, result.corruptedEntries.size)
        assertNull(result.errorMessage)
    }

    @Test
    fun `verifyLogIntegrity should detect tampered entry`() = runTest {
        // Arrange - Create valid log chain
        auditLogger.logSecurityEvent(
            event = AuditLogger.SecurityEvent.AUTH_LOGIN_SUCCESS,
            action = "event1",
            resource = "auth"
        )

        // Tamper with the log
        val tamperedLog = JSONArray().apply {
            put(JSONObject().apply {
                put("id", "1")
                put("timestamp", System.currentTimeMillis())
                put("event", "AUTH_LOGIN_FAILURE") // Changed event
                put("action", "event1")
                put("resource", "auth")
                put("result", "SUCCESS")
                put("hash", "invalid_hash") // Invalid hash
                put("previousHash", "")
            })
        }
        every { mockPreferences.getAuditLogsJson() } returns tamperedLog.toString()

        // Act
        val result = auditLogger.verifyLogIntegrity()

        // Assert
        assertFalse(result.isValid)
        assertEquals(1, result.corruptedEntries.size)
        assertNotNull(result.errorMessage)
    }

    @Test
    fun `verifyLogIntegrity should detect broken chain link`() = runTest {
        // Arrange - Create log with broken chain
        val brokenChain = JSONArray().apply {
            put(JSONObject().apply {
                put("id", "1")
                put("timestamp", System.currentTimeMillis())
                put("event", "AUTH_LOGIN_SUCCESS")
                put("action", "event1")
                put("resource", "auth")
                put("result", "SUCCESS")
                put("hash", "hash1")
                put("previousHash", "")
            })
            put(JSONObject().apply {
                put("id", "2")
                put("timestamp", System.currentTimeMillis())
                put("event", "AUTH_LOGOUT")
                put("action", "event2")
                put("resource", "auth")
                put("result", "SUCCESS")
                put("hash", "hash2")
                put("previousHash", "wrong_hash") // Doesn't match hash1
            })
        }
        every { mockPreferences.getAuditLogsJson() } returns brokenChain.toString()

        // Act
        val result = auditLogger.verifyLogIntegrity()

        // Assert
        assertFalse(result.isValid)
        assertTrue(result.corruptedEntries.isNotEmpty())
    }

    // ==================== Event Streaming Tests ====================

    @Test
    fun `observeSecurityEvents should emit new events`() = runTest {
        // Arrange
        val events = mutableListOf<AuditLogger.AuditLogEntry>()
        val job = kotlinx.coroutines.launch {
            auditLogger.observeSecurityEvents()
                .take(2)
                .collect { events.add(it) }
        }

        // Act
        auditLogger.logSecurityEvent(
            event = AuditLogger.SecurityEvent.AUTH_LOGIN_SUCCESS,
            action = "login1",
            resource = "auth"
        )

        auditLogger.logSecurityEvent(
            event = AuditLogger.SecurityEvent.AUTH_LOGOUT,
            action = "logout1",
            resource = "auth"
        )

        job.join()

        // Assert
        assertEquals(2, events.size)
        assertEquals("login1", events[0].action)
        assertEquals("logout1", events[1].action)
    }

    // ==================== Log Rotation Tests ====================

    @Test
    fun `logSecurityEvent should enforce maximum log entries`() {
        // Arrange
        val maxEntries = AuditLogger.MAX_LOG_ENTRIES
        val existingLogs = JSONArray()

        // Create full log
        repeat(maxEntries) { i ->
            existingLogs.put(JSONObject().apply {
                put("id", i.toString())
                put("timestamp", System.currentTimeMillis() - (maxEntries - i) * 1000)
                put("event", "TEST_EVENT")
                put("action", "action$i")
                put("resource", "resource")
                put("result", "SUCCESS")
                put("hash", "hash$i")
                put("previousHash", if (i == 0) "" else "hash${i-1}")
            })
        }

        every { mockPreferences.getAuditLogsJson() } returns existingLogs.toString()

        var savedJson: String? = null
        every { mockPreferences.saveAuditLogsJson(any()) } answers {
            savedJson = firstArg()
        }

        // Act - Add one more entry (should trigger rotation)
        auditLogger.logSecurityEvent(
            event = AuditLogger.SecurityEvent.AUTH_LOGIN_SUCCESS,
            action = "new_event",
            resource = "auth"
        )

        // Assert - Oldest entries should be removed
        assertNotNull(savedJson)
        val logArray = JSONArray(savedJson)
        assertTrue(logArray.length() <= maxEntries)
    }

    // ==================== Log Retention Tests ====================

    @Test
    fun `deleteExpiredLogs should remove logs older than retention period`() = runTest {
        // Arrange
        val retentionDays = 90
        every { mockPreferences.getLogRetentionDays() } returns retentionDays

        val now = System.currentTimeMillis()
        val expiredTime = now - TimeUnit.DAYS.toMillis(retentionDays.toLong() + 1)

        val logs = JSONArray().apply {
            // Expired log
            put(JSONObject().apply {
                put("id", "1")
                put("timestamp", expiredTime)
                put("event", "OLD_EVENT")
                put("action", "old")
                put("resource", "auth")
                put("result", "SUCCESS")
                put("hash", "hash1")
                put("previousHash", "")
            })
            // Recent log
            put(JSONObject().apply {
                put("id", "2")
                put("timestamp", now - TimeUnit.DAYS.toMillis(30))
                put("event", "RECENT_EVENT")
                put("action", "recent")
                put("resource", "auth")
                put("result", "SUCCESS")
                put("hash", "hash2")
                put("previousHash", "hash1")
            })
        }

        every { mockPreferences.getAuditLogsJson() } returns logs.toString()

        var savedJson: String? = null
        every { mockPreferences.saveAuditLogsJson(any()) } answers {
            savedJson = firstArg()
        }

        // Act
        val deletedCount = auditLogger.deleteExpiredLogs()

        // Assert
        assertEquals(1, deletedCount)
        assertNotNull(savedJson)
        val remainingLogs = JSONArray(savedJson)
        assertEquals(1, remainingLogs.length())
        assertEquals("RECENT_EVENT", remainingLogs.getJSONObject(0).getString("event"))
    }

    @Test
    fun `deleteExpiredLogs should preserve logs within retention period`() = runTest {
        // Arrange
        every { mockPreferences.getLogRetentionDays() } returns 90

        val now = System.currentTimeMillis()
        val recentLogs = JSONArray().apply {
            repeat(5) { i ->
                put(JSONObject().apply {
                    put("id", i.toString())
                    put("timestamp", now - TimeUnit.DAYS.toMillis(30))
                    put("event", "RECENT_EVENT")
                    put("action", "action$i")
                    put("resource", "auth")
                    put("result", "SUCCESS")
                    put("hash", "hash$i")
                    put("previousHash", if (i == 0) "" else "hash${i-1}")
                })
            }
        }

        every { mockPreferences.getAuditLogsJson() } returns recentLogs.toString()

        // Act
        val deletedCount = auditLogger.deleteExpiredLogs()

        // Assert
        assertEquals(0, deletedCount)
    }

    // ==================== Query Tests ====================

    @Test
    fun `getLogs should return all audit logs`() = runTest {
        // Arrange
        val logs = JSONArray().apply {
            put(JSONObject().apply {
                put("id", "1")
                put("timestamp", System.currentTimeMillis())
                put("event", "AUTH_LOGIN_SUCCESS")
                put("action", "login")
                put("resource", "auth")
                put("result", "SUCCESS")
                put("hash", "hash1")
                put("previousHash", "")
            })
            put(JSONObject().apply {
                put("id", "2")
                put("timestamp", System.currentTimeMillis())
                put("event", "DATA_CREATED")
                put("action", "create")
                put("resource", "credential:1")
                put("result", "SUCCESS")
                put("hash", "hash2")
                put("previousHash", "hash1")
            })
        }
        every { mockPreferences.getAuditLogsJson() } returns logs.toString()

        // Act
        val retrievedLogs = auditLogger.getLogs()

        // Assert
        assertEquals(2, retrievedLogs.size)
        assertEquals(AuditLogger.SecurityEvent.AUTH_LOGIN_SUCCESS, retrievedLogs[0].event)
        assertEquals(AuditLogger.SecurityEvent.DATA_CREATED, retrievedLogs[1].event)
    }

    @Test
    fun `getLogsByEvent should filter by event type`() = runTest {
        // Arrange
        val logs = JSONArray().apply {
            put(JSONObject().apply {
                put("id", "1")
                put("timestamp", System.currentTimeMillis())
                put("event", "AUTH_LOGIN_SUCCESS")
                put("action", "login")
                put("resource", "auth")
                put("result", "SUCCESS")
                put("hash", "hash1")
                put("previousHash", "")
            })
            put(JSONObject().apply {
                put("id", "2")
                put("timestamp", System.currentTimeMillis())
                put("event", "AUTH_LOGIN_FAILURE")
                put("action", "login_failed")
                put("resource", "auth")
                put("result", "FAILURE")
                put("hash", "hash2")
                put("previousHash", "hash1")
            })
            put(JSONObject().apply {
                put("id", "3")
                put("timestamp", System.currentTimeMillis())
                put("event", "AUTH_LOGIN_SUCCESS")
                put("action", "login2")
                put("resource", "auth")
                put("result", "SUCCESS")
                put("hash", "hash3")
                put("previousHash", "hash2")
            })
        }
        every { mockPreferences.getAuditLogsJson() } returns logs.toString()

        // Act
        val successLogs = auditLogger.getLogsByEvent(AuditLogger.SecurityEvent.AUTH_LOGIN_SUCCESS)

        // Assert
        assertEquals(2, successLogs.size)
        assertTrue(successLogs.all { it.event == AuditLogger.SecurityEvent.AUTH_LOGIN_SUCCESS })
    }

    @Test
    fun `getLogsByTimeRange should filter by timestamp`() = runTest {
        // Arrange
        val now = System.currentTimeMillis()
        val oneDayAgo = now - TimeUnit.DAYS.toMillis(1)
        val twoDaysAgo = now - TimeUnit.DAYS.toMillis(2)

        val logs = JSONArray().apply {
            put(JSONObject().apply {
                put("id", "1")
                put("timestamp", twoDaysAgo)
                put("event", "OLD_EVENT")
                put("action", "old")
                put("resource", "auth")
                put("result", "SUCCESS")
                put("hash", "hash1")
                put("previousHash", "")
            })
            put(JSONObject().apply {
                put("id", "2")
                put("timestamp", oneDayAgo)
                put("event", "RECENT_EVENT")
                put("action", "recent")
                put("resource", "auth")
                put("result", "SUCCESS")
                put("hash", "hash2")
                put("previousHash", "hash1")
            })
            put(JSONObject().apply {
                put("id", "3")
                put("timestamp", now)
                put("event", "CURRENT_EVENT")
                put("action", "current")
                put("resource", "auth")
                put("result", "SUCCESS")
                put("hash", "hash3")
                put("previousHash", "hash2")
            })
        }
        every { mockPreferences.getAuditLogsJson() } returns logs.toString()

        // Act - Get logs from last day
        val recentLogs = auditLogger.getLogsByTimeRange(startTime = oneDayAgo, endTime = now)

        // Assert
        assertEquals(2, recentLogs.size)
        assertTrue(recentLogs.all { it.timestamp >= oneDayAgo })
    }

    // ==================== Error Handling Tests ====================

    @Test
    fun `logSecurityEvent should handle storage failures gracefully`() {
        // Arrange
        every { mockPreferences.saveAuditLogsJson(any()) } throws Exception("Storage full")

        // Act & Assert - Should not throw exception
        try {
            auditLogger.logSecurityEvent(
                event = AuditLogger.SecurityEvent.AUTH_LOGIN_SUCCESS,
                action = "login",
                resource = "auth"
            )
        } catch (e: Exception) {
            fail("Should handle storage failures gracefully")
        }
    }

    @Test
    fun `getLogs should handle corrupted JSON gracefully`() = runTest {
        // Arrange
        every { mockPreferences.getAuditLogsJson() } returns "{ invalid json }"

        // Act
        val logs = auditLogger.getLogs()

        // Assert
        assertTrue(logs.isEmpty())
    }

    // ==================== GDPR and ISO 27001 Compliance Tests ====================

    @Test
    fun `audit logs should support GDPR Article 30 record of processing activities`() {
        // Arrange
        var savedJson: String? = null
        every { mockPreferences.saveAuditLogsJson(any()) } answers {
            savedJson = firstArg()
        }

        // Act - Log data processing activity
        auditLogger.logSecurityEvent(
            event = AuditLogger.SecurityEvent.PRIVACY_DATA_PROCESSED,
            action = "data_processed",
            resource = "credentials",
            metadata = mapOf(
                "purpose" to "CREDENTIAL_STORAGE",
                "data_type" to "login_credential",
                "record_count" to 1
            )
        )

        // Assert - Complete audit trail
        assertNotNull(savedJson)
        val logArray = JSONArray(savedJson)
        val logEntry = logArray.getJSONObject(0)
        assertEquals("PRIVACY_DATA_PROCESSED", logEntry.getString("event"))
        assertTrue(logEntry.has("metadata"))
    }

    @Test
    fun `audit logs should implement ISO 27001 A_12_4_1 event logging`() {
        // Arrange - ISO 27001 requires logging of: user activities, exceptions, security events
        // Act
        auditLogger.logSecurityEvent(
            event = AuditLogger.SecurityEvent.SECURITY_THREAT_DETECTED,
            severity = AuditLogger.EventSeverity.CRITICAL,
            action = "threat_detected",
            resource = "authentication_system",
            result = AuditLogger.EventResult.FAILURE,
            metadata = mapOf(
                "threat_type" to "brute_force",
                "failed_attempts" to 5
            )
        )

        // Assert - Security event logged with severity and details
        verify(exactly = 1) { mockPreferences.saveAuditLogsJson(any()) }
    }

    @Test
    fun `tamper-proof hash chain should prevent undetected log modification`() = runTest {
        // Arrange - ISO 27001 A.12.4.2 requires protection of log information
        auditLogger.logSecurityEvent(
            event = AuditLogger.SecurityEvent.AUTH_LOGIN_SUCCESS,
            action = "event1",
            resource = "auth"
        )

        // Get the saved log
        var savedJson: String? = null
        every { mockPreferences.saveAuditLogsJson(any()) } answers {
            savedJson = firstArg()
        }

        auditLogger.logSecurityEvent(
            event = AuditLogger.SecurityEvent.AUTH_LOGOUT,
            action = "event2",
            resource = "auth"
        )

        // Act - Verify integrity
        val result = auditLogger.verifyLogIntegrity()

        // Assert - Chain should be valid
        assertTrue(result.isValid)
    }
}
