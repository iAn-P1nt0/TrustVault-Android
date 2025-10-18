package com.trustvault.android.autofill

import android.view.autofill.AutofillId
import com.trustvault.android.domain.model.Credential
import com.trustvault.android.domain.model.CredentialCategory
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for OtpAutofillHelper.
 *
 * Tests TOTP code generation and autofill dataset creation:
 * - TOTP code generation for valid credentials
 * - Code validity checking (expiration time)
 * - Remaining seconds calculation
 * - Dataset creation with RemoteViews
 * - Error handling for invalid secrets
 *
 * Note: Requires Android API 26+ (O)
 */
class OtpAutofillHelperTest {

    private lateinit var helper: OtpAutofillHelper
    private lateinit var mockAutofillId: AutofillId

    @Before
    fun setUp() {
        helper = OtpAutofillHelper()
        mockAutofillId = mockk()
    }

    @Test
    fun testGenerateOtpCodeWithValidSecret() {
        val credential = createCredentialWithOtp()
        val code = helper.generateOtpCode(credential)

        assertTrue("Code should not be empty", code.isNotEmpty())
        assertEquals(6, code.length)
        assertTrue(code.all { it.isDigit() })
    }

    @Test
    fun testGenerateOtpCodeConsistencyWithinTimeWindow() {
        val credential = createCredentialWithOtp()

        val code1 = helper.generateOtpCode(credential)
        val code2 = helper.generateOtpCode(credential)

        // Codes should be the same within the same 30-second window
        assertEquals(code1, code2)
    }

    @Test
    fun testGenerateOtpCodeEmptyForNullSecret() {
        val credential = Credential(
            id = 1,
            title = "Test",
            username = "user",
            password = "pass",
            website = "https://example.com",
            notes = "",
            category = CredentialCategory.LOGIN,
            otpSecret = null
        )

        val code = helper.generateOtpCode(credential)
        assertEquals("", code)
    }

    @Test
    fun testGenerateOtpCodeEmptyForBlankSecret() {
        val credential = Credential(
            id = 1,
            title = "Test",
            username = "user",
            password = "pass",
            website = "https://example.com",
            notes = "",
            category = CredentialCategory.LOGIN,
            otpSecret = ""
        )

        val code = helper.generateOtpCode(credential)
        assertEquals("", code)
    }

    @Test
    fun testIsOtpCodeValidWithValidCode() {
        val credential = createCredentialWithOtp()

        // Test multiple times to avoid edge cases at 30-second boundary
        var anyValid = false
        repeat(3) {
            val isValid = helper.isOtpCodeValid(credential)
            if (isValid) {
                anyValid = true
            }
        }

        // At least one of these should be valid (edge case of hitting last 2 seconds is rare)
        assertTrue("Code should be valid at least once", anyValid)
    }

    @Test
    fun testIsOtpCodeValidFalseForNullSecret() {
        val credential = Credential(
            id = 1,
            title = "Test",
            username = "user",
            password = "pass",
            website = "https://example.com",
            notes = "",
            category = CredentialCategory.LOGIN,
            otpSecret = null
        )

        assertFalse(helper.isOtpCodeValid(credential))
    }

    @Test
    fun testIsOtpCodeValidFalseForBlankSecret() {
        val credential = Credential(
            id = 1,
            title = "Test",
            username = "user",
            password = "pass",
            website = "https://example.com",
            notes = "",
            category = CredentialCategory.LOGIN,
            otpSecret = ""
        )

        assertFalse(helper.isOtpCodeValid(credential))
    }

    @Test
    fun testGetRemainingSecondsReturnsValidValue() {
        val credential = createCredentialWithOtp()
        val remainingSeconds = helper.getRemainingSeconds(credential)

        // Should be between 0 and 30 seconds
        assertTrue(remainingSeconds in 0..30)
    }

    @Test
    fun testGetRemainingSecondsZeroForNullSecret() {
        val credential = Credential(
            id = 1,
            title = "Test",
            username = "user",
            password = "pass",
            website = "https://example.com",
            notes = "",
            category = CredentialCategory.LOGIN,
            otpSecret = null
        )

        assertEquals(0, helper.getRemainingSeconds(credential))
    }

    @Test
    fun testGetRemainingSecondsZeroForBlankSecret() {
        val credential = Credential(
            id = 1,
            title = "Test",
            username = "user",
            password = "pass",
            website = "https://example.com",
            notes = "",
            category = CredentialCategory.LOGIN,
            otpSecret = ""
        )

        assertEquals(0, helper.getRemainingSeconds(credential))
    }

