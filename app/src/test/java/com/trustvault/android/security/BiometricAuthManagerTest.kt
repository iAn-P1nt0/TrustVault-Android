package com.trustvault.android.security

import android.content.Context
import androidx.biometric.BiometricManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for BiometricAuthManager.
 *
 * Tests verify:
 * - Biometric availability detection
 * - Status mapping from BiometricManager
 * - Proper handling of different hardware states
 */
class BiometricAuthManagerTest {

    private lateinit var mockContext: Context
    private lateinit var mockBiometricManager: BiometricManager
    private lateinit var biometricAuthManager: BiometricAuthManager

    @Before
    fun setup() {
        mockContext = mockk(relaxed = true)
        mockBiometricManager = mockk(relaxed = true)

        // Mock BiometricManager.from() static method
        mockkStatic(BiometricManager::class)
        every { BiometricManager.from(any()) } returns mockBiometricManager

        biometricAuthManager = BiometricAuthManager(mockContext)
    }

    @After
    fun teardown() {
        unmockkStatic(BiometricManager::class)
    }

    @Test
    fun `isBiometricAvailable returns AVAILABLE when biometric is ready`() {
        every {
            mockBiometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
        } returns BiometricManager.BIOMETRIC_SUCCESS

        val status = biometricAuthManager.isBiometricAvailable()

        assertEquals(BiometricStatus.AVAILABLE, status)
    }

    @Test
    fun `isBiometricAvailable returns NO_HARDWARE when no hardware`() {
        every {
            mockBiometricManager.canAuthenticate(any())
        } returns BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE

        val status = biometricAuthManager.isBiometricAvailable()

        assertEquals(BiometricStatus.NO_HARDWARE, status)
    }

    @Test
    fun `isBiometricAvailable returns UNAVAILABLE when hardware unavailable`() {
        every {
            mockBiometricManager.canAuthenticate(any())
        } returns BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE

        val status = biometricAuthManager.isBiometricAvailable()

        assertEquals(BiometricStatus.UNAVAILABLE, status)
    }

    @Test
    fun `isBiometricAvailable returns NOT_ENROLLED when no biometrics enrolled`() {
        every {
            mockBiometricManager.canAuthenticate(any())
        } returns BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED

        val status = biometricAuthManager.isBiometricAvailable()

        assertEquals(BiometricStatus.NOT_ENROLLED, status)
    }

    @Test
    fun `isBiometricAvailable returns SECURITY_UPDATE_REQUIRED when update needed`() {
        every {
            mockBiometricManager.canAuthenticate(any())
        } returns BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED

        val status = biometricAuthManager.isBiometricAvailable()

        assertEquals(BiometricStatus.SECURITY_UPDATE_REQUIRED, status)
    }

    @Test
    fun `isBiometricAvailable returns UNSUPPORTED when unsupported`() {
        every {
            mockBiometricManager.canAuthenticate(any())
        } returns BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED

        val status = biometricAuthManager.isBiometricAvailable()

        assertEquals(BiometricStatus.UNSUPPORTED, status)
    }

    @Test
    fun `isBiometricAvailable returns UNKNOWN for unknown status`() {
        every {
            mockBiometricManager.canAuthenticate(any())
        } returns BiometricManager.BIOMETRIC_STATUS_UNKNOWN

        val status = biometricAuthManager.isBiometricAvailable()

        assertEquals(BiometricStatus.UNKNOWN, status)
    }

    @Test
    fun `isBiometricAvailable returns UNKNOWN for unexpected status code`() {
        every {
            mockBiometricManager.canAuthenticate(any())
        } returns 999 // Unexpected code

        val status = biometricAuthManager.isBiometricAvailable()

        assertEquals(BiometricStatus.UNKNOWN, status)
    }

    @Test
    fun `BiometricStatus enum has all expected values`() {
        val expectedStatuses = setOf(
            BiometricStatus.AVAILABLE,
            BiometricStatus.NO_HARDWARE,
            BiometricStatus.UNAVAILABLE,
            BiometricStatus.NOT_ENROLLED,
            BiometricStatus.SECURITY_UPDATE_REQUIRED,
            BiometricStatus.UNSUPPORTED,
            BiometricStatus.UNKNOWN
        )

        val actualStatuses = BiometricStatus.values().toSet()

        assertEquals("BiometricStatus should have all expected values", expectedStatuses, actualStatuses)
    }
}
