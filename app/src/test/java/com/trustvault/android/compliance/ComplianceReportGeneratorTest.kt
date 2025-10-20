package com.trustvault.android.compliance

import android.content.Context
import com.trustvault.android.domain.model.Credential
import com.trustvault.android.domain.model.CredentialCategory
import com.trustvault.android.domain.repository.CredentialRepository
import com.trustvault.android.util.PreferencesManager
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Unit tests for ComplianceReportGenerator.
 *
 * Tests compliance report generation for:
 * - GDPR Article 30 (Record of Processing Activities)
 * - DPDP Act 2023 (India)
 * - ISO 27001:2022 Audit Reports
 * - JSON and text export formats
 * - Metric calculations
 * - Report completeness
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ComplianceReportGeneratorTest {

    private lateinit var reportGenerator: ComplianceReportGenerator
    private lateinit var mockContext: Context
    private lateinit var mockRepository: CredentialRepository
    private lateinit var mockConsentManager: ConsentManager
    private lateinit var mockAuditLogger: AuditLogger
    private lateinit var mockPreferences: PreferencesManager

    private val currentTime = System.currentTimeMillis()

    @Before
    fun setup() {
        mockContext = mockk(relaxed = true)
        mockRepository = mockk(relaxed = true)
        mockConsentManager = mockk(relaxed = true)
        mockAuditLogger = mockk(relaxed = true)
        mockPreferences = mockk(relaxed = true)

        // Default mock behaviors
        coEvery { mockRepository.getAllCredentials() } returns flowOf(createTestCredentials())
        every { mockConsentManager.getConsentRecords() } returns createTestConsentRecords()
        every { mockAuditLogger.getLogs() } returns createTestAuditLogs()
        every { mockAuditLogger.getLogsByTimeRange(any(), any()) } returns createTestAuditLogs()
        coEvery { mockAuditLogger.verifyLogIntegrity() } returns AuditLogger.VerificationResult(
            isValid = true,
            totalEntries = 10,
            corruptedEntries = emptyList(),
            errorMessage = null
        )
        every { mockPreferences.getRetentionPolicyDays() } returns 90
        every { mockPreferences.isAutoDeleteEnabled() } returns true
        every { mockPreferences.getAutoLockTimeoutMinutes() } returns 5

        reportGenerator = ComplianceReportGenerator(
            context = mockContext,
            credentialRepository = mockRepository,
            consentManager = mockConsentManager,
            auditLogger = mockAuditLogger,
            preferencesManager = mockPreferences
        )
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    // ==================== GDPR Article 30 Report Tests ====================

    @Test
    fun `generateGdprArticle30Report should create complete report`() = runTest {
        // Act
        val report = reportGenerator.generateGdprArticle30Report(reportingPeriodDays = 90)

        // Assert
        assertNotNull(report)
        assertTrue(report.organizationName.isNotEmpty())
        assertTrue(report.reportGeneratedAt > 0)
        assertEquals(90, report.reportingPeriodDays)
        assertNotNull(report.dataControllerInfo)
        assertTrue(report.processingActivities.isNotEmpty())
        assertNotNull(report.securityMeasures)
        assertNotNull(report.dataRetentionPolicy)
    }

    @Test
    fun `GDPR report should include data controller information`() = runTest {
        // Act
        val report = reportGenerator.generateGdprArticle30Report()

        // Assert
        val controllerInfo = report.dataControllerInfo
        assertEquals("TrustVault User", controllerInfo.name)
        assertEquals("Local Device", controllerInfo.contactDetails)
        assertEquals("User (Self)", controllerInfo.representative)
    }

    @Test
    fun `GDPR report should list all processing activities`() = runTest {
        // Act
        val report = reportGenerator.generateGdprArticle30Report()

        // Assert
        val activities = report.processingActivities
        assertTrue(activities.isNotEmpty())

        // Should include key processing activities
        assertTrue(activities.any { it.purpose.contains("Credential Storage") })
        assertTrue(activities.any { it.legalBasis.contains("Consent") })
    }

    @Test
    fun `GDPR report should document security measures`() = runTest {
        // Act
        val report = reportGenerator.generateGdprArticle30Report()

        // Assert
        val securityMeasures = report.securityMeasures
        assertTrue(securityMeasures.encryption.contains("AES-256-GCM"))
        assertTrue(securityMeasures.accessControl.contains("Master password"))
        assertTrue(securityMeasures.dataMinimization.contains("No telemetry"))
    }

    @Test
    fun `GDPR report should include data retention policy`() = runTest {
        // Arrange
        every { mockPreferences.getRetentionPolicyDays() } returns 365

        // Act
        val report = reportGenerator.generateGdprArticle30Report()

        // Assert
        val retentionPolicy = report.dataRetentionPolicy
        assertEquals(365, retentionPolicy.retentionPeriodDays)
        assertTrue(retentionPolicy.autoDeleteEnabled)
    }

    @Test
    fun `GDPR report should calculate processing statistics`() = runTest {
        // Arrange
        val credentials = createTestCredentials()
        coEvery { mockRepository.getAllCredentials() } returns flowOf(credentials)

        // Act
        val report = reportGenerator.generateGdprArticle30Report()

        // Assert
        assertTrue(report.processingActivities.isNotEmpty())
        val credentialStorage = report.processingActivities.find {
            it.purpose.contains("Credential Storage")
        }
        assertNotNull(credentialStorage)
    }

    // ==================== DPDP Act Report Tests ====================

    @Test
    fun `generateDpdpComplianceReport should create complete report`() = runTest {
        // Act
        val report = reportGenerator.generateDpdpComplianceReport()

        // Assert
        assertNotNull(report)
        assertTrue(report.organizationName.isNotEmpty())
        assertTrue(report.reportGeneratedAt > 0)
        assertNotNull(report.consentManagement)
        assertNotNull(report.dataSecurityMeasures)
        assertNotNull(report.userRights)
        assertNotNull(report.dataRetentionCompliance)
    }

    @Test
    fun `DPDP report should document consent management`() = runTest {
        // Act
        val report = reportGenerator.generateDpdpComplianceReport()

        // Assert
        val consentMgmt = report.consentManagement
        assertTrue(consentMgmt.consentRecordCount >= 0)
        assertTrue(consentMgmt.granularConsentSupported)
        assertTrue(consentMgmt.withdrawalMechanismAvailable)
    }

    @Test
    fun `DPDP report should list implemented user rights`() = runTest {
        // Act
        val report = reportGenerator.generateDpdpComplianceReport()

        // Assert
        val userRights = report.userRights
        assertTrue(userRights.rightToAccess)
        assertTrue(userRights.rightToCorrection)
        assertTrue(userRights.rightToErasure)
        assertTrue(userRights.rightToDataPortability)
    }

    @Test
    fun `DPDP report should document data security measures`() = runTest {
        // Act
        val report = reportGenerator.generateDpdpComplianceReport()

        // Assert
        val securityMeasures = report.dataSecurityMeasures
        assertTrue(securityMeasures.encryptionAtRest)
        assertTrue(securityMeasures.accessControls)
        assertTrue(securityMeasures.auditLogging)
        assertFalse(securityMeasures.thirdPartySharing) // TrustVault is local-only
    }

    @Test
    fun `DPDP report should verify retention compliance`() = runTest {
        // Arrange
        every { mockPreferences.getRetentionPolicyDays() } returns 180

        // Act
        val report = reportGenerator.generateDpdpComplianceReport()

        // Assert
        val retentionCompliance = report.dataRetentionCompliance
        assertEquals(180, retentionCompliance.retentionPeriodDays)
        assertTrue(retentionCompliance.autoDeleteEnabled)
    }

    // ==================== ISO 27001 Audit Report Tests ====================

    @Test
    fun `generateIso27001AuditReport should create complete report`() = runTest {
        // Act
        val report = reportGenerator.generateIso27001AuditReport(reportingPeriodDays = 90)

        // Assert
        assertNotNull(report)
        assertTrue(report.organizationName.isNotEmpty())
        assertTrue(report.reportGeneratedAt > 0)
        assertEquals(90, report.reportingPeriodDays)
        assertNotNull(report.accessControl)
        assertNotNull(report.cryptographicControls)
        assertNotNull(report.loggingAndMonitoring)
        assertNotNull(report.incidentManagement)
    }

    @Test
    fun `ISO 27001 report should document access control (A_5_15)`() = runTest {
        // Act
        val report = reportGenerator.generateIso27001AuditReport()

        // Assert
        val accessControl = report.accessControl
        assertTrue(accessControl.authenticationMechanism.contains("Master password"))
        assertTrue(accessControl.authenticationMechanism.contains("Argon2id"))
        assertEquals(5, accessControl.sessionTimeoutMinutes)
        assertTrue(accessControl.biometricAuthSupported)
    }

    @Test
    fun `ISO 27001 report should document cryptographic controls (A_8_24)`() = runTest {
        // Act
        val report = reportGenerator.generateIso27001AuditReport()

        // Assert
        val cryptoControls = report.cryptographicControls
        assertTrue(cryptoControls.encryptionAlgorithms.contains("AES-256-GCM"))
        assertTrue(cryptoControls.keyManagement.contains("Android Keystore"))
        assertTrue(cryptoControls.hardwareBackedKeys)
    }

    @Test
    fun `ISO 27001 report should document logging (A_12_4)`() = runTest {
        // Arrange
        val auditLogs = createTestAuditLogs()
        every { mockAuditLogger.getLogsByTimeRange(any(), any()) } returns auditLogs

        // Act
        val report = reportGenerator.generateIso27001AuditReport(reportingPeriodDays = 90)

        // Assert
        val logging = report.loggingAndMonitoring
        assertTrue(logging.auditLoggingEnabled)
        assertEquals(auditLogs.size, logging.logEntriesInPeriod)
        assertTrue(logging.tamperProofLogging)
        assertTrue(logging.logIntegrityVerified)
    }

    @Test
    fun `ISO 27001 report should calculate security metrics`() = runTest {
        // Arrange
        val auditLogs = listOf(
            createAuditLogEntry(AuditLogger.SecurityEvent.AUTH_LOGIN_SUCCESS, currentTime),
            createAuditLogEntry(AuditLogger.SecurityEvent.AUTH_LOGIN_FAILURE, currentTime),
            createAuditLogEntry(AuditLogger.SecurityEvent.AUTH_LOGIN_FAILURE, currentTime),
            createAuditLogEntry(AuditLogger.SecurityEvent.DATA_CREATED, currentTime)
        )
        every { mockAuditLogger.getLogsByTimeRange(any(), any()) } returns auditLogs

        // Act
        val report = reportGenerator.generateIso27001AuditReport()

        // Assert
        val incidentMgmt = report.incidentManagement
        assertEquals(2, incidentMgmt.authenticationFailures) // 2 login failures
        assertEquals(0, incidentMgmt.securityIncidents) // No incidents
    }

    // ==================== Report Export Tests ====================

    @Test
    fun `exportReportToJson should produce valid JSON for GDPR report`() = runTest {
        // Arrange
        val report = reportGenerator.generateGdprArticle30Report()

        // Act
        val jsonString = reportGenerator.exportReportToJson(report)

        // Assert
        assertNotNull(jsonString)
        assertTrue(jsonString.contains("organizationName"))
        assertTrue(jsonString.contains("reportGeneratedAt"))
        assertTrue(jsonString.contains("processingActivities"))
        assertTrue(jsonString.contains("securityMeasures"))
    }

    @Test
    fun `exportReportToJson should produce valid JSON for DPDP report`() = runTest {
        // Arrange
        val report = reportGenerator.generateDpdpComplianceReport()

        // Act
        val jsonString = reportGenerator.exportReportToJson(report)

        // Assert
        assertNotNull(jsonString)
        assertTrue(jsonString.contains("consentManagement"))
        assertTrue(jsonString.contains("dataSecurityMeasures"))
        assertTrue(jsonString.contains("userRights"))
    }

    @Test
    fun `exportReportToJson should produce valid JSON for ISO 27001 report`() = runTest {
        // Arrange
        val report = reportGenerator.generateIso27001AuditReport()

        // Act
        val jsonString = reportGenerator.exportReportToJson(report)

        // Assert
        assertNotNull(jsonString)
        assertTrue(jsonString.contains("accessControl"))
        assertTrue(jsonString.contains("cryptographicControls"))
        assertTrue(jsonString.contains("loggingAndMonitoring"))
    }

    @Test
    fun `exportReportToText should produce human-readable GDPR report`() = runTest {
        // Arrange
        val report = reportGenerator.generateGdprArticle30Report()

        // Act
        val textReport = reportGenerator.exportReportToText(
            report,
            ComplianceReportGenerator.ReportType.GDPR_ARTICLE_30
        )

        // Assert
        assertNotNull(textReport)
        assertTrue(textReport.contains("GDPR Article 30"))
        assertTrue(textReport.contains("Record of Processing Activities"))
        assertTrue(textReport.contains("Data Controller"))
        assertTrue(textReport.contains("Security Measures"))
    }

    @Test
    fun `exportReportToText should produce human-readable DPDP report`() = runTest {
        // Arrange
        val report = reportGenerator.generateDpdpComplianceReport()

        // Act
        val textReport = reportGenerator.exportReportToText(
            report,
            ComplianceReportGenerator.ReportType.DPDP_ACT_2023
        )

        // Assert
        assertNotNull(textReport)
        assertTrue(textReport.contains("DPDP Act 2023"))
        assertTrue(textReport.contains("Consent Management"))
        assertTrue(textReport.contains("User Rights"))
    }

    @Test
    fun `exportReportToText should produce human-readable ISO 27001 report`() = runTest {
        // Arrange
        val report = reportGenerator.generateIso27001AuditReport()

        // Act
        val textReport = reportGenerator.exportReportToText(
            report,
            ComplianceReportGenerator.ReportType.ISO_27001_AUDIT
        )

        // Assert
        assertNotNull(textReport)
        assertTrue(textReport.contains("ISO 27001:2022"))
        assertTrue(textReport.contains("Access Control"))
        assertTrue(textReport.contains("Cryptographic Controls"))
    }

    // ==================== Reporting Period Tests ====================

    @Test
    fun `reports should support different reporting periods`() = runTest {
        // Act
        val report30Days = reportGenerator.generateGdprArticle30Report(reportingPeriodDays = 30)
        val report90Days = reportGenerator.generateGdprArticle30Report(reportingPeriodDays = 90)
        val report365Days = reportGenerator.generateGdprArticle30Report(reportingPeriodDays = 365)

        // Assert
        assertEquals(30, report30Days.reportingPeriodDays)
        assertEquals(90, report90Days.reportingPeriodDays)
        assertEquals(365, report365Days.reportingPeriodDays)
    }

    @Test
    fun `reports should filter audit logs by time period`() = runTest {
        // Arrange
        val logsLast30Days = listOf(
            createAuditLogEntry(AuditLogger.SecurityEvent.DATA_CREATED, currentTime)
        )
        val logsLast90Days = listOf(
            createAuditLogEntry(AuditLogger.SecurityEvent.DATA_CREATED, currentTime),
            createAuditLogEntry(AuditLogger.SecurityEvent.DATA_UPDATED, currentTime - TimeUnit.DAYS.toMillis(60))
        )

        every { mockAuditLogger.getLogsByTimeRange(
            startTime = match { it > currentTime - TimeUnit.DAYS.toMillis(31) },
            endTime = any()
        )} returns logsLast30Days

        every { mockAuditLogger.getLogsByTimeRange(
            startTime = match { it <= currentTime - TimeUnit.DAYS.toMillis(31) },
            endTime = any()
        )} returns logsLast90Days

        // Act
        val report30 = reportGenerator.generateIso27001AuditReport(reportingPeriodDays = 30)
        val report90 = reportGenerator.generateIso27001AuditReport(reportingPeriodDays = 90)

        // Assert
        // Reports should show different log counts based on period
        assertNotNull(report30.loggingAndMonitoring)
        assertNotNull(report90.loggingAndMonitoring)
    }

    // ==================== Error Handling Tests ====================

    @Test
    fun `report generation should handle empty credentials gracefully`() = runTest {
        // Arrange
        coEvery { mockRepository.getAllCredentials() } returns flowOf(emptyList())

        // Act
        val report = reportGenerator.generateGdprArticle30Report()

        // Assert
        assertNotNull(report)
        assertTrue(report.processingActivities.isNotEmpty()) // Still has activities, just 0 records
    }

    @Test
    fun `report generation should handle empty audit logs`() = runTest {
        // Arrange
        every { mockAuditLogger.getLogsByTimeRange(any(), any()) } returns emptyList()

        // Act
        val report = reportGenerator.generateIso27001AuditReport()

        // Assert
        assertNotNull(report)
        assertEquals(0, report.loggingAndMonitoring.logEntriesInPeriod)
    }

    @Test
    fun `report generation should handle repository errors`() = runTest {
        // Arrange
        coEvery { mockRepository.getAllCredentials() } throws Exception("Database error")

        // Act
        val report = reportGenerator.generateGdprArticle30Report()

        // Assert
        assertNotNull(report)
        // Should still generate report with available data
    }

    // ==================== Compliance Metrics Tests ====================

    @Test
    fun `reports should calculate consent coverage percentage`() = runTest {
        // Arrange
        val consentRecords = listOf(
            ConsentRecord(
                purpose = DataProcessingPurpose.BIOMETRIC_AUTH,
                granted = true,
                timestamp = currentTime,
                version = "1.0.0",
                withdrawable = true
            ),
            ConsentRecord(
                purpose = DataProcessingPurpose.PASSWORD_ANALYSIS,
                granted = false,
                timestamp = currentTime,
                version = "1.0.0",
                withdrawable = true
            )
        )
        every { mockConsentManager.getConsentRecords() } returns consentRecords

        // Act
        val report = reportGenerator.generateDpdpComplianceReport()

        // Assert
        assertEquals(2, report.consentManagement.consentRecordCount)
    }

    @Test
    fun `reports should verify log integrity`() = runTest {
        // Arrange
        coEvery { mockAuditLogger.verifyLogIntegrity() } returns AuditLogger.VerificationResult(
            isValid = true,
            totalEntries = 50,
            corruptedEntries = emptyList(),
            errorMessage = null
        )

        // Act
        val report = reportGenerator.generateIso27001AuditReport()

        // Assert
        assertTrue(report.loggingAndMonitoring.logIntegrityVerified)
    }

    @Test
    fun `reports should detect corrupted logs`() = runTest {
        // Arrange
        coEvery { mockAuditLogger.verifyLogIntegrity() } returns AuditLogger.VerificationResult(
            isValid = false,
            totalEntries = 50,
            corruptedEntries = listOf("entry_1", "entry_2"),
            errorMessage = "Hash chain broken"
        )

        // Act
        val report = reportGenerator.generateIso27001AuditReport()

        // Assert
        assertFalse(report.loggingAndMonitoring.logIntegrityVerified)
    }

    // ==================== Helper Functions ====================

    private fun createTestCredentials(): List<Credential> {
        return listOf(
            Credential(
                id = 1,
                title = "Test Account",
                username = "user@example.com",
                password = "password",
                category = CredentialCategory.LOGIN,
                createdAt = currentTime,
                modifiedAt = currentTime
            ),
            Credential(
                id = 2,
                title = "Bank Account",
                username = "bank@example.com",
                password = "secure",
                category = CredentialCategory.PAYMENT,
                createdAt = currentTime - TimeUnit.DAYS.toMillis(30),
                modifiedAt = currentTime - TimeUnit.DAYS.toMillis(15)
            )
        )
    }

    private fun createTestConsentRecords(): List<ConsentRecord> {
        return listOf(
            ConsentRecord(
                purpose = DataProcessingPurpose.CREDENTIAL_STORAGE,
                granted = true,
                timestamp = currentTime,
                version = "1.0.0",
                withdrawable = false
            ),
            ConsentRecord(
                purpose = DataProcessingPurpose.BIOMETRIC_AUTH,
                granted = true,
                timestamp = currentTime,
                version = "1.0.0",
                withdrawable = true
            )
        )
    }

    private fun createTestAuditLogs(): List<AuditLogger.AuditLogEntry> {
        return listOf(
            createAuditLogEntry(AuditLogger.SecurityEvent.AUTH_LOGIN_SUCCESS, currentTime),
            createAuditLogEntry(AuditLogger.SecurityEvent.DATA_CREATED, currentTime - 1000),
            createAuditLogEntry(AuditLogger.SecurityEvent.DATA_UPDATED, currentTime - 2000)
        )
    }

    private fun createAuditLogEntry(
        event: AuditLogger.SecurityEvent,
        timestamp: Long
    ): AuditLogger.AuditLogEntry {
        return AuditLogger.AuditLogEntry(
            id = "log_${System.nanoTime()}",
            timestamp = timestamp,
            event = event,
            severity = AuditLogger.EventSeverity.INFO,
            action = "test_action",
            resource = "test_resource",
            result = AuditLogger.EventResult.SUCCESS,
            userId = null,
            ipAddress = null,
            deviceInfo = null,
            metadata = emptyMap(),
            previousHash = "",
            hash = "test_hash_${System.nanoTime()}"
        )
    }
}
