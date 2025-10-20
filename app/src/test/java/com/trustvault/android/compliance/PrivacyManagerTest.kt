package com.trustvault.android.compliance

import android.content.Context
import com.trustvault.android.domain.model.Credential
import com.trustvault.android.domain.model.CredentialCategory
import com.trustvault.android.domain.repository.CredentialRepository
import com.trustvault.android.util.PreferencesManager
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for PrivacyManager.
 *
 * Tests GDPR and DPDP Act compliance features:
 * - Consent management
 * - Data processing tracking
 * - Privacy dashboard data
 * - Data retention policies
 * - Right to erasure
 * - Data portability
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PrivacyManagerTest {

    private lateinit var privacyManager: PrivacyManager
    private lateinit var mockContext: Context
    private lateinit var mockConsentManager: ConsentManager
    private lateinit var mockDataRetentionManager: DataRetentionManager
    private lateinit var mockDataErasure: DataErasure
    private lateinit var mockDataPortability: DataPortability
    private lateinit var mockAuditLogger: AuditLogger
    private lateinit var mockRepository: CredentialRepository
    private lateinit var mockPreferences: PreferencesManager

    private val testCredentials = listOf(
        Credential(
            id = 1,
            title = "Test Account",
            username = "test@example.com",
            password = "password123",
            category = CredentialCategory.LOGIN,
            createdAt = System.currentTimeMillis(),
            modifiedAt = System.currentTimeMillis()
        ),
        Credential(
            id = 2,
            title = "Bank Account",
            username = "user@bank.com",
            password = "secure456",
            category = CredentialCategory.PAYMENT,
            createdAt = System.currentTimeMillis() - 100000,
            modifiedAt = System.currentTimeMillis() - 50000
        )
    )

    @Before
    fun setup() {
        mockContext = mockk(relaxed = true)
        mockConsentManager = mockk(relaxed = true)
        mockDataRetentionManager = mockk(relaxed = true)
        mockDataErasure = mockk(relaxed = true)
        mockDataPortability = mockk(relaxed = true)
        mockAuditLogger = mockk(relaxed = true)
        mockRepository = mockk(relaxed = true)
        mockPreferences = mockk(relaxed = true)

        // Default mock behaviors
        coEvery { mockRepository.getAllCredentials() } returns flowOf(testCredentials)
        every { mockConsentManager.hasConsent(any()) } returns true
        every { mockConsentManager.getConsentRecords() } returns emptyList()
        every { mockPreferences.getRetentionPolicyDays() } returns 365
        every { mockPreferences.isAutoDeleteEnabled() } returns false

        privacyManager = PrivacyManager(
            context = mockContext,
            consentManager = mockConsentManager,
            dataRetentionManager = mockDataRetentionManager,
            dataErasure = mockDataErasure,
            dataPortability = mockDataPortability,
            auditLogger = mockAuditLogger,
            credentialRepository = mockRepository,
            preferencesManager = mockPreferences
        )
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    // ==================== Consent Management Tests ====================

    @Test
    fun `setConsent should grant required purpose and log event`() = runTest {
        // Arrange
        val purpose = DataProcessingPurpose.CREDENTIAL_STORAGE
        every { mockConsentManager.recordConsent(purpose, true, any()) } returns true

        // Act
        privacyManager.setConsent(purpose, granted = true)

        // Assert
        verify(exactly = 1) { mockConsentManager.recordConsent(purpose, true, any()) }
        verify(exactly = 1) { mockAuditLogger.logSecurityEvent(
            event = AuditLogger.SecurityEvent.PRIVACY_CONSENT_GRANTED,
            action = "consent_granted",
            resource = "processing_purpose:CREDENTIAL_STORAGE",
            result = AuditLogger.EventResult.SUCCESS,
            metadata = any()
        )}
    }

    @Test
    fun `setConsent should withdraw consent for optional purpose`() = runTest {
        // Arrange
        val purpose = DataProcessingPurpose.PASSWORD_ANALYSIS
        every { mockConsentManager.withdrawConsent(purpose) } returns true

        // Act
        privacyManager.setConsent(purpose, granted = false)

        // Assert
        verify(exactly = 1) { mockConsentManager.withdrawConsent(purpose) }
        verify(exactly = 1) { mockAuditLogger.logSecurityEvent(
            event = AuditLogger.SecurityEvent.PRIVACY_CONSENT_WITHDRAWN,
            action = "consent_withdrawn",
            resource = "processing_purpose:PASSWORD_ANALYSIS",
            result = AuditLogger.EventResult.SUCCESS,
            metadata = any()
        )}
    }

    @Test
    fun `hasConsent should return true for required purposes`() {
        // Arrange
        val requiredPurpose = DataProcessingPurpose.CREDENTIAL_STORAGE

        // Act
        val hasConsent = privacyManager.hasConsent(requiredPurpose)

        // Assert
        assertTrue(hasConsent)
    }

    @Test
    fun `hasConsent should delegate to ConsentManager for optional purposes`() {
        // Arrange
        val optionalPurpose = DataProcessingPurpose.BIOMETRIC_AUTH
        every { mockConsentManager.hasConsent(optionalPurpose) } returns false

        // Act
        val hasConsent = privacyManager.hasConsent(optionalPurpose)

        // Assert
        assertFalse(hasConsent)
        verify(exactly = 1) { mockConsentManager.hasConsent(optionalPurpose) }
    }

    @Test
    fun `hasConsent should always return true for DATA_EXPORT`() {
        // Arrange
        val exportPurpose = DataProcessingPurpose.DATA_EXPORT

        // Act
        val hasConsent = privacyManager.hasConsent(exportPurpose)

        // Assert
        assertTrue(hasConsent)
    }

    // ==================== Data Processing Tracking Tests ====================

    @Test
    fun `recordDataProcessing should log processing activity`() {
        // Arrange
        val purpose = DataProcessingPurpose.CREDENTIAL_STORAGE
        val action = "create"
        val dataType = "login_credential"
        val recordCount = 1

        // Act
        privacyManager.recordDataProcessing(purpose, action, dataType, recordCount)

        // Assert
        verify(exactly = 1) { mockAuditLogger.logSecurityEvent(
            event = AuditLogger.SecurityEvent.PRIVACY_DATA_PROCESSED,
            action = action,
            resource = "purpose:CREDENTIAL_STORAGE|type:login_credential",
            result = AuditLogger.EventResult.SUCCESS,
            metadata = match { meta ->
                meta["record_count"] == recordCount &&
                meta["data_type"] == dataType &&
                meta["purpose"] == purpose.name
            }
        )}
    }

    @Test
    fun `recordDataProcessing should not log if consent is missing`() {
        // Arrange
        val purpose = DataProcessingPurpose.DIAGNOSTIC_LOGGING
        every { mockConsentManager.hasConsent(purpose) } returns false

        // Act
        privacyManager.recordDataProcessing(purpose, "log", "error_report", 1)

        // Assert
        verify(exactly = 0) { mockAuditLogger.logSecurityEvent(any(), any(), any(), any(), any(), any()) }
    }

    // ==================== Privacy Dashboard Tests ====================

    @Test
    fun `getPrivacyDashboardData should return complete privacy overview`() = runTest {
        // Arrange
        val consentRecords = listOf(
            ConsentRecord(
                purpose = DataProcessingPurpose.CREDENTIAL_STORAGE,
                granted = true,
                timestamp = System.currentTimeMillis(),
                version = "1.0.0",
                withdrawable = false
            ),
            ConsentRecord(
                purpose = DataProcessingPurpose.BIOMETRIC_AUTH,
                granted = true,
                timestamp = System.currentTimeMillis(),
                version = "1.0.0",
                withdrawable = true
            )
        )
        every { mockConsentManager.getConsentRecords() } returns consentRecords
        every { mockPreferences.getRetentionPolicyDays() } returns 365
        every { mockPreferences.isAutoDeleteEnabled() } returns true
        every { mockPreferences.getAutoLockTimeoutMinutes() } returns 5

        // Act
        val dashboardData = privacyManager.getPrivacyDashboardData()

        // Assert
        assertNotNull(dashboardData)
        assertEquals(2, dashboardData.totalCredentials)
        assertEquals(2, dashboardData.consentGiven.size)
        assertEquals(365, dashboardData.dataRetentionDays)
        assertTrue(dashboardData.autoDeleteEnabled)
        assertEquals(5, dashboardData.sessionTimeoutMinutes)
    }

    @Test
    fun `getPrivacyDashboardData should calculate storage size`() = runTest {
        // Act
        val dashboardData = privacyManager.getPrivacyDashboardData()

        // Assert
        assertTrue(dashboardData.estimatedStorageBytes > 0)
    }

    @Test
    fun `getPrivacyDashboardData should return last access time`() = runTest {
        // Arrange
        val lastAccessTime = System.currentTimeMillis() - 3600000 // 1 hour ago
        every { mockPreferences.getLastAccessTime() } returns lastAccessTime

        // Act
        val dashboardData = privacyManager.getPrivacyDashboardData()

        // Assert
        assertEquals(lastAccessTime, dashboardData.lastAccessTime)
    }

    // ==================== Data Retention Tests ====================

    @Test
    fun `setRetentionPolicy should update preferences and log event`() {
        // Arrange
        val retentionDays = 90
        val autoDelete = true
        val exemptFavorites = true
        every { mockPreferences.setRetentionPolicyDays(retentionDays) } just Runs
        every { mockPreferences.setAutoDeleteEnabled(autoDelete) } just Runs
        every { mockPreferences.setExemptFavoritesFromAutoDelete(exemptFavorites) } just Runs

        // Act
        val result = privacyManager.setRetentionPolicy(retentionDays, autoDelete, exemptFavorites)

        // Assert
        assertTrue(result)
        verify(exactly = 1) { mockPreferences.setRetentionPolicyDays(retentionDays) }
        verify(exactly = 1) { mockPreferences.setAutoDeleteEnabled(autoDelete) }
        verify(exactly = 1) { mockPreferences.setExemptFavoritesFromAutoDelete(exemptFavorites) }
        verify(exactly = 1) { mockAuditLogger.logSecurityEvent(
            event = AuditLogger.SecurityEvent.PRIVACY_RETENTION_POLICY_CHANGED,
            action = "update_retention_policy",
            resource = "retention_policy",
            result = AuditLogger.EventResult.SUCCESS,
            metadata = match { it["retention_days"] == retentionDays }
        )}
    }

    @Test
    fun `setRetentionPolicy should reject invalid retention days`() {
        // Act
        val result = privacyManager.setRetentionPolicy(15, false, false) // Invalid: not in allowed list

        // Assert
        assertFalse(result)
        verify(exactly = 0) { mockPreferences.setRetentionPolicyDays(any()) }
    }

    @Test
    fun `enforceRetentionPolicy should delegate to DataRetentionManager`() = runTest {
        // Arrange
        val deletionResult = DataRetentionManager.DeletionResult(
            success = true,
            deletedCount = 3,
            exemptedCount = 1,
            deletedItems = listOf("Item 1", "Item 2", "Item 3")
        )
        coEvery { mockDataRetentionManager.enforceRetentionPolicy(false) } returns deletionResult

        // Act
        val result = privacyManager.enforceRetentionPolicy(dryRun = false)

        // Assert
        assertEquals(deletionResult, result)
        coVerify(exactly = 1) { mockDataRetentionManager.enforceRetentionPolicy(false) }
    }

    // ==================== Right to Erasure Tests ====================

    @Test
    fun `initiateCompleteErasure should delegate to DataErasure`() = runTest {
        // Arrange
        val password = "test_password".toCharArray()
        val erasureResult = DataErasure.ErasureResult(
            success = true,
            itemsDeleted = mapOf(
                "credentials" to 2,
                "master_password" to 1,
                "encryption_keys" to 3
            ),
            errors = emptyList(),
            verificationPassed = true
        )
        coEvery { mockDataErasure.executeCompleteErasure(true, false, password) } returns erasureResult

        // Act
        val result = privacyManager.initiateCompleteErasure(
            deleteMasterPassword = true,
            deleteAuditLogs = false,
            masterPassword = password
        )

        // Assert
        assertEquals(erasureResult, result)
        coVerify(exactly = 1) { mockDataErasure.executeCompleteErasure(true, false, password) }
    }

    // ==================== Data Portability Tests ====================

    @Test
    fun `exportUserData should delegate to DataPortability with consent check`() = runTest {
        // Arrange
        val format = DataPortability.ExportFormat.JSON
        val exportResult = DataPortability.ExportResult(
            success = true,
            format = format,
            filePath = "/path/to/export.json",
            fileSize = 1024,
            itemCount = 2,
            errorMessage = null
        )
        every { mockConsentManager.hasConsent(DataProcessingPurpose.DATA_EXPORT) } returns true
        coEvery { mockDataPortability.exportUserData(format, any(), false, null) } returns exportResult

        // Act
        val result = privacyManager.exportUserData(format, encrypt = false, password = null)

        // Assert
        assertEquals(exportResult, result)
        coVerify(exactly = 1) { mockDataPortability.exportUserData(format, any(), false, null) }
    }

    @Test
    fun `exportUserData should fail if consent is not given`() = runTest {
        // Arrange
        every { mockConsentManager.hasConsent(DataProcessingPurpose.DATA_EXPORT) } returns false

        // Act
        val result = privacyManager.exportUserData(DataPortability.ExportFormat.JSON)

        // Assert
        assertFalse(result.success)
        assertTrue(result.errorMessage?.contains("consent") == true)
        coVerify(exactly = 0) { mockDataPortability.exportUserData(any(), any(), any(), any()) }
    }

    // ==================== GDPR Compliance Tests ====================

    @Test
    fun `getGdprComplianceStatus should return compliance metrics`() {
        // Arrange
        val consentRecords = listOf(
            ConsentRecord(
                purpose = DataProcessingPurpose.CREDENTIAL_STORAGE,
                granted = true,
                timestamp = System.currentTimeMillis(),
                version = "1.0.0",
                withdrawable = false
            )
        )
        every { mockConsentManager.getConsentRecords() } returns consentRecords
        every { mockPreferences.getRetentionPolicyDays() } returns 365

        // Act
        val complianceStatus = privacyManager.getGdprComplianceStatus()

        // Assert
        assertNotNull(complianceStatus)
        assertTrue(complianceStatus.contains("GDPR Compliance"))
        assertTrue(complianceStatus.contains("Consent Management: ✓"))
        assertTrue(complianceStatus.contains("Data Retention: ✓"))
    }

    // ==================== Edge Cases and Error Handling ====================

    @Test
    fun `setConsent should handle ConsentManager failure gracefully`() = runTest {
        // Arrange
        val purpose = DataProcessingPurpose.BIOMETRIC_AUTH
        every { mockConsentManager.recordConsent(purpose, true, any()) } throws Exception("Database error")

        // Act & Assert
        try {
            privacyManager.setConsent(purpose, granted = true)
            // Should not throw exception
        } catch (e: Exception) {
            fail("Should handle consent manager failures gracefully")
        }
    }

    @Test
    fun `getPrivacyDashboardData should handle empty credentials`() = runTest {
        // Arrange
        coEvery { mockRepository.getAllCredentials() } returns flowOf(emptyList())

        // Act
        val dashboardData = privacyManager.getPrivacyDashboardData()

        // Assert
        assertEquals(0, dashboardData.totalCredentials)
        assertEquals(0, dashboardData.estimatedStorageBytes)
    }

    @Test
    fun `enforceRetentionPolicy should handle retention manager errors`() = runTest {
        // Arrange
        coEvery { mockDataRetentionManager.enforceRetentionPolicy(any()) } throws Exception("Enforcement failed")

        // Act
        val result = privacyManager.enforceRetentionPolicy(dryRun = false)

        // Assert
        assertFalse(result.success)
        assertEquals(0, result.deletedCount)
    }
}
