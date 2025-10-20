package com.trustvault.android.compliance

import android.content.Context
import com.trustvault.android.domain.model.Credential
import com.trustvault.android.domain.model.CredentialCategory
import com.trustvault.android.domain.repository.CredentialRepository
import com.trustvault.android.security.BiometricAuthManager
import com.trustvault.android.security.DatabaseKeyManager
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
 * Unit tests for DataErasure.
 *
 * Tests GDPR Article 17 "Right to Erasure" (Right to be Forgotten) compliance:
 * - Complete data erasure workflow
 * - Credential deletion
 * - Master password removal
 * - Biometric data deletion
 * - Encryption key clearing
 * - Backup file deletion
 * - Preference clearing
 * - Cache clearing
 * - Audit log deletion
 * - Verification of erasure
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DataErasureTest {

    private lateinit var dataErasure: DataErasure
    private lateinit var mockContext: Context
    private lateinit var mockRepository: CredentialRepository
    private lateinit var mockPreferences: PreferencesManager
    private lateinit var mockDatabaseKeyManager: DatabaseKeyManager
    private lateinit var mockBiometricManager: BiometricAuthManager
    private lateinit var mockAuditLogger: AuditLogger
    private lateinit var mockConsentManager: ConsentManager

    private val testPassword = "test_password".toCharArray()

    @Before
    fun setup() {
        mockContext = mockk(relaxed = true)
        mockRepository = mockk(relaxed = true)
        mockPreferences = mockk(relaxed = true)
        mockDatabaseKeyManager = mockk(relaxed = true)
        mockBiometricManager = mockk(relaxed = true)
        mockAuditLogger = mockk(relaxed = true)
        mockConsentManager = mockk(relaxed = true)

        // Default mock behaviors
        coEvery { mockRepository.getAllCredentials() } returns flowOf(createTestCredentials())
        coEvery { mockRepository.deleteCredential(any()) } just Runs
        coEvery { mockRepository.deleteAllCredentials() } just Runs
        every { mockPreferences.clearAll() } just Runs
        every { mockPreferences.deleteMasterPasswordHash() } just Runs
        every { mockDatabaseKeyManager.clearKeys() } just Runs
        every { mockBiometricManager.removeBiometricKey() } just Runs
        every { mockConsentManager.revokeAllOptionalConsents() } returns 0

        // Mock file operations
        val mockFilesDir = mockk<File>(relaxed = true)
        every { mockContext.filesDir } returns mockFilesDir
        every { mockFilesDir.listFiles() } returns emptyArray()

        val mockCacheDir = mockk<File>(relaxed = true)
        every { mockContext.cacheDir } returns mockCacheDir
        every { mockCacheDir.listFiles() } returns emptyArray()

        dataErasure = DataErasure(
            context = mockContext,
            credentialRepository = mockRepository,
            preferencesManager = mockPreferences,
            databaseKeyManager = mockDatabaseKeyManager,
            biometricAuthManager = mockBiometricManager,
            auditLogger = mockAuditLogger,
            consentManager = mockConsentManager
        )
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    // ==================== Complete Erasure Tests ====================

    @Test
    fun `executeCompleteErasure should delete all user data successfully`() = runTest {
        // Act
        val result = dataErasure.executeCompleteErasure(
            deleteMasterPassword = true,
            deleteAuditLogs = true,
            masterPassword = testPassword
        )

        // Assert
        assertTrue(result.success)
        assertTrue(result.verificationPassed)
        assertTrue(result.itemsDeleted["credentials"]!! > 0)
        assertTrue(result.itemsDeleted.containsKey("master_password"))
        assertTrue(result.itemsDeleted.containsKey("encryption_keys"))
        assertTrue(result.itemsDeleted.containsKey("biometric_data"))
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `executeCompleteErasure should preserve master password when requested`() = runTest {
        // Act
        val result = dataErasure.executeCompleteErasure(
            deleteMasterPassword = false,
            deleteAuditLogs = true,
            masterPassword = testPassword
        )

        // Assert
        assertTrue(result.success)
        assertEquals(0, result.itemsDeleted["master_password"])
        verify(exactly = 0) { mockPreferences.deleteMasterPasswordHash() }
    }

    @Test
    fun `executeCompleteErasure should preserve audit logs when requested`() = runTest {
        // Act
        val result = dataErasure.executeCompleteErasure(
            deleteMasterPassword = true,
            deleteAuditLogs = false,
            masterPassword = testPassword
        )

        // Assert
        assertTrue(result.success)
        assertEquals(0, result.itemsDeleted["audit_logs"])
    }

    // ==================== Credential Deletion Tests ====================

    @Test
    fun `executeCompleteErasure should delete all credentials`() = runTest {
        // Arrange
        val testCredentials = createTestCredentials()
        coEvery { mockRepository.getAllCredentials() } returns flowOf(testCredentials)

        // Act
        val result = dataErasure.executeCompleteErasure(
            deleteMasterPassword = true,
            deleteAuditLogs = true,
            masterPassword = testPassword
        )

        // Assert
        assertEquals(testCredentials.size, result.itemsDeleted["credentials"])
        coVerify(exactly = 1) { mockRepository.deleteAllCredentials() }
    }

    @Test
    fun `executeCompleteErasure should handle empty credential list`() = runTest {
        // Arrange
        coEvery { mockRepository.getAllCredentials() } returns flowOf(emptyList())

        // Act
        val result = dataErasure.executeCompleteErasure(
            deleteMasterPassword = true,
            deleteAuditLogs = true,
            masterPassword = testPassword
        )

        // Assert
        assertTrue(result.success)
        assertEquals(0, result.itemsDeleted["credentials"])
    }

    // ==================== Master Password Deletion Tests ====================

    @Test
    fun `executeCompleteErasure should delete master password hash`() = runTest {
        // Act
        val result = dataErasure.executeCompleteErasure(
            deleteMasterPassword = true,
            deleteAuditLogs = true,
            masterPassword = testPassword
        )

        // Assert
        assertEquals(1, result.itemsDeleted["master_password"])
        verify(exactly = 1) { mockPreferences.deleteMasterPasswordHash() }
    }

    // ==================== Encryption Key Deletion Tests ====================

    @Test
    fun `executeCompleteErasure should clear all encryption keys`() = runTest {
        // Act
        val result = dataErasure.executeCompleteErasure(
            deleteMasterPassword = true,
            deleteAuditLogs = true,
            masterPassword = testPassword
        )

        // Assert
        assertTrue(result.itemsDeleted.containsKey("encryption_keys"))
        verify(exactly = 1) { mockDatabaseKeyManager.clearKeys() }
    }

    // ==================== Biometric Data Deletion Tests ====================

    @Test
    fun `executeCompleteErasure should remove biometric keys`() = runTest {
        // Act
        val result = dataErasure.executeCompleteErasure(
            deleteMasterPassword = true,
            deleteAuditLogs = true,
            masterPassword = testPassword
        )

        // Assert
        assertTrue(result.itemsDeleted.containsKey("biometric_data"))
        verify(exactly = 1) { mockBiometricManager.removeBiometricKey() }
    }

    // ==================== Backup File Deletion Tests ====================

    @Test
    fun `executeCompleteErasure should delete backup files`() = runTest {
        // Arrange
        val mockBackupFile = mockk<File>(relaxed = true)
        every { mockBackupFile.name } returns "backup_20250101.trustvault"
        every { mockBackupFile.delete() } returns true

        val mockFilesDir = mockk<File>(relaxed = true)
        every { mockContext.filesDir } returns mockFilesDir
        every { mockFilesDir.listFiles() } returns arrayOf(mockBackupFile)

        // Act
        val result = dataErasure.executeCompleteErasure(
            deleteMasterPassword = true,
            deleteAuditLogs = true,
            masterPassword = testPassword
        )

        // Assert
        assertTrue(result.itemsDeleted["backup_files"]!! > 0)
        verify(exactly = 1) { mockBackupFile.delete() }
    }

    @Test
    fun `executeCompleteErasure should handle backup file deletion failures`() = runTest {
        // Arrange
        val mockBackupFile = mockk<File>(relaxed = true)
        every { mockBackupFile.name } returns "backup.trustvault"
        every { mockBackupFile.delete() } returns false // Deletion fails

        val mockFilesDir = mockk<File>(relaxed = true)
        every { mockContext.filesDir } returns mockFilesDir
        every { mockFilesDir.listFiles() } returns arrayOf(mockBackupFile)

        // Act
        val result = dataErasure.executeCompleteErasure(
            deleteMasterPassword = true,
            deleteAuditLogs = true,
            masterPassword = testPassword
        )

        // Assert
        assertFalse(result.success) // Overall operation marked as failed
        assertTrue(result.errors.any { it.contains("backup") })
    }

    // ==================== Preferences Clearing Tests ====================

    @Test
    fun `executeCompleteErasure should clear all preferences`() = runTest {
        // Act
        val result = dataErasure.executeCompleteErasure(
            deleteMasterPassword = true,
            deleteAuditLogs = true,
            masterPassword = testPassword
        )

        // Assert
        assertTrue(result.itemsDeleted.containsKey("preferences"))
        verify(exactly = 1) { mockPreferences.clearAll() }
    }

    // ==================== Cache Clearing Tests ====================

    @Test
    fun `executeCompleteErasure should clear cache directory`() = runTest {
        // Arrange
        val mockCacheFile = mockk<File>(relaxed = true)
        every { mockCacheFile.delete() } returns true

        val mockCacheDir = mockk<File>(relaxed = true)
        every { mockContext.cacheDir } returns mockCacheDir
        every { mockCacheDir.listFiles() } returns arrayOf(mockCacheFile)

        // Act
        val result = dataErasure.executeCompleteErasure(
            deleteMasterPassword = true,
            deleteAuditLogs = true,
            masterPassword = testPassword
        )

        // Assert
        assertTrue(result.itemsDeleted["cache_files"]!! > 0)
        verify(exactly = 1) { mockCacheFile.delete() }
    }

    // ==================== Consent Clearing Tests ====================

    @Test
    fun `executeCompleteErasure should revoke all optional consents`() = runTest {
        // Arrange
        every { mockConsentManager.revokeAllOptionalConsents() } returns 3

        // Act
        val result = dataErasure.executeCompleteErasure(
            deleteMasterPassword = true,
            deleteAuditLogs = true,
            masterPassword = testPassword
        )

        // Assert
        assertEquals(3, result.itemsDeleted["consent_records"])
        verify(exactly = 1) { mockConsentManager.revokeAllOptionalConsents() }
    }

    // ==================== Verification Tests ====================

    @Test
    fun `executeCompleteErasure should verify database is empty after deletion`() = runTest {
        // Arrange
        coEvery { mockRepository.getAllCredentials() } returnsMany listOf(
            flowOf(createTestCredentials()), // Before deletion
            flowOf(emptyList()) // After deletion
        )

        // Act
        val result = dataErasure.executeCompleteErasure(
            deleteMasterPassword = true,
            deleteAuditLogs = true,
            masterPassword = testPassword
        )

        // Assert
        assertTrue(result.verificationPassed)
        coVerify(exactly = 2) { mockRepository.getAllCredentials() } // Once before, once for verification
    }

    @Test
    fun `executeCompleteErasure should fail verification if credentials remain`() = runTest {
        // Arrange
        coEvery { mockRepository.getAllCredentials() } returns flowOf(createTestCredentials())
        coEvery { mockRepository.deleteAllCredentials() } just Runs // Deletion "succeeds" but data remains

        // Act
        val result = dataErasure.executeCompleteErasure(
            deleteMasterPassword = true,
            deleteAuditLogs = true,
            masterPassword = testPassword
        )

        // Assert
        assertFalse(result.verificationPassed)
        assertTrue(result.errors.any { it.contains("verification") })
    }

    // ==================== Audit Logging Tests ====================

    @Test
    fun `executeCompleteErasure should log erasure start event`() = runTest {
        // Act
        dataErasure.executeCompleteErasure(
            deleteMasterPassword = true,
            deleteAuditLogs = false,
            masterPassword = testPassword
        )

        // Assert
        verify(exactly = 1) { mockAuditLogger.logSecurityEvent(
            event = AuditLogger.SecurityEvent.PRIVACY_ERASURE_INITIATED,
            severity = AuditLogger.EventSeverity.CRITICAL,
            action = "complete_erasure_started",
            resource = "user_data",
            result = AuditLogger.EventResult.SUCCESS,
            metadata = match {
                it["delete_master_password"] == true &&
                it["delete_audit_logs"] == false
            }
        )}
    }

    @Test
    fun `executeCompleteErasure should log completion event`() = runTest {
        // Act
        dataErasure.executeCompleteErasure(
            deleteMasterPassword = true,
            deleteAuditLogs = false,
            masterPassword = testPassword
        )

        // Assert
        verify(exactly = 1) { mockAuditLogger.logSecurityEvent(
            event = AuditLogger.SecurityEvent.PRIVACY_ERASURE_COMPLETED,
            severity = AuditLogger.EventSeverity.CRITICAL,
            action = "complete_erasure_finished",
            resource = "user_data",
            result = AuditLogger.EventResult.SUCCESS,
            metadata = match { it.containsKey("items_deleted") }
        )}
    }

    // ==================== Error Handling Tests ====================

    @Test
    fun `executeCompleteErasure should handle credential deletion failures`() = runTest {
        // Arrange
        coEvery { mockRepository.deleteAllCredentials() } throws Exception("Database locked")

        // Act
        val result = dataErasure.executeCompleteErasure(
            deleteMasterPassword = true,
            deleteAuditLogs = true,
            masterPassword = testPassword
        )

        // Assert
        assertFalse(result.success)
        assertTrue(result.errors.any { it.contains("credentials") })
    }

    @Test
    fun `executeCompleteErasure should continue despite individual component failures`() = runTest {
        // Arrange
        every { mockBiometricManager.removeBiometricKey() } throws Exception("Biometric error")

        // Act
        val result = dataErasure.executeCompleteErasure(
            deleteMasterPassword = true,
            deleteAuditLogs = true,
            masterPassword = testPassword
        )

        // Assert
        assertFalse(result.success) // Overall operation failed
        assertTrue(result.errors.any { it.contains("biometric") })
        // But other components should still be deleted
        assertTrue(result.itemsDeleted["credentials"]!! > 0)
        verify(exactly = 1) { mockPreferences.deleteMasterPasswordHash() }
    }

    @Test
    fun `executeCompleteErasure should handle null filesDir gracefully`() = runTest {
        // Arrange
        every { mockContext.filesDir } returns null

        // Act
        val result = dataErasure.executeCompleteErasure(
            deleteMasterPassword = true,
            deleteAuditLogs = true,
            masterPassword = testPassword
        )

        // Assert
        // Should not crash, just skip file deletion
        assertTrue(result.itemsDeleted.containsKey("backup_files"))
    }

    // ==================== GDPR Compliance Tests ====================

    @Test
    fun `executeCompleteErasure should implement GDPR Article 17 Right to Erasure`() = runTest {
        // Arrange - GDPR requires complete erasure of personal data
        val credentials = createTestCredentials()
        coEvery { mockRepository.getAllCredentials() } returnsMany listOf(
            flowOf(credentials),
            flowOf(emptyList()) // Verification: all data gone
        )

        // Act
        val result = dataErasure.executeCompleteErasure(
            deleteMasterPassword = true,
            deleteAuditLogs = true,
            masterPassword = testPassword
        )

        // Assert - All personal data categories erased
        assertTrue(result.success)
        assertTrue(result.verificationPassed)
        assertTrue(result.itemsDeleted.containsKey("credentials"))
        assertTrue(result.itemsDeleted.containsKey("master_password"))
        assertTrue(result.itemsDeleted.containsKey("biometric_data"))
        assertTrue(result.itemsDeleted.containsKey("encryption_keys"))
        assertTrue(result.itemsDeleted.containsKey("backup_files"))
        assertTrue(result.itemsDeleted.containsKey("preferences"))
        assertTrue(result.itemsDeleted.containsKey("cache_files"))
    }

    @Test
    fun `executeCompleteErasure should provide detailed erasure report`() = runTest {
        // Arrange - GDPR transparency requirement
        val credentials = createTestCredentials()
        coEvery { mockRepository.getAllCredentials() } returns flowOf(credentials)

        // Act
        val result = dataErasure.executeCompleteErasure(
            deleteMasterPassword = true,
            deleteAuditLogs = true,
            masterPassword = testPassword
        )

        // Assert - Detailed report of what was deleted
        assertNotNull(result.itemsDeleted)
        assertTrue(result.itemsDeleted.size >= 7) // At least 7 categories tracked
        assertEquals(credentials.size, result.itemsDeleted["credentials"])
    }

    @Test
    fun `executeCompleteErasure should allow selective deletion`() = runTest {
        // Arrange - User control over what to delete
        // Act
        val result = dataErasure.executeCompleteErasure(
            deleteMasterPassword = false, // Keep master password
            deleteAuditLogs = false,      // Keep audit trail
            masterPassword = testPassword
        )

        // Assert
        assertTrue(result.success)
        assertEquals(0, result.itemsDeleted["master_password"])
        assertEquals(0, result.itemsDeleted["audit_logs"])
    }

    // ==================== Security Tests ====================

    @Test
    fun `executeCompleteErasure should clear sensitive data from memory`() = runTest {
        // Arrange
        val password = "sensitive_password".toCharArray()

        // Act
        dataErasure.executeCompleteErasure(
            deleteMasterPassword = true,
            deleteAuditLogs = true,
            masterPassword = password
        )

        // Assert - Password should be cleared after use
        assertTrue(password.all { it == '\u0000' })
    }

    // ==================== Helper Functions ====================

    private fun createTestCredentials(): List<Credential> {
        return listOf(
            Credential(
                id = 1,
                title = "Test Account 1",
                username = "user1@example.com",
                password = "password1",
                category = CredentialCategory.LOGIN,
                createdAt = System.currentTimeMillis(),
                modifiedAt = System.currentTimeMillis()
            ),
            Credential(
                id = 2,
                title = "Test Account 2",
                username = "user2@example.com",
                password = "password2",
                category = CredentialCategory.LOGIN,
                createdAt = System.currentTimeMillis(),
                modifiedAt = System.currentTimeMillis()
            ),
            Credential(
                id = 3,
                title = "Bank Account",
                username = "bank@example.com",
                password = "secure123",
                category = CredentialCategory.PAYMENT,
                createdAt = System.currentTimeMillis(),
                modifiedAt = System.currentTimeMillis()
            )
        )
    }
}
