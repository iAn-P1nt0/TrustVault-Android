package com.trustvault.android.compliance

import android.content.Context
import com.trustvault.android.util.PreferencesManager
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject

/**
 * Unit tests for ConsentManager.
 *
 * Tests GDPR Article 7 and DPDP Act Section 6 compliance:
 * - Consent recording
 * - Consent withdrawal
 * - Consent audit trail
 * - Version tracking
 * - JSON export/import
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConsentManagerTest {

    private lateinit var consentManager: ConsentManager
    private lateinit var mockContext: Context
    private lateinit var mockPreferences: PreferencesManager
    private lateinit var mockAuditLogger: AuditLogger

    @Before
    fun setup() {
        mockContext = mockk(relaxed = true)
        mockPreferences = mockk(relaxed = true)
        mockAuditLogger = mockk(relaxed = true)

        // Default mock behaviors
        every { mockPreferences.getConsentRecordsJson() } returns "[]"
        every { mockPreferences.saveConsentRecordsJson(any()) } just Runs

        consentManager = ConsentManager(
            context = mockContext,
            preferencesManager = mockPreferences,
            auditLogger = mockAuditLogger
        )
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    // ==================== Consent Recording Tests ====================

    @Test
    fun `recordConsent should create new consent record for first-time grant`() {
        // Arrange
        val purpose = DataProcessingPurpose.BIOMETRIC_AUTH
        val version = "1.0.0"

        // Act
        val result = consentManager.recordConsent(purpose, granted = true, version)

        // Assert
        assertTrue(result)
        verify(exactly = 1) { mockPreferences.saveConsentRecordsJson(any()) }
        verify(exactly = 1) { mockAuditLogger.logSecurityEvent(
            event = AuditLogger.SecurityEvent.PRIVACY_CONSENT_GRANTED,
            action = "consent_recorded",
            resource = "purpose:BIOMETRIC_AUTH",
            result = AuditLogger.EventResult.SUCCESS,
            metadata = match { it["version"] == version }
        )}
    }

    @Test
    fun `recordConsent should update existing consent record`() {
        // Arrange
        val purpose = DataProcessingPurpose.PASSWORD_ANALYSIS
        val existingConsent = JSONArray().apply {
            put(JSONObject().apply {
                put("purpose", purpose.name)
                put("granted", false)
                put("timestamp", System.currentTimeMillis() - 10000)
                put("version", "1.0.0")
                put("withdrawable", true)
            })
        }
        every { mockPreferences.getConsentRecordsJson() } returns existingConsent.toString()

        // Act
        val result = consentManager.recordConsent(purpose, granted = true, "1.1.0")

        // Assert
        assertTrue(result)
        val records = consentManager.getConsentRecords()
        assertEquals(1, records.size)
        assertTrue(records.first().granted)
        assertEquals("1.1.0", records.first().version)
    }

    @Test
    fun `recordConsent should not allow withdrawal of required purposes`() {
        // Arrange
        val requiredPurpose = DataProcessingPurpose.CREDENTIAL_STORAGE

        // Act
        val result = consentManager.recordConsent(requiredPurpose, granted = false)

        // Assert
        assertFalse(result)
        verify(exactly = 0) { mockPreferences.saveConsentRecordsJson(any()) }
    }

    @Test
    fun `recordConsent should handle version updates`() {
        // Arrange
        val purpose = DataProcessingPurpose.BIOMETRIC_AUTH

        // Act - Record v1.0.0
        consentManager.recordConsent(purpose, granted = true, "1.0.0")

        // Act - Update to v2.0.0 (e.g., privacy policy update)
        consentManager.recordConsent(purpose, granted = true, "2.0.0")

        // Assert
        val records = consentManager.getConsentRecords()
        val biometricConsent = records.find { it.purpose == purpose }
        assertNotNull(biometricConsent)
        assertEquals("2.0.0", biometricConsent?.version)
    }

    // ==================== Consent Withdrawal Tests ====================

    @Test
    fun `withdrawConsent should remove consent for optional purpose`() {
        // Arrange
        val purpose = DataProcessingPurpose.DIAGNOSTIC_LOGGING
        val existingConsent = JSONArray().apply {
            put(JSONObject().apply {
                put("purpose", purpose.name)
                put("granted", true)
                put("timestamp", System.currentTimeMillis())
                put("version", "1.0.0")
                put("withdrawable", true)
            })
        }
        every { mockPreferences.getConsentRecordsJson() } returns existingConsent.toString()

        // Act
        val result = consentManager.withdrawConsent(purpose)

        // Assert
        assertTrue(result)
        val records = consentManager.getConsentRecords()
        val withdrawnConsent = records.find { it.purpose == purpose }
        assertNotNull(withdrawnConsent)
        assertFalse(withdrawnConsent!!.granted)
        verify(exactly = 1) { mockAuditLogger.logSecurityEvent(
            event = AuditLogger.SecurityEvent.PRIVACY_CONSENT_WITHDRAWN,
            action = "consent_withdrawn",
            resource = "purpose:DIAGNOSTIC_LOGGING",
            result = AuditLogger.EventResult.SUCCESS,
            metadata = any()
        )}
    }

    @Test
    fun `withdrawConsent should fail for required purposes`() {
        // Arrange
        val requiredPurpose = DataProcessingPurpose.CREDENTIAL_STORAGE

        // Act
        val result = consentManager.withdrawConsent(requiredPurpose)

        // Assert
        assertFalse(result)
        verify(exactly = 0) { mockPreferences.saveConsentRecordsJson(any()) }
    }

    @Test
    fun `withdrawConsent should fail for non-existent consent`() {
        // Arrange
        val purpose = DataProcessingPurpose.OCR_CAPTURE
        every { mockPreferences.getConsentRecordsJson() } returns "[]"

        // Act
        val result = consentManager.withdrawConsent(purpose)

        // Assert
        assertFalse(result)
    }

    // ==================== Consent Query Tests ====================

    @Test
    fun `hasConsent should return true for granted consent`() {
        // Arrange
        val purpose = DataProcessingPurpose.AUTOFILL_SERVICE
        val consent = JSONArray().apply {
            put(JSONObject().apply {
                put("purpose", purpose.name)
                put("granted", true)
                put("timestamp", System.currentTimeMillis())
                put("version", "1.0.0")
                put("withdrawable", true)
            })
        }
        every { mockPreferences.getConsentRecordsJson() } returns consent.toString()

        // Act
        val result = consentManager.hasConsent(purpose)

        // Assert
        assertTrue(result)
    }

    @Test
    fun `hasConsent should return false for withdrawn consent`() {
        // Arrange
        val purpose = DataProcessingPurpose.PASSWORD_ANALYSIS
        val consent = JSONArray().apply {
            put(JSONObject().apply {
                put("purpose", purpose.name)
                put("granted", false)
                put("timestamp", System.currentTimeMillis())
                put("version", "1.0.0")
                put("withdrawable", true)
            })
        }
        every { mockPreferences.getConsentRecordsJson() } returns consent.toString()

        // Act
        val result = consentManager.hasConsent(purpose)

        // Assert
        assertFalse(result)
    }

    @Test
    fun `hasConsent should return false for missing consent record`() {
        // Arrange
        every { mockPreferences.getConsentRecordsJson() } returns "[]"

        // Act
        val result = consentManager.hasConsent(DataProcessingPurpose.OCR_CAPTURE)

        // Assert
        assertFalse(result)
    }

    @Test
    fun `getConsentRecord should return specific consent details`() {
        // Arrange
        val purpose = DataProcessingPurpose.BACKUP_CREATION
        val timestamp = System.currentTimeMillis()
        val consent = JSONArray().apply {
            put(JSONObject().apply {
                put("purpose", purpose.name)
                put("granted", true)
                put("timestamp", timestamp)
                put("version", "1.0.0")
                put("withdrawable", true)
            })
        }
        every { mockPreferences.getConsentRecordsJson() } returns consent.toString()

        // Act
        val record = consentManager.getConsentRecord(purpose)

        // Assert
        assertNotNull(record)
        assertEquals(purpose, record?.purpose)
        assertTrue(record!!.granted)
        assertEquals(timestamp, record.timestamp)
        assertEquals("1.0.0", record.version)
        assertTrue(record.withdrawable)
    }

    @Test
    fun `getConsentRecords should return all consent records`() {
        // Arrange
        val consents = JSONArray().apply {
            put(JSONObject().apply {
                put("purpose", DataProcessingPurpose.BIOMETRIC_AUTH.name)
                put("granted", true)
                put("timestamp", System.currentTimeMillis())
                put("version", "1.0.0")
                put("withdrawable", true)
            })
            put(JSONObject().apply {
                put("purpose", DataProcessingPurpose.PASSWORD_ANALYSIS.name)
                put("granted", false)
                put("timestamp", System.currentTimeMillis() - 5000)
                put("version", "1.0.0")
                put("withdrawable", true)
            })
        }
        every { mockPreferences.getConsentRecordsJson() } returns consents.toString()

        // Act
        val records = consentManager.getConsentRecords()

        // Assert
        assertEquals(2, records.size)
        assertTrue(records.any { it.purpose == DataProcessingPurpose.BIOMETRIC_AUTH && it.granted })
        assertTrue(records.any { it.purpose == DataProcessingPurpose.PASSWORD_ANALYSIS && !it.granted })
    }

    // ==================== Required vs Optional Purpose Tests ====================

    @Test
    fun `isRequiredPurpose should return true for mandatory purposes`() {
        // Act & Assert
        assertTrue(consentManager.isRequiredPurpose(DataProcessingPurpose.CREDENTIAL_STORAGE))
        assertFalse(consentManager.isRequiredPurpose(DataProcessingPurpose.BIOMETRIC_AUTH))
        assertFalse(consentManager.isRequiredPurpose(DataProcessingPurpose.PASSWORD_ANALYSIS))
    }

    @Test
    fun `isWithdrawable should return false for required purposes`() {
        // Arrange
        val purpose = DataProcessingPurpose.CREDENTIAL_STORAGE

        // Act
        val withdrawable = consentManager.isWithdrawable(purpose)

        // Assert
        assertFalse(withdrawable)
    }

    @Test
    fun `isWithdrawable should return true for optional purposes`() {
        // Arrange
        val purpose = DataProcessingPurpose.BIOMETRIC_AUTH

        // Act
        val withdrawable = consentManager.isWithdrawable(purpose)

        // Assert
        assertTrue(withdrawable)
    }

    // ==================== Export Tests ====================

    @Test
    fun `exportConsentRecords should return JSON string of all consents`() {
        // Arrange
        val timestamp = System.currentTimeMillis()
        val consents = JSONArray().apply {
            put(JSONObject().apply {
                put("purpose", DataProcessingPurpose.BIOMETRIC_AUTH.name)
                put("granted", true)
                put("timestamp", timestamp)
                put("version", "1.0.0")
                put("withdrawable", true)
            })
        }
        every { mockPreferences.getConsentRecordsJson() } returns consents.toString()

        // Act
        val exportedJson = consentManager.exportConsentRecords()

        // Assert
        assertNotNull(exportedJson)
        assertTrue(exportedJson.contains("BIOMETRIC_AUTH"))
        assertTrue(exportedJson.contains("\"granted\":true"))
        assertTrue(exportedJson.contains("1.0.0"))
    }

    @Test
    fun `exportConsentRecords should return empty array for no consents`() {
        // Arrange
        every { mockPreferences.getConsentRecordsJson() } returns "[]"

        // Act
        val exportedJson = consentManager.exportConsentRecords()

        // Assert
        assertEquals("[]", exportedJson)
    }

    // ==================== Bulk Operations Tests ====================

    @Test
    fun `revokeAllOptionalConsents should withdraw all optional purposes`() {
        // Arrange
        val consents = JSONArray().apply {
            put(JSONObject().apply {
                put("purpose", DataProcessingPurpose.CREDENTIAL_STORAGE.name)
                put("granted", true)
                put("timestamp", System.currentTimeMillis())
                put("version", "1.0.0")
                put("withdrawable", false)
            })
            put(JSONObject().apply {
                put("purpose", DataProcessingPurpose.BIOMETRIC_AUTH.name)
                put("granted", true)
                put("timestamp", System.currentTimeMillis())
                put("version", "1.0.0")
                put("withdrawable", true)
            })
            put(JSONObject().apply {
                put("purpose", DataProcessingPurpose.PASSWORD_ANALYSIS.name)
                put("granted", true)
                put("timestamp", System.currentTimeMillis())
                put("version", "1.0.0")
                put("withdrawable", true)
            })
        }
        every { mockPreferences.getConsentRecordsJson() } returns consents.toString()

        // Act
        val revokedCount = consentManager.revokeAllOptionalConsents()

        // Assert
        assertEquals(2, revokedCount) // BIOMETRIC_AUTH and PASSWORD_ANALYSIS
        val records = consentManager.getConsentRecords()
        assertTrue(records.find { it.purpose == DataProcessingPurpose.CREDENTIAL_STORAGE }?.granted == true)
        assertFalse(records.find { it.purpose == DataProcessingPurpose.BIOMETRIC_AUTH }?.granted == true)
        assertFalse(records.find { it.purpose == DataProcessingPurpose.PASSWORD_ANALYSIS }?.granted == true)
    }

    // ==================== Edge Cases and Error Handling ====================

    @Test
    fun `getConsentRecords should handle corrupted JSON gracefully`() {
        // Arrange
        every { mockPreferences.getConsentRecordsJson() } returns "{ invalid json }"

        // Act
        val records = consentManager.getConsentRecords()

        // Assert
        assertTrue(records.isEmpty())
    }

    @Test
    fun `recordConsent should handle PreferencesManager save failure`() {
        // Arrange
        every { mockPreferences.saveConsentRecordsJson(any()) } throws Exception("Storage full")

        // Act
        val result = consentManager.recordConsent(
            DataProcessingPurpose.BIOMETRIC_AUTH,
            granted = true
        )

        // Assert
        assertFalse(result)
    }

    @Test
    fun `withdrawConsent should handle missing purpose gracefully`() {
        // Arrange
        every { mockPreferences.getConsentRecordsJson() } returns "[]"

        // Act
        val result = consentManager.withdrawConsent(DataProcessingPurpose.OCR_CAPTURE)

        // Assert
        assertFalse(result)
    }

    @Test
    fun `hasConsent should handle null timestamp`() {
        // Arrange
        val consent = JSONArray().apply {
            put(JSONObject().apply {
                put("purpose", DataProcessingPurpose.BIOMETRIC_AUTH.name)
                put("granted", true)
                // Missing timestamp
                put("version", "1.0.0")
                put("withdrawable", true)
            })
        }
        every { mockPreferences.getConsentRecordsJson() } returns consent.toString()

        // Act
        val result = consentManager.hasConsent(DataProcessingPurpose.BIOMETRIC_AUTH)

        // Assert - Should still work with missing timestamp
        assertTrue(result)
    }

    // ==================== Audit Trail Tests ====================

    @Test
    fun `consent changes should create audit trail`() {
        // Arrange
        val purpose = DataProcessingPurpose.AUTOFILL_SERVICE

        // Act
        consentManager.recordConsent(purpose, granted = true)
        consentManager.withdrawConsent(purpose)

        // Assert
        verify(exactly = 1) { mockAuditLogger.logSecurityEvent(
            event = AuditLogger.SecurityEvent.PRIVACY_CONSENT_GRANTED,
            action = "consent_recorded",
            resource = "purpose:AUTOFILL_SERVICE",
            result = AuditLogger.EventResult.SUCCESS,
            metadata = any()
        )}
        verify(exactly = 1) { mockAuditLogger.logSecurityEvent(
            event = AuditLogger.SecurityEvent.PRIVACY_CONSENT_WITHDRAWN,
            action = "consent_withdrawn",
            resource = "purpose:AUTOFILL_SERVICE",
            result = AuditLogger.EventResult.SUCCESS,
            metadata = any()
        )}
    }

    // ==================== GDPR Compliance Tests ====================

    @Test
    fun `consent records should include all GDPR Article 7 requirements`() {
        // Arrange
        val purpose = DataProcessingPurpose.BIOMETRIC_AUTH
        val version = "1.0.0"

        // Act
        consentManager.recordConsent(purpose, granted = true, version)
        val record = consentManager.getConsentRecord(purpose)

        // Assert - GDPR Article 7 requirements
        assertNotNull(record)
        assertNotNull(record!!.timestamp) // Verifiable record
        assertTrue(record.withdrawable) // Right to withdraw
        assertEquals(version, record.version) // Version tracking
        assertEquals(purpose, record.purpose) // Clear purpose
    }
}