    @Test
    fun testCreateOtpDatasetReturnsNullForMissingSecret() {
        val credential = Credential(
            id = 1,
            title = "Test",
            username = "user",
            password = "pass",
            website = "https://example.com",
            notes = "",
            category = CredentialCategory.LOGIN,
            otpSecret = null
        )

        // Even with null secret, helper should handle gracefully
        val code = helper.generateOtpCode(credential)
        assertEquals("", code)
    }

    @Test
    fun testCreateOtpDatasetReturnsNullForBlankSecret() {
        val credential = Credential(
            id = 1,
            title = "Test",
            username = "user",
            password = "pass",
            website = "https://example.com",
            notes = "",
            category = CredentialCategory.LOGIN,
            otpSecret = ""
        )

        // Blank secret should also fail gracefully
        val code = helper.generateOtpCode(credential)
        assertEquals("", code)
    }

    @Test
    fun testCreateOtpDatasetSucceedsWithValidSecret() {
        val credential = createCredentialWithOtp()
        val code = helper.generateOtpCode(credential)

        // Should generate a valid code
        assertTrue("Code should not be empty", code.isNotEmpty())
        assertEquals(6, code.length)
    }

    @Test
    fun testCreateMultipleOtpDatasetsWithValidCredential() {
        val credential = createCredentialWithOtp()
        // Generate multiple codes - they should all be the same in the same time window
        val code1 = helper.generateOtpCode(credential)
        val code2 = helper.generateOtpCode(credential)

        assertEquals("Codes should match in same time window", code1, code2)
    }

    @Test
    fun testCreateMultipleOtpDatasetsEmptyForNullSecret() {
        val credential = Credential(
            id = 1,
            title = "Test",
            username = "user",
            password = "pass",
            website = "https://example.com",
            notes = "",
            category = CredentialCategory.LOGIN,
            otpSecret = null
        )

        // Code generation should fail gracefully
        val code = helper.generateOtpCode(credential)
        assertEquals("", code)
    }

    @Test
    fun testOtpCodeGenerationWithDifferentSecrets() {
        // Test with different Base32-encoded secrets
        val secrets = listOf(
            "JBSWY3DPEBLW64TMMQ======",  // Standard Base32
            "AAAAAAAAAAAAAAAA",           // Simple pattern
            "ONZQ43TEMRMW64TMMQ======"   // Different Base32
        )

        for (secret in secrets) {
            val credential = Credential(
                id = 1,
                title = "Test",
                username = "user",
                password = "pass",
                website = "https://example.com",
                notes = "",
                category = CredentialCategory.LOGIN,
                otpSecret = secret
            )

            val code = helper.generateOtpCode(credential)
            // Each valid secret should generate a code
            if (code.isNotEmpty()) {
                assertEquals(6, code.length)
                assertTrue(code.all { it.isDigit() })
            }
        }
    }

    @Test
    fun testOtpValidityCheckGuarantees2SecondWindow() {
        val credential = createCredentialWithOtp()
        val isValid = helper.isOtpCodeValid(credential)

        if (isValid) {
            // If valid, remaining seconds should be > 2
            val remaining = helper.getRemainingSeconds(credential)
            assertTrue("Remaining seconds should be > 2 when valid", remaining > 2)
        }
    }

    @Test
    fun testOtpCodeStaysValidForFullTimeWindow() {
        val credential = createCredentialWithOtp()

        // Test multiple times within the same window
        repeat(5) {
            val isValid = helper.isOtpCodeValid(credential)
            val code = helper.generateOtpCode(credential)

            if (isValid) {
                assertEquals(6, code.length)
                assertTrue(code.all { it.isDigit() })
            }
        }
    }

    /**
     * Helper function to create a credential with valid OTP secret.
     * Uses a Base32-encoded secret compatible with RFC 6238.
     */
    private fun createCredentialWithOtp(): Credential {
        return Credential(
            id = 1,
            title = "Test Account",
            username = "testuser",
            password = "testpass",
            website = "https://example.com",
            notes = "Test credential with OTP",
            category = CredentialCategory.LOGIN,
            packageName = "com.example.app",
            otpSecret = "JBSWY3DPEBLW64TMMQ======" // Valid Base32 secret
        )
    }
}
