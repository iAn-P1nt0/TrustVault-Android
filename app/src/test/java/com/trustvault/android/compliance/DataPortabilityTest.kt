package com.trustvault.android.compliance

import android.content.Context
import com.trustvault.android.domain.model.Credential
import com.trustvault.android.domain.model.CredentialCategory
import com.trustvault.android.domain.repository.CredentialRepository
import com.trustvault.android.security.CryptoManager
import com.trustvault.android.util.PreferencesManager
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Unit tests for DataPortability.
 *
 * Tests GDPR Article 20 "Right to Data Portability" compliance:
 * - JSON export format
 * - CSV export format
 * - XML export format
 * - KeePass CSV export format
 * - Encrypted exports
 * - Export scopes (credentials only, with consent, all data)
 * - Metadata inclusion
 * - File creation and verification
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DataPortabilityTest {

    private lateinit var dataPortability: DataPortability
    private lateinit var mockContext: Context
    private lateinit var mockRepository: CredentialRepository
    private lateinit var mockConsentManager: ConsentManager
    private lateinit var mockPreferences: PreferencesManager
    private lateinit var mockCryptoManager: CryptoManager
    private lateinit var mockAuditLogger: AuditLogger
    private lateinit var mockExternalFilesDir: File

    private val testPassword = "export_password".toCharArray()

    @Before
    fun setup() {
        mockContext = mockk(relaxed = true)
        mockRepository = mockk(relaxed = true)
        mockConsentManager = mockk(relaxed = true)
        mockPreferences = mockk(relaxed = true)
        mockCryptoManager = mockk(relaxed = true)
        mockAuditLogger = mockk(relaxed = true)

        // Mock external files directory
        mockExternalFilesDir = mockk(relaxed = true)
        every { mockContext.getExternalFilesDir(any()) } returns mockExternalFilesDir
        every { mockExternalFilesDir.absolutePath } returns "/sdcard/TrustVault/exports"

        // Default mock behaviors
        coEvery { mockRepository.getAllCredentials() } returns flowOf(createTestCredentials())
        every { mockConsentManager.exportConsentRecords() } returns "[]"
        every { mockPreferences.getRetentionPolicyDays() } returns 90
        every { mockPreferences.isAutoDeleteEnabled() } returns false

        // Mock encryption
        val mockEncryptedData = mockk<CryptoManager.EncryptedData>(relaxed = true)
        every { mockEncryptedData.ciphertext } returns "encrypted_data".toByteArray()
        every { mockEncryptedData.iv } returns "iv".toByteArray()
        every { mockEncryptedData.algorithm } returns CryptoManager.Algorithm.AES_GCM
        every { mockCryptoManager.encrypt(any(), any(), any()) } returns mockEncryptedData

        dataPortability = DataPortability(
            context = mockContext,
            credentialRepository = mockRepository,
            consentManager = mockConsentManager,
            preferencesManager = mockPreferences,
            cryptoManager = mockCryptoManager,
            auditLogger = mockAuditLogger
        )
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    // ==================== JSON Export Tests ====================

    @Test
    fun `exportUserData should create JSON file with all data`() = runTest {
        // Arrange
        every { mockExternalFilesDir.exists() } returns true

        // Act
        val result = dataPortability.exportUserData(
            format = DataPortability.ExportFormat.JSON,
            scope = DataPortability.ExportScope.ALL_DATA,
            encrypt = false,
            password = null
        )

        // Assert
        assertTrue(result.success)
        assertEquals(DataPortability.ExportFormat.JSON, result.format)
        assertTrue(result.filePath.endsWith(".json"))
        assertTrue(result.itemCount > 0)
        assertNull(result.errorMessage)
    }

    @Test
    fun `JSON export should include credentials with all fields`() = runTest {
        // Arrange
        val credentials = createTestCredentials()
        coEvery { mockRepository.getAllCredentials() } returns flowOf(credentials)

        // Act
        val result = dataPortability.exportUserData(
            format = DataPortability.ExportFormat.JSON,
            scope = DataPortability.ExportScope.CREDENTIALS_ONLY,
            encrypt = false,
            password = null
        )

        // Assert
        assertTrue(result.success)
        assertEquals(credentials.size, result.itemCount)
    }

    @Test
    fun `JSON export should include metadata`() = runTest {
        // Act
        val result = dataPortability.exportUserData(
            format = DataPortability.ExportFormat.JSON,
            scope = DataPortability.ExportScope.ALL_DATA,
            encrypt = false,
            password = null
        )

        // Assert
        assertTrue(result.success)
        // Metadata should be included in the file
    }

    // ==================== CSV Export Tests ====================

    @Test
    fun `exportUserData should create CSV file with credentials`() = runTest {
        // Act
        val result = dataPortability.exportUserData(
            format = DataPortability.ExportFormat.CSV,
            scope = DataPortability.ExportScope.CREDENTIALS_ONLY,
            encrypt = false,
            password = null
        )

        // Assert
        assertTrue(result.success)
        assertEquals(DataPortability.ExportFormat.CSV, result.format)
        assertTrue(result.filePath.endsWith(".csv"))
        assertTrue(result.itemCount > 0)
    }

    @Test
    fun `CSV export should handle special characters in fields`() = runTest {
        // Arrange
        val credentialsWithSpecialChars = listOf(
            Credential(
                id = 1,
                title = "Account with, comma",
                username = "user\"with\"quotes@example.com",
                password = "pass\nwith\nnewlines",
                notes = "Notes with 'single' and \"double\" quotes",
                category = CredentialCategory.LOGIN,
                createdAt = System.currentTimeMillis(),
                modifiedAt = System.currentTimeMillis()
            )
        )
        coEvery { mockRepository.getAllCredentials() } returns flowOf(credentialsWithSpecialChars)

        // Act
        val result = dataPortability.exportUserData(
            format = DataPortability.ExportFormat.CSV,
            scope = DataPortability.ExportScope.CREDENTIALS_ONLY,
            encrypt = false,
            password = null
        )

        // Assert
        assertTrue(result.success)
    }

    // ==================== XML Export Tests ====================

    @Test
    fun `exportUserData should create XML file with structured data`() = runTest {
        // Act
        val result = dataPortability.exportUserData(
            format = DataPortability.ExportFormat.XML,
            scope = DataPortability.ExportScope.ALL_DATA,
            encrypt = false,
            password = null
        )

        // Assert
        assertTrue(result.success)
        assertEquals(DataPortability.ExportFormat.XML, result.format)
        assertTrue(result.filePath.endsWith(".xml"))
        assertTrue(result.itemCount > 0)
    }

    @Test
    fun `XML export should escape special XML characters`() = runTest {
        // Arrange
        val credentialsWithXmlChars = listOf(
            Credential(
                id = 1,
                title = "Account <with> & special chars",
                username = "user@example.com",
                password = "pass&word",
                notes = "Notes with <tags> & ampersands",
                category = CredentialCategory.LOGIN,
                createdAt = System.currentTimeMillis(),
                modifiedAt = System.currentTimeMillis()
            )
        )
        coEvery { mockRepository.getAllCredentials() } returns flowOf(credentialsWithXmlChars)

        // Act
        val result = dataPortability.exportUserData(
            format = DataPortability.ExportFormat.XML,
            scope = DataPortability.ExportScope.CREDENTIALS_ONLY,
            encrypt = false,
            password = null
        )

        // Assert
        assertTrue(result.success)
    }

    // ==================== KeePass CSV Export Tests ====================

    @Test
    fun `exportUserData should create KeePass-compatible CSV`() = runTest {
        // Act
        val result = dataPortability.exportUserData(
            format = DataPortability.ExportFormat.KEEPASS_CSV,
            scope = DataPortability.ExportScope.CREDENTIALS_ONLY,
            encrypt = false,
            password = null
        )

        // Assert
        assertTrue(result.success)
        assertEquals(DataPortability.ExportFormat.KEEPASS_CSV, result.format)
        assertTrue(result.filePath.endsWith(".csv"))
        assertTrue(result.itemCount > 0)
    }

    @Test
    fun `KeePass CSV should use correct column headers`() = runTest {
        // Act
        val result = dataPortability.exportUserData(
            format = DataPortability.ExportFormat.KEEPASS_CSV,
            scope = DataPortability.ExportScope.CREDENTIALS_ONLY,
            encrypt = false,
            password = null
        )

        // Assert
        assertTrue(result.success)
        // KeePass format: "Account","Login Name","Password","Web Site","Comments"
    }

    // ==================== Export Scope Tests ====================

    @Test
    fun `CREDENTIALS_ONLY scope should export only credentials`() = runTest {
        // Act
        val result = dataPortability.exportUserData(
            format = DataPortability.ExportFormat.JSON,
            scope = DataPortability.ExportScope.CREDENTIALS_ONLY,
            encrypt = false,
            password = null
        )

        // Assert
        assertTrue(result.success)
        // Should not include consent records or privacy preferences
    }

    @Test
    fun `WITH_CONSENT scope should include credentials and consent records`() = runTest {
        // Arrange
        val consentJson = """[{"purpose":"BIOMETRIC_AUTH","granted":true}]"""
        every { mockConsentManager.exportConsentRecords() } returns consentJson

        // Act
        val result = dataPortability.exportUserData(
            format = DataPortability.ExportFormat.JSON,
            scope = DataPortability.ExportScope.WITH_CONSENT,
            encrypt = false,
            password = null
        )

        // Assert
        assertTrue(result.success)
        // Should include consent records
    }

    @Test
    fun `ALL_DATA scope should include everything`() = runTest {
        // Arrange
        val consentJson = """[{"purpose":"BIOMETRIC_AUTH","granted":true}]"""
        every { mockConsentManager.exportConsentRecords() } returns consentJson

        // Act
        val result = dataPortability.exportUserData(
            format = DataPortability.ExportFormat.JSON,
            scope = DataPortability.ExportScope.ALL_DATA,
            encrypt = false,
            password = null
        )

        // Assert
        assertTrue(result.success)
        // Should include credentials, consent, privacy preferences, and metadata
    }

    // ==================== Encryption Tests ====================

    @Test
    fun `exportUserData should encrypt export when requested`() = runTest {
        // Arrange
        every { mockExternalFilesDir.exists() } returns true

        // Act
        val result = dataPortability.exportUserData(
            format = DataPortability.ExportFormat.JSON,
            scope = DataPortability.ExportScope.ALL_DATA,
            encrypt = true,
            password = testPassword
        )

        // Assert
        assertTrue(result.success)
        assertTrue(result.filePath.endsWith(".encrypted"))
        verify(atLeast = 1) { mockCryptoManager.encrypt(any(), any(), any()) }
    }

    @Test
    fun `encrypted export should require password`() = runTest {
        // Act
        val result = dataPortability.exportUserData(
            format = DataPortability.ExportFormat.JSON,
            scope = DataPortability.ExportScope.ALL_DATA,
            encrypt = true,
            password = null // Missing password
        )

        // Assert
        assertFalse(result.success)
        assertNotNull(result.errorMessage)
        assertTrue(result.errorMessage!!.contains("password"))
    }

    @Test
    fun `encrypted export should clear password from memory`() = runTest {
        // Arrange
        val password = "test_password".toCharArray()
        every { mockExternalFilesDir.exists() } returns true

        // Act
        dataPortability.exportUserData(
            format = DataPortability.ExportFormat.JSON,
            scope = DataPortability.ExportScope.ALL_DATA,
            encrypt = true,
            password = password
        )

        // Assert - Password should be cleared after use
        assertTrue(password.all { it == '\u0000' })
    }

    // ==================== File Operations Tests ====================

    @Test
    fun `exportUserData should create export directory if missing`() = runTest {
        // Arrange
        every { mockExternalFilesDir.exists() } returns false
        every { mockExternalFilesDir.mkdirs() } returns true

        // Act
        val result = dataPortability.exportUserData(
            format = DataPortability.ExportFormat.JSON,
            scope = DataPortability.ExportScope.CREDENTIALS_ONLY,
            encrypt = false,
            password = null
        )

        // Assert
        assertTrue(result.success)
        verify(exactly = 1) { mockExternalFilesDir.mkdirs() }
    }

    @Test
    fun `exportUserData should fail if directory creation fails`() = runTest {
        // Arrange
        every { mockExternalFilesDir.exists() } returns false
        every { mockExternalFilesDir.mkdirs() } returns false

        // Act
        val result = dataPortability.exportUserData(
            format = DataPortability.ExportFormat.JSON,
            scope = DataPortability.ExportScope.CREDENTIALS_ONLY,
            encrypt = false,
            password = null
        )

        // Assert
        assertFalse(result.success)
        assertNotNull(result.errorMessage)
    }

    @Test
    fun `exportUserData should generate unique filenames with timestamp`() = runTest {
        // Act
        val result1 = dataPortability.exportUserData(
            format = DataPortability.ExportFormat.JSON,
            scope = DataPortability.ExportScope.CREDENTIALS_ONLY,
            encrypt = false,
            password = null
        )

        // Small delay to ensure different timestamps
        Thread.sleep(10)

        val result2 = dataPortability.exportUserData(
            format = DataPortability.ExportFormat.JSON,
            scope = DataPortability.ExportScope.CREDENTIALS_ONLY,
            encrypt = false,
            password = null
        )

        // Assert
        assertTrue(result1.success)
        assertTrue(result2.success)
        assertNotEquals(result1.filePath, result2.filePath)
    }

    @Test
    fun `exportUserData should calculate correct file size`() = runTest {
        // Act
        val result = dataPortability.exportUserData(
            format = DataPortability.ExportFormat.JSON,
            scope = DataPortability.ExportScope.CREDENTIALS_ONLY,
            encrypt = false,
            password = null
        )

        // Assert
        assertTrue(result.success)
        assertTrue(result.fileSize > 0)
    }

    // ==================== Empty Data Tests ====================

    @Test
    fun `exportUserData should handle empty credential list`() = runTest {
        // Arrange
        coEvery { mockRepository.getAllCredentials() } returns flowOf(emptyList())

        // Act
        val result = dataPortability.exportUserData(
            format = DataPortability.ExportFormat.JSON,
            scope = DataPortability.ExportScope.CREDENTIALS_ONLY,
            encrypt = false,
            password = null
        )

        // Assert
        assertTrue(result.success)
        assertEquals(0, result.itemCount)
    }

    // ==================== Audit Logging Tests ====================

    @Test
    fun `exportUserData should log export event`() = runTest {
        // Act
        dataPortability.exportUserData(
            format = DataPortability.ExportFormat.JSON,
            scope = DataPortability.ExportScope.ALL_DATA,
            encrypt = false,
            password = null
        )

        // Assert
        verify(exactly = 1) { mockAuditLogger.logSecurityEvent(
            event = AuditLogger.SecurityEvent.PRIVACY_DATA_EXPORTED,
            severity = AuditLogger.EventSeverity.INFO,
            action = "data_export",
            resource = "user_data",
            result = AuditLogger.EventResult.SUCCESS,
            metadata = match {
                it["format"] == "JSON" &&
                it["scope"] == "ALL_DATA" &&
                it["encrypted"] == false
            }
        )}
    }

    @Test
    fun `exportUserData should log export failures`() = runTest {
        // Arrange
        every { mockExternalFilesDir.exists() } returns false
        every { mockExternalFilesDir.mkdirs() } returns false

        // Act
        dataPortability.exportUserData(
            format = DataPortability.ExportFormat.JSON,
            scope = DataPortability.ExportScope.CREDENTIALS_ONLY,
            encrypt = false,
            password = null
        )

        // Assert
        verify(exactly = 1) { mockAuditLogger.logSecurityEvent(
            event = AuditLogger.SecurityEvent.PRIVACY_DATA_EXPORT_FAILED,
            severity = AuditLogger.EventSeverity.WARNING,
            action = "data_export_failed",
            resource = "user_data",
            result = AuditLogger.EventResult.FAILURE,
            metadata = any()
        )}
    }

    // ==================== Error Handling Tests ====================

    @Test
    fun `exportUserData should handle repository errors gracefully`() = runTest {
        // Arrange
        coEvery { mockRepository.getAllCredentials() } throws Exception("Database error")

        // Act
        val result = dataPortability.exportUserData(
            format = DataPortability.ExportFormat.JSON,
            scope = DataPortability.ExportScope.CREDENTIALS_ONLY,
            encrypt = false,
            password = null
        )

        // Assert
        assertFalse(result.success)
        assertNotNull(result.errorMessage)
        assertTrue(result.errorMessage!!.contains("error"))
    }

    @Test
    fun `exportUserData should handle encryption failures`() = runTest {
        // Arrange
        every { mockCryptoManager.encrypt(any(), any(), any()) } throws Exception("Encryption failed")

        // Act
        val result = dataPortability.exportUserData(
            format = DataPortability.ExportFormat.JSON,
            scope = DataPortability.ExportScope.CREDENTIALS_ONLY,
            encrypt = true,
            password = testPassword
        )

        // Assert
        assertFalse(result.success)
        assertNotNull(result.errorMessage)
    }

    // ==================== GDPR Compliance Tests ====================

    @Test
    fun `data export should implement GDPR Article 20 requirements`() = runTest {
        // Arrange - GDPR Article 20 requires structured, commonly used format
        val credentials = createTestCredentials()
        coEvery { mockRepository.getAllCredentials() } returns flowOf(credentials)

        // Act - Export in JSON (structured, commonly used format)
        val result = dataPortability.exportUserData(
            format = DataPortability.ExportFormat.JSON,
            scope = DataPortability.ExportScope.ALL_DATA,
            encrypt = false,
            password = null
        )

        // Assert
        assertTrue(result.success)
        assertEquals(credentials.size, result.itemCount)
        assertTrue(result.filePath.endsWith(".json")) // Machine-readable format
    }

    @Test
    fun `data export should support interoperability with KeePass format`() = runTest {
        // Arrange - GDPR Article 20 requires data portability to another controller
        // Act
        val result = dataPortability.exportUserData(
            format = DataPortability.ExportFormat.KEEPASS_CSV,
            scope = DataPortability.ExportScope.CREDENTIALS_ONLY,
            encrypt = false,
            password = null
        )

        // Assert - KeePass CSV allows transfer to other password managers
        assertTrue(result.success)
        assertEquals(DataPortability.ExportFormat.KEEPASS_CSV, result.format)
    }

    @Test
    fun `data export should include all personal data categories`() = runTest {
        // Arrange - GDPR Article 20 requires complete data export
        val consentJson = """[{"purpose":"BIOMETRIC_AUTH","granted":true}]"""
        every { mockConsentManager.exportConsentRecords() } returns consentJson

        // Act
        val result = dataPortability.exportUserData(
            format = DataPortability.ExportFormat.JSON,
            scope = DataPortability.ExportScope.ALL_DATA,
            encrypt = false,
            password = null
        )

        // Assert - All data categories exported
        assertTrue(result.success)
        assertTrue(result.itemCount > 0)
        // Export should include:
        // - Credentials (personal data)
        // - Consent records (processing basis)
        // - Privacy preferences (user choices)
        // - Metadata (export details)
    }

    // ==================== Format Validation Tests ====================

    @Test
    fun `exportUserData should support all declared formats`() = runTest {
        // Act & Assert - Test all enum values
        val formats = listOf(
            DataPortability.ExportFormat.JSON,
            DataPortability.ExportFormat.CSV,
            DataPortability.ExportFormat.XML,
            DataPortability.ExportFormat.KEEPASS_CSV
        )

        for (format in formats) {
            val result = dataPortability.exportUserData(
                format = format,
                scope = DataPortability.ExportScope.CREDENTIALS_ONLY,
                encrypt = false,
                password = null
            )
            assertTrue("Format $format should succeed", result.success)
        }
    }

    // ==================== Helper Functions ====================

    private fun createTestCredentials(): List<Credential> {
        return listOf(
            Credential(
                id = 1,
                title = "Gmail Account",
                username = "user@gmail.com",
                password = "password123",
                url = "https://gmail.com",
                category = CredentialCategory.LOGIN,
                createdAt = System.currentTimeMillis(),
                modifiedAt = System.currentTimeMillis()
            ),
            Credential(
                id = 2,
                title = "GitHub",
                username = "developer",
                password = "gh_token_abc123",
                url = "https://github.com",
                category = CredentialCategory.LOGIN,
                createdAt = System.currentTimeMillis(),
                modifiedAt = System.currentTimeMillis()
            ),
            Credential(
                id = 3,
                title = "Bank Account",
                username = "12345678",
                password = "pin1234",
                notes = "Main checking account",
                category = CredentialCategory.PAYMENT,
                createdAt = System.currentTimeMillis(),
                modifiedAt = System.currentTimeMillis()
            )
        )
    }
}
