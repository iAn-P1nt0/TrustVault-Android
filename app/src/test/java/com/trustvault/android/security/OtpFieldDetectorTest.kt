package com.trustvault.android.security

import android.view.accessibility.AccessibilityNodeInfo
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for OtpFieldDetector.
 *
 * Tests OTP field detection strategies:
 * - Autofill hints (OTP hint)
 * - Content descriptions (otp, code, 2fa, etc.)
 * - View ID patterns
 * - False positive exclusion (country_code, phone_code, etc.)
 */
class OtpFieldDetectorTest {

    @Test
    fun testIsOtpHintDetectsOtpKeyword() {
        val hints = arrayOf("otp")
        assertTrue(OtpFieldDetector.isOtpHint(hints))
    }

    @Test
    fun testIsOtpHintDetectsTotp() {
        val hints = arrayOf("totp")
        assertTrue(OtpFieldDetector.isOtpHint(hints))
    }

    @Test
    fun testIsOtpHintDetectsCode() {
        val hints = arrayOf("code")
        assertTrue(OtpFieldDetector.isOtpHint(hints))
    }

    @Test
    fun testIsOtpHintDetectsVerification() {
        val hints = arrayOf("verification")
        assertTrue(OtpFieldDetector.isOtpHint(hints))
    }

    @Test
    fun testIsOtpHintDetects2fa() {
        val hints = arrayOf("2fa")
        assertTrue(OtpFieldDetector.isOtpHint(hints))
    }

    @Test
    fun testIsOtpHintDetectsMfa() {
        val hints = arrayOf("mfa")
        assertTrue(OtpFieldDetector.isOtpHint(hints))
    }

    @Test
    fun testIsOtpHintCaseInsensitive() {
        val hints = arrayOf("OTP", "TOTP", "CODE")
        assertTrue(OtpFieldDetector.isOtpHint(hints))
    }

    @Test
    fun testIsOtpHintIgnoreNonOtpHints() {
        val hints = arrayOf("username", "password", "email")
        assertFalse(OtpFieldDetector.isOtpHint(hints))
    }

    @Test
    fun testIsOtpHintNullReturnsfalse() {
        assertFalse(OtpFieldDetector.isOtpHint(null))
    }

    @Test
    fun testIsOtpHintEmptyArrayReturnsfalse() {
        val hints = arrayOf<String>()
        assertFalse(OtpFieldDetector.isOtpHint(hints))
    }

    @Test
    fun testIsOtpFieldDetectsOtpByContentDescription() {
        val node = mockk<AccessibilityNodeInfo>()
        every { node.isEnabled } returns true
        every { node.isVisibleToUser } returns true
        every { node.contentDescription } returns "Enter OTP code"
        every { node.viewIdResourceName } returns null
        every { node.text } returns null

        assertTrue(OtpFieldDetector.isOtpField(node))
    }

    @Test
    fun testIsOtpFieldDetectsOtpByViewId() {
        val node = mockk<AccessibilityNodeInfo>()
        every { node.isEnabled } returns true
        every { node.isVisibleToUser } returns true
        every { node.contentDescription } returns null
        every { node.viewIdResourceName } returns "com.example.app:id/otp_input"
        every { node.text } returns null

        assertTrue(OtpFieldDetector.isOtpField(node))
    }

    @Test
    fun testIsOtpFieldDetectsOtpByFieldName() {
        val node = mockk<AccessibilityNodeInfo>()
        every { node.isEnabled } returns true
        every { node.isVisibleToUser } returns true
        every { node.contentDescription } returns null
        every { node.viewIdResourceName } returns "com.example.app:id/totp_field"
        every { node.text } returns null

        assertTrue(OtpFieldDetector.isOtpField(node))
    }

    @Test
    fun testIsOtpFieldDetectsCodeFieldVariation() {
        val node = mockk<AccessibilityNodeInfo>()
        every { node.isEnabled } returns true
        every { node.isVisibleToUser } returns true
        every { node.contentDescription } returns "6-digit code"
        every { node.viewIdResourceName } returns null
        every { node.text } returns null

        assertTrue(OtpFieldDetector.isOtpField(node))
    }

    @Test
    fun testIsOtpFieldDetectsVerificationCode() {
        val node = mockk<AccessibilityNodeInfo>()
        every { node.isEnabled } returns true
        every { node.isVisibleToUser } returns true
        every { node.contentDescription } returns "verification code"
        every { node.viewIdResourceName } returns null
        every { node.text } returns null

        assertTrue(OtpFieldDetector.isOtpField(node))
    }

    @Test
    fun testIsOtpFieldExcludesFalsePositiveCountryCode() {
        val node = mockk<AccessibilityNodeInfo>()
        every { node.isEnabled } returns true
        every { node.isVisibleToUser } returns true
        every { node.contentDescription } returns "country code"
        every { node.viewIdResourceName } returns null
        every { node.text } returns null

        assertFalse(OtpFieldDetector.isOtpField(node))
    }

