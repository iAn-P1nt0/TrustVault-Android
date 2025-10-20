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
 * Unit tests for DataRetentionManager.
 *
 * Tests GDPR Article 5(1)(e) and Article 25 compliance:
 * - Automated data retention policies
 * - Expired credential detection
 * - Auto-deletion with favorite exemption
 * - Dry-run mode for preview
 * - Audit logging of deletions
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DataRetentionManagerTest {

    private lateinit var dataRetentionManager: DataRetentionManager
    private lateinit var mockContext: Context
    private lateinit var mockRepository: CredentialRepository
    private lateinit var mockPreferences: PreferencesManager
    private lateinit var mockAuditLogger: AuditLogger

    private val currentTime = System.currentTimeMillis()
    private val oneDayAgo = currentTime - TimeUnit.DAYS.toMillis(1)
    private val thirtyDaysAgo = currentTime - TimeUnit.DAYS.toMillis(30)
    private val ninetyDaysAgo = currentTime - TimeUnit.DAYS.toMillis(90)
    private val oneYearAgo = currentTime - TimeUnit.DAYS.toMillis(365)

    @Before
    fun setup() {
        mockContext = mockk(relaxed = true)
        mockRepository = mockk(relaxed = true)
        mockPreferences = mockk(relaxed = true)
        mockAuditLogger = mockk(relaxed = true)

        // Default mock behaviors
        every { mockPreferences.getRetentionPolicyDays() } returns 90
        every { mockPreferences.isAutoDeleteEnabled() } returns false
        every { mockPreferences.exemptFavoritesFromAutoDelete() } returns false
        coEvery { mockRepository.getAllCredentials() } returns flowOf(emptyList())

        dataRetentionManager = DataRetentionManager(
            context = mockContext,
            credentialRepository = mockRepository,
            preferencesManager = mockPreferences,
            auditLogger = mockAuditLogger
        )
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    // ==================== Expired Credential Detection Tests ====================

    @Test
    fun `findExpiredCredentials should return credentials older than retention period`() = runTest {
        // Arrange
        val credentials = listOf(
            createCredential(id = 1, title = "Recent", modifiedAt = oneDayAgo),
            createCredential(id = 2, title = "Old", modifiedAt = oneYearAgo),
            createCredential(id = 3, title = "Ancient", modifiedAt = oneYearAgo - TimeUnit.DAYS.toMillis(100))
        )
        coEvery { mockRepository.getAllCredentials() } returns flowOf(credentials)
        every { mockPreferences.getRetentionPolicyDays() } returns 90

        // Act
        val expiredCredentials = dataRetentionManager.findExpiredCredentials()

        // Assert
        assertEquals(2, expiredCredentials.size)
        assertTrue(expiredCredentials.any { it.title == "Old" })
        assertTrue(expiredCredentials.any { it.title == "Ancient" })
        assertFalse(expiredCredentials.any { it.title == "Recent" })
    }

    @Test
    fun `findExpiredCredentials should respect 30-day retention policy`() = runTest {
        // Arrange
        val credentials = listOf(
            createCredential(id = 1, title = "Recent", modifiedAt = oneDayAgo),
            createCredential(id = 2, title = "29 days old", modifiedAt = currentTime - TimeUnit.DAYS.toMillis(29)),
            createCredential(id = 3, title = "31 days old", modifiedAt = currentTime - TimeUnit.DAYS.toMillis(31)),
            createCredential(id = 4, title = "60 days old", modifiedAt = currentTime - TimeUnit.DAYS.toMillis(60))
        )
        coEvery { mockRepository.getAllCredentials() } returns flowOf(credentials)
        every { mockPreferences.getRetentionPolicyDays() } returns 30

        // Act
        val expiredCredentials = dataRetentionManager.findExpiredCredentials()

        // Assert
        assertEquals(2, expiredCredentials.size)
        assertTrue(expiredCredentials.any { it.title == "31 days old" })
        assertTrue(expiredCredentials.any { it.title == "60 days old" })
    }

    @Test
    fun `findExpiredCredentials should handle unlimited retention (-1)`() = runTest {
        // Arrange
        val credentials = listOf(
            createCredential(id = 1, title = "Very Old", modifiedAt = oneYearAgo - TimeUnit.DAYS.toMillis(1000))
        )
        coEvery { mockRepository.getAllCredentials() } returns flowOf(credentials)
        every { mockPreferences.getRetentionPolicyDays() } returns -1 // Unlimited

        // Act
        val expiredCredentials = dataRetentionManager.findExpiredCredentials()

        // Assert
        assertEquals(0, expiredCredentials.size) // Nothing expires with unlimited retention
    }

    @Test
    fun `findExpiredCredentials should exempt favorites when configured`() = runTest {
        // Arrange
        val credentials = listOf(
            createCredential(id = 1, title = "Old Favorite", modifiedAt = oneYearAgo, isFavorite = true),
            createCredential(id = 2, title = "Old Non-Favorite", modifiedAt = oneYearAgo, isFavorite = false)
        )
        coEvery { mockRepository.getAllCredentials() } returns flowOf(credentials)
        every { mockPreferences.getRetentionPolicyDays() } returns 90
        every { mockPreferences.exemptFavoritesFromAutoDelete() } returns true

        // Act
        val expiredCredentials = dataRetentionManager.findExpiredCredentials()

        // Assert
        assertEquals(1, expiredCredentials.size)
        assertEquals("Old Non-Favorite", expiredCredentials.first().title)
    }

    @Test
    fun `findExpiredCredentials should include favorites when exemption disabled`() = runTest {
        // Arrange
        val credentials = listOf(
            createCredential(id = 1, title = "Old Favorite", modifiedAt = oneYearAgo, isFavorite = true),
            createCredential(id = 2, title = "Old Non-Favorite", modifiedAt = oneYearAgo, isFavorite = false)
        )
        coEvery { mockRepository.getAllCredentials() } returns flowOf(credentials)
        every { mockPreferences.getRetentionPolicyDays() } returns 90
        every { mockPreferences.exemptFavoritesFromAutoDelete() } returns false

        // Act
        val expiredCredentials = dataRetentionManager.findExpiredCredentials()

        // Assert
        assertEquals(2, expiredCredentials.size)
    }

    // ==================== Retention Policy Enforcement Tests ====================

    @Test
    fun `enforceRetentionPolicy should delete expired credentials when auto-delete enabled`() = runTest {
        // Arrange
        val expiredCredentials = listOf(
            createCredential(id = 1, title = "Expired1", modifiedAt = oneYearAgo),
            createCredential(id = 2, title = "Expired2", modifiedAt = oneYearAgo)
        )
        coEvery { mockRepository.getAllCredentials() } returns flowOf(expiredCredentials)
        every { mockPreferences.getRetentionPolicyDays() } returns 90
        every { mockPreferences.isAutoDeleteEnabled() } returns true
        coEvery { mockRepository.deleteCredential(any()) } just Runs

        // Act
        val result = dataRetentionManager.enforceRetentionPolicy(dryRun = false)

        // Assert
        assertTrue(result.success)
        assertEquals(2, result.deletedCount)
        assertEquals(0, result.exemptedCount)
        assertTrue(result.deletedItems.contains("Expired1"))
        assertTrue(result.deletedItems.contains("Expired2"))
        coVerify(exactly = 2) { mockRepository.deleteCredential(any()) }
    }

    @Test
    fun `enforceRetentionPolicy should not delete when auto-delete disabled`() = runTest {
        // Arrange
        val expiredCredentials = listOf(
            createCredential(id = 1, title = "Expired", modifiedAt = oneYearAgo)
        )
        coEvery { mockRepository.getAllCredentials() } returns flowOf(expiredCredentials)
        every { mockPreferences.getRetentionPolicyDays() } returns 90
        every { mockPreferences.isAutoDeleteEnabled() } returns false

        // Act
        val result = dataRetentionManager.enforceRetentionPolicy(dryRun = false)

        // Assert
        assertFalse(result.success)
        assertEquals(0, result.deletedCount)
        coVerify(exactly = 0) { mockRepository.deleteCredential(any()) }
    }

    @Test
    fun `enforceRetentionPolicy should not delete in dry-run mode`() = runTest {
        // Arrange
        val expiredCredentials = listOf(
            createCredential(id = 1, title = "Expired", modifiedAt = oneYearAgo)
        )
        coEvery { mockRepository.getAllCredentials() } returns flowOf(expiredCredentials)
        every { mockPreferences.getRetentionPolicyDays() } returns 90
        every { mockPreferences.isAutoDeleteEnabled() } returns true

        // Act
        val result = dataRetentionManager.enforceRetentionPolicy(dryRun = true)

        // Assert
        assertTrue(result.success)
        assertEquals(1, result.deletedCount) // Counted, but not deleted
        coVerify(exactly = 0) { mockRepository.deleteCredential(any()) } // No actual deletion
    }

    @Test
    fun `enforceRetentionPolicy should exempt favorites and track count`() = runTest {
        // Arrange
        val credentials = listOf(
            createCredential(id = 1, title = "Expired Favorite", modifiedAt = oneYearAgo, isFavorite = true),
            createCredential(id = 2, title = "Expired Non-Favorite", modifiedAt = oneYearAgo, isFavorite = false)
        )
        coEvery { mockRepository.getAllCredentials() } returns flowOf(credentials)
        every { mockPreferences.getRetentionPolicyDays() } returns 90
        every { mockPreferences.isAutoDeleteEnabled() } returns true
        every { mockPreferences.exemptFavoritesFromAutoDelete() } returns true
        coEvery { mockRepository.deleteCredential(any()) } just Runs

        // Act
        val result = dataRetentionManager.enforceRetentionPolicy(dryRun = false)

        // Assert
        assertTrue(result.success)
        assertEquals(1, result.deletedCount)
        assertEquals(1, result.exemptedCount)
        assertTrue(result.deletedItems.contains("Expired Non-Favorite"))
        assertFalse(result.deletedItems.contains("Expired Favorite"))
    }

    @Test
    fun `enforceRetentionPolicy should log audit events for deletions`() = runTest {
        // Arrange
        val expiredCredentials = listOf(
            createCredential(id = 1, title = "Expired1", modifiedAt = oneYearAgo),
            createCredential(id = 2, title = "Expired2", modifiedAt = oneYearAgo)
        )
        coEvery { mockRepository.getAllCredentials() } returns flowOf(expiredCredentials)
        every { mockPreferences.getRetentionPolicyDays() } returns 90
        every { mockPreferences.isAutoDeleteEnabled() } returns true
        coEvery { mockRepository.deleteCredential(any()) } just Runs

        // Act
        dataRetentionManager.enforceRetentionPolicy(dryRun = false)

        // Assert
        verify(exactly = 2) { mockAuditLogger.logSecurityEvent(
            event = AuditLogger.SecurityEvent.DATA_DELETED,
            severity = AuditLogger.EventSeverity.INFO,
            action = "retention_policy_deletion",
            resource = match { it.startsWith("credential:") },
            result = AuditLogger.EventResult.SUCCESS,
            metadata = any()
        )}
    }

    // ==================== Edge Cases and Error Handling ====================

    @Test
    fun `enforceRetentionPolicy should handle empty credential list`() = runTest {
        // Arrange
        coEvery { mockRepository.getAllCredentials() } returns flowOf(emptyList())
        every { mockPreferences.isAutoDeleteEnabled() } returns true

        // Act
        val result = dataRetentionManager.enforceRetentionPolicy(dryRun = false)

        // Assert
        assertTrue(result.success)
        assertEquals(0, result.deletedCount)
        assertEquals(0, result.exemptedCount)
    }

    @Test
    fun `enforceRetentionPolicy should handle deletion failures gracefully`() = runTest {
        // Arrange
        val expiredCredentials = listOf(
            createCredential(id = 1, title = "Expired1", modifiedAt = oneYearAgo),
            createCredential(id = 2, title = "Expired2", modifiedAt = oneYearAgo)
        )
        coEvery { mockRepository.getAllCredentials() } returns flowOf(expiredCredentials)
        every { mockPreferences.getRetentionPolicyDays() } returns 90
        every { mockPreferences.isAutoDeleteEnabled() } returns true
        coEvery { mockRepository.deleteCredential(match { it.id == 1L }) } throws Exception("Database error")
        coEvery { mockRepository.deleteCredential(match { it.id == 2L }) } just Runs

        // Act
        val result = dataRetentionManager.enforceRetentionPolicy(dryRun = false)

        // Assert
        assertFalse(result.success) // Failed due to one error
        assertEquals(1, result.deletedCount) // One successful deletion
        assertTrue(result.deletedItems.contains("Expired2"))
        assertFalse(result.deletedItems.contains("Expired1"))
    }

    @Test
    fun `findExpiredCredentials should handle repository errors`() = runTest {
        // Arrange
        coEvery { mockRepository.getAllCredentials() } throws Exception("Database error")

        // Act
        val expiredCredentials = dataRetentionManager.findExpiredCredentials()

        // Assert
        assertTrue(expiredCredentials.isEmpty())
    }

    // ==================== Retention Period Validation Tests ====================

    @Test
    fun `isValidRetentionPeriod should accept valid periods`() {
        // Act & Assert
        assertTrue(dataRetentionManager.isValidRetentionPeriod(30))
        assertTrue(dataRetentionManager.isValidRetentionPeriod(90))
        assertTrue(dataRetentionManager.isValidRetentionPeriod(180))
        assertTrue(dataRetentionManager.isValidRetentionPeriod(365))
        assertTrue(dataRetentionManager.isValidRetentionPeriod(730))
        assertTrue(dataRetentionManager.isValidRetentionPeriod(-1)) // Unlimited
    }

    @Test
    fun `isValidRetentionPeriod should reject invalid periods`() {
        // Act & Assert
        assertFalse(dataRetentionManager.isValidRetentionPeriod(15))
        assertFalse(dataRetentionManager.isValidRetentionPeriod(45))
        assertFalse(dataRetentionManager.isValidRetentionPeriod(100))
        assertFalse(dataRetentionManager.isValidRetentionPeriod(1000))
    }

    // ==================== Batch Deletion Tests ====================

    @Test
    fun `enforceRetentionPolicy should handle large batches efficiently`() = runTest {
        // Arrange
        val largeCredentialList = (1..100).map { id ->
            createCredential(id = id.toLong(), title = "Expired$id", modifiedAt = oneYearAgo)
        }
        coEvery { mockRepository.getAllCredentials() } returns flowOf(largeCredentialList)
        every { mockPreferences.getRetentionPolicyDays() } returns 90
        every { mockPreferences.isAutoDeleteEnabled() } returns true
        coEvery { mockRepository.deleteCredential(any()) } just Runs

        // Act
        val result = dataRetentionManager.enforceRetentionPolicy(dryRun = false)

        // Assert
        assertTrue(result.success)
        assertEquals(100, result.deletedCount)
        coVerify(exactly = 100) { mockRepository.deleteCredential(any()) }
    }

    // ==================== GDPR Compliance Tests ====================

    @Test
    fun `retention policy should comply with GDPR Article 5(1)(e) storage limitation`() = runTest {
        // Arrange - GDPR requires data not kept longer than necessary
        val credentials = listOf(
            createCredential(id = 1, title = "Old", modifiedAt = oneYearAgo),
            createCredential(id = 2, title = "Recent", modifiedAt = oneDayAgo)
        )
        coEvery { mockRepository.getAllCredentials() } returns flowOf(credentials)
        every { mockPreferences.getRetentionPolicyDays() } returns 90
        every { mockPreferences.isAutoDeleteEnabled() } returns true
        coEvery { mockRepository.deleteCredential(any()) } just Runs

        // Act
        val result = dataRetentionManager.enforceRetentionPolicy(dryRun = false)

        // Assert - Only old data deleted, recent data retained
        assertTrue(result.success)
        assertEquals(1, result.deletedCount)
        assertTrue(result.deletedItems.contains("Old"))
    }

    @Test
    fun `dry-run mode should allow users to preview deletions before enforcement`() = runTest {
        // Arrange - GDPR transparency requirement
        val credentials = listOf(
            createCredential(id = 1, title = "WillBeDeleted", modifiedAt = oneYearAgo)
        )
        coEvery { mockRepository.getAllCredentials() } returns flowOf(credentials)
        every { mockPreferences.getRetentionPolicyDays() } returns 90
        every { mockPreferences.isAutoDeleteEnabled() } returns true

        // Act
        val dryRunResult = dataRetentionManager.enforceRetentionPolicy(dryRun = true)

        // Assert - User can see what will be deleted
        assertTrue(dryRunResult.success)
        assertEquals(1, dryRunResult.deletedCount)
        assertTrue(dryRunResult.deletedItems.contains("WillBeDeleted"))
        coVerify(exactly = 0) { mockRepository.deleteCredential(any()) } // No actual deletion
    }

    // ==================== Helper Functions ====================

    private fun createCredential(
        id: Long,
        title: String,
        modifiedAt: Long,
        isFavorite: Boolean = false
    ): Credential {
        return Credential(
            id = id,
            title = title,
            username = "user@example.com",
            password = "password",
            category = CredentialCategory.LOGIN,
            createdAt = modifiedAt,
            modifiedAt = modifiedAt,
            isFavorite = isFavorite
        )
    }
}