    @Test
    fun testIsOtpFieldExcludesFalsePositivePhoneCode() {
        val node = mockk<AccessibilityNodeInfo>()
        every { node.isEnabled } returns true
        every { node.isVisibleToUser } returns true
        every { node.contentDescription } returns "phone code"
        every { node.viewIdResourceName } returns null
        every { node.text } returns null

        assertFalse(OtpFieldDetector.isOtpField(node))
    }

    @Test
    fun testIsOtpFieldExcludesFalsePositivePostalCode() {
        val node = mockk<AccessibilityNodeInfo>()
        every { node.isEnabled } returns true
        every { node.isVisibleToUser } returns true
        every { node.contentDescription } returns "postal code"
        every { node.viewIdResourceName } returns null
        every { node.text } returns null

        assertFalse(OtpFieldDetector.isOtpField(node))
    }

    @Test
    fun testIsOtpFieldExcludesFalsePositiveZipCode() {
        val node = mockk<AccessibilityNodeInfo>()
        every { node.isEnabled } returns true
        every { node.isVisibleToUser } returns true
        every { node.contentDescription } returns "zip code"
        every { node.viewIdResourceName } returns null
        every { node.text } returns null

        assertFalse(OtpFieldDetector.isOtpField(node))
    }

    @Test
    fun testIsOtpFieldIgnoresDisabledFields() {
        val node = mockk<AccessibilityNodeInfo>()
        every { node.isEnabled } returns false
        every { node.isVisibleToUser } returns true
        every { node.contentDescription } returns "OTP"
        every { node.viewIdResourceName } returns null
        every { node.text } returns null

        assertFalse(OtpFieldDetector.isOtpField(node))
    }

    @Test
    fun testIsOtpFieldIgnoresInvisibleFields() {
        val node = mockk<AccessibilityNodeInfo>()
        every { node.isEnabled } returns true
        every { node.isVisibleToUser } returns false
        every { node.contentDescription } returns "OTP"
        every { node.viewIdResourceName } returns null
        every { node.text } returns null

        assertFalse(OtpFieldDetector.isOtpField(node))
    }

    @Test
    fun testIsOtpFieldNullReturnsfalse() {
        assertFalse(OtpFieldDetector.isOtpField(null))
    }

    @Test
    fun testGetConfidenceScoreForOtpPattern() {
        val score = OtpFieldDetector.getConfidenceScore("otp_field")
        assertTrue(score > 0.9f)
    }

    @Test
    fun testGetConfidenceScoreForNonOtpPattern() {
        val score = OtpFieldDetector.getConfidenceScore("username")
        assertEquals(0.0f, score)
    }

    @Test
    fun testCanAutoFillOtpDetectsOtpField() {
        val node = mockk<AccessibilityNodeInfo>()
        every { node.isEnabled } returns true
        every { node.isVisibleToUser } returns true
        every { node.contentDescription } returns "OTP"
        every { node.viewIdResourceName } returns null
        every { node.text } returns null
        every { node.isPassword } returns false
        every { node.className } returns "android.widget.EditText"

        assertTrue(OtpFieldDetector.canAutoFillOtp(node))
    }

    @Test
    fun testCanAutoFillOtpNullReturnsfalse() {
        assertFalse(OtpFieldDetector.canAutoFillOtp(null))
    }

    @Test
    fun testCanAutoFillOtpNonOtpFieldReturnsfalse() {
        val node = mockk<AccessibilityNodeInfo>()
        every { node.isEnabled } returns true
        every { node.isVisibleToUser } returns true
        every { node.contentDescription } returns "username"
        every { node.viewIdResourceName } returns null
        every { node.text } returns null

        assertFalse(OtpFieldDetector.canAutoFillOtp(node))
    }

    @Test
    fun testDetectsMultipleOtpFieldIdentifiers() {
        val otpPatterns = listOf(
            "otp_input",
            "code_field",
            "totp_secret",
            "2fa_code",
            "mfa_pin",
            "verification_code",
            "auth_code",
            "confirm_code"
        )

        for (pattern in otpPatterns) {
            val node = mockk<AccessibilityNodeInfo>()
            every { node.isEnabled } returns true
            every { node.isVisibleToUser } returns true
            every { node.contentDescription } returns null
            every { node.viewIdResourceName } returns "com.example:id/$pattern"
            every { node.text } returns null

            assertTrue("Pattern '$pattern' should be detected as OTP", OtpFieldDetector.isOtpField(node))
        }
    }

    @Test
    fun testCaseInsensitiveOtpDetection() {
        val caseVariations = listOf("OTP", "Otp", "oTP", "otP")

        for (variation in caseVariations) {
            val node = mockk<AccessibilityNodeInfo>()
            every { node.isEnabled } returns true
            every { node.isVisibleToUser } returns true
            every { node.contentDescription } returns variation
            every { node.viewIdResourceName } returns null
            every { node.text } returns null

            assertTrue("Case variation '$variation' should be detected", OtpFieldDetector.isOtpField(node))
        }
    }
}
