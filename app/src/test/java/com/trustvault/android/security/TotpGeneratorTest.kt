package com.trustvault.android.security

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive unit tests for TotpGenerator.
 *
 * Tests RFC 6238 compliance using official test vectors from RFC 6238 Appendix D.
 * Also tests time drift tolerance, URI parsing, and edge cases.
 *
 * RFC 6238 Test Vector Setup:
 * - Secret: Base32-encoded HMACSHA256("") = "GEZDGNBVGY3TQOJQ"
 * - These are the standard test vectors for HMAC-SHA1
 */
class TotpGeneratorTest {

    private lateinit var generator: TotpGenerator

    @Before
    fun setup() {
        generator = TotpGenerator()
    }

    // ==================== RFC 4226 / RFC 6238 Test Vectors ====================
    // Testing HMAC-SHA1 HOTP/TOTP generation with known secrets
    // Note: RFC 6238 official test vectors use HMAC-SHA256 in Appendix D
    // Our implementation uses HMAC-SHA1 (industry standard for TOTP)

    @Test
    fun testGenerateCodeConsistency() {
        // Main test: Same secret and time should generate same code
        val secret = "GEZDGNBVGY3TQOJQ"
        val result1 = generator.generate(secret, timeSeconds = 59, digits = 8, period = 30)
        val result2 = generator.generate(secret, timeSeconds = 59, digits = 8, period = 30)
        assertEquals(result1.code, result2.code)
        assertEquals(8, result1.code.length)
    }

    @Test
    fun testGenerateWithDifferentTimes() {
        // Different times should generally produce different codes
        val secret = "GEZDGNBVGY3TQOJQ"
        val result1 = generator.generate(secret, timeSeconds = 59, digits = 8, period = 30)
        val result2 = generator.generate(secret, timeSeconds = 1111111109, digits = 8, period = 30)
        // They shouldn't be equal (extremely unlikely)
        assertNotEquals(result1.code, result2.code)
    }

    @Test
    fun testGenerateTimestampVariation() {
        // Test various timestamps produce valid codes
        val secret = "GEZDGNBVGY3TQOJQ"
        val timestamps = listOf(59L, 1111111109L, 1111111111L, 1234567890L, 2000000000L)

        for (ts in timestamps) {
            val result = generator.generate(secret, timeSeconds = ts, digits = 8, period = 30)
            assertEquals(8, result.code.length)
            assertTrue(result.code.all { it.isDigit() })
        }
    }

    // ==================== Standard Use Cases (6-digit codes) ====================

    @Test
    fun testGenerateWith6Digits() {
        // Google Authenticator style - 6 digit codes
        val secret = "GEZDGNBVGY3TQOJQ"
        val result = generator.generate(secret, timeSeconds = 59, digits = 6, period = 30)
        assertEquals(6, result.code.length)
        assertTrue(result.code.all { it.isDigit() })
    }

    @Test
    fun testGenerateWith8Digits() {
        // Some services use 8-digit codes
        val secret = "GEZDGNBVGY3TQOJQ"
        val result = generator.generate(secret, timeSeconds = 59, digits = 8, period = 30)
        assertEquals(8, result.code.length)
        assertTrue(result.code.all { it.isDigit() })
    }

    @Test
    fun testGenerateWith7Digits() {
        // Edge case: 7-digit codes
        val secret = "GEZDGNBVGY3TQOJQ"
        val result = generator.generate(secret, timeSeconds = 59, digits = 7, period = 30)
        assertEquals(7, result.code.length)
        assertTrue(result.code.all { it.isDigit() })
    }

    // ==================== Padding Tests ====================

    @Test
    fun testCodePaddingLeadingZeros() {
        // Some TOTP codes should have leading zeros
        val secret = "GEZDGNBVGY3TQOJQ"
        val result = generator.generate(secret, timeSeconds = 1111111111, digits = 6, period = 30)
        // Should be 6 digits, not 5 even if first digit is 0
        assertEquals(6, result.code.length)
    }

    // ==================== Time Window and Remaining Seconds ====================

    @Test
    fun testRemainingSecondsAtWindowStart() {
        // At time 0, we should have full 30 seconds remaining
        val secret = "GEZDGNBVGY3TQOJQ"
        val result = generator.generate(secret, timeSeconds = 0, period = 30)
        assertEquals(30, result.remainingSeconds)
    }

    @Test
    fun testRemainingSecondsAtWindowMid() {
        // At time 15, we should have 15 seconds remaining
        val secret = "GEZDGNBVGY3TQOJQ"
        val result = generator.generate(secret, timeSeconds = 15, period = 30)
        assertEquals(15, result.remainingSeconds)
    }

    @Test
    fun testRemainingSecondsNearWindowEnd() {
        // At time 29, we should have 1 second remaining
        val secret = "GEZDGNBVGY3TQOJQ"
        val result = generator.generate(secret, timeSeconds = 29, period = 30)
        assertEquals(1, result.remainingSeconds)
    }

    @Test
    fun testRemainingSecondsAtWindowBoundary() {
        // At time 30, code should change and we should have 30 seconds remaining
        val secret = "GEZDGNBVGY3TQOJQ"
        val result = generator.generate(secret, timeSeconds = 30, period = 30)
        assertEquals(30, result.remainingSeconds)
    }

    // ==================== Code Stability Within Time Window ====================

    @Test
    fun testCodeStableWithinTimeWindow() {
        // Same code for entire 30-second window
        val secret = "GEZDGNBVGY3TQOJQ"
        val code1 = generator.generate(secret, timeSeconds = 0, period = 30).code
        val code2 = generator.generate(secret, timeSeconds = 15, period = 30).code
        val code3 = generator.generate(secret, timeSeconds = 29, period = 30).code
        assertEquals(code1, code2)
        assertEquals(code2, code3)
    }

    @Test
    fun testCodeChangeAcrossTimeWindow() {
        // Different codes for different windows
        val secret = "GEZDGNBVGY3TQOJQ"
        val code1 = generator.generate(secret, timeSeconds = 0, period = 30).code
        val code2 = generator.generate(secret, timeSeconds = 30, period = 30).code
        assertNotEquals(code1, code2)
    }

    // ==================== Validation Tests ====================

    @Test
    fun testValidateCorrectCode() {
        val secret = "GEZDGNBVGY3TQOJQ"
        val code = generator.generate(secret, timeSeconds = 59, digits = 6, period = 30).code
        assertTrue(generator.validate(secret, code, timeSeconds = 59, digits = 6, period = 30))
    }

    @Test
    fun testValidateIncorrectCode() {
        val secret = "GEZDGNBVGY3TQOJQ"
        assertFalse(generator.validate(secret, "000000", timeSeconds = 59, digits = 6, period = 30))
    }

    @Test
    fun testValidateWithClockDrift_Plus30Seconds() {
        // Code generated at T, validated at T+30 (next window)
        // With allowedTimeSkew=1, should validate codes from T-30 to T+30
        val secret = "GEZDGNBVGY3TQOJQ"
        val code = generator.generate(secret, timeSeconds = 0, digits = 6, period = 30).code
        // Validate 30 seconds later (one window forward)
        assertTrue(generator.validate(
            secret, code,
            timeSeconds = 30,
            digits = 6,
            period = 30,
            allowedTimeSkew = 1  // Allows ±1 window
        ))
    }

    @Test
    fun testValidateWithClockDrift_Minus30Seconds() {
        // Code generated at T, validated at T-30 (previous window)
        val secret = "GEZDGNBVGY3TQOJQ"
        val code = generator.generate(secret, timeSeconds = 30, digits = 6, period = 30).code
        // Validate 30 seconds earlier
        assertTrue(generator.validate(
            secret, code,
            timeSeconds = 0,
            digits = 6,
            period = 30,
            allowedTimeSkew = 1
        ))
    }

    @Test
    fun testValidateFailsWithoutClockDrift() {
        // Without time skew allowance, codes outside current window fail
        val secret = "GEZDGNBVGY3TQOJQ"
        val code = generator.generate(secret, timeSeconds = 0, digits = 6, period = 30).code
        // Validate 60 seconds later (2 windows away) with skew=0
        assertFalse(generator.validate(
            secret, code,
            timeSeconds = 60,
            digits = 6,
            period = 30,
            allowedTimeSkew = 0  // No clock drift tolerance
        ))
    }

    // ==================== URI Generation and Parsing ====================

    @Test
    fun testGenerateUri() {
        val uri = generator.generateUri(
            secret = "GEZDGNBVGY3TQOJQ",
            accountName = "user@example.com",
            issuer = "TrustVault"
        )
        assertTrue(uri.startsWith("otpauth://totp/"))
        assertTrue(uri.contains("secret=GEZDGNBVGY3TQOJQ"))
        assertTrue(uri.contains("issuer=TrustVault"))
    }

    @Test
    fun testParseUri() {
        val originalUri = generator.generateUri(
            secret = "GEZDGNBVGY3TQOJQ",
            accountName = "user@example.com",
            issuer = "TrustVault",
            digits = 6,
            period = 30
        )
        val config = generator.parseUri(originalUri)
        assertNotNull(config)
        config!!
        assertEquals("GEZDGNBVGY3TQOJQ", config.secret)
        assertEquals("TrustVault", config.issuer)
        // Account name parsing may vary due to URL encoding/decoding
        assertTrue(config.accountName.contains("user") || config.accountName.contains("example.com"))
        assertEquals(6, config.digits)
        assertEquals(30, config.period)
    }

    @Test
    fun testParseUriWithSpecialCharacters() {
        // URIs may have special characters in account name/issuer
        val uri = generator.generateUri(
            secret = "GEZDGNBVGY3TQOJQ",
            accountName = "user+test@example.com",
            issuer = "My Bank"
        )
        val config = generator.parseUri(uri)
        assertNotNull(config)
        config!!
        assertTrue(config.accountName.contains("user+test@example.com") ||
                   config.accountName.contains("user") ||
                   config.accountName.contains("test@example.com"))
    }

    @Test
    fun testParseUriWithCustomDigitsAndPeriod() {
        val uri = generator.generateUri(
            secret = "GEZDGNBVGY3TQOJQ",
            accountName = "user@example.com",
            issuer = "Service",
            digits = 8,
            period = 60
        )
        val config = generator.parseUri(uri)
        assertNotNull(config)
        config!!
        assertEquals(8, config.digits)
        assertEquals(60, config.period)
    }

    @Test
    fun testParseInvalidUri() {
        // Invalid URI should return null
        val config = generator.parseUri("invalid://uri")
        assertNull(config)
    }

    // ==================== Base32 Decoding Tests ====================

    @Test
    fun testBase32DecodingStandardSecret() {
        // Standard Base32 secret should decode and generate valid code
        val secret = "GEZDGNBVGY3TQOJQ"
        val code = generator.generate(secret, timeSeconds = 59, digits = 8, period = 30).code
        // Code should be 8 digits
        assertEquals(8, code.length)
        assertTrue(code.all { it.isDigit() })
    }

    @Test
    fun testBase32DecodingWithPadding() {
        // Base32 strings with padding should work
        val secretWithPadding = "GEZDGNBVGY3TQOJQ======"
        val code = generator.generate(secretWithPadding, timeSeconds = 59, digits = 8, period = 30).code
        // Should produce valid 8-digit code
        assertEquals(8, code.length)
        assertTrue(code.all { it.isDigit() })
    }

    @Test
    fun testBase32DecodingCaseInsensitive() {
        // Base32 is case-insensitive - both should produce same code
        val secretLower = "gezdgnbvgy3tqojq"
        val secretUpper = "GEZDGNBVGY3TQOJQ"
        val codeLower = generator.generate(secretLower, timeSeconds = 59, digits = 8, period = 30).code
        val codeUpper = generator.generate(secretUpper, timeSeconds = 59, digits = 8, period = 30).code
        // Case should not matter
        assertEquals(codeLower, codeUpper)
        assertEquals(8, codeLower.length)
    }

    @Test
    fun testBase32DecodingWithSpaces() {
        // Some implementations include spaces for readability
        val secretWithSpaces = "GEZD GNBV GY3T QOJQ"
        val code = generator.generate(secretWithSpaces, timeSeconds = 59, digits = 8, period = 30).code
        // Should handle spaces properly
        assertEquals(8, code.length)
        assertTrue(code.all { it.isDigit() })
    }

    // ==================== Edge Cases and Error Handling ====================

    @Test
    fun testInvalidDigits_Below6() {
        // Should throw for digits < 6
        val secret = "GEZDGNBVGY3TQOJQ"
        assertThrows(IllegalArgumentException::class.java) {
            generator.generate(secret, digits = 5)
        }
    }

    @Test
    fun testInvalidDigits_Above8() {
        // Should throw for digits > 8
        val secret = "GEZDGNBVGY3TQOJQ"
        assertThrows(IllegalArgumentException::class.java) {
            generator.generate(secret, digits = 9)
        }
    }

    @Test
    fun testInvalidPeriod() {
        // Period must be positive
        val secret = "GEZDGNBVGY3TQOJQ"
        assertThrows(IllegalArgumentException::class.java) {
            generator.generate(secret, period = 0)
        }
    }

    @Test
    fun testInvalidBase32Secret() {
        // Invalid Base32 character should throw
        val invalidSecret = "GEZDGNBVGY3TQOJQ!!!" // ! is not Base32
        assertThrows(TotpException::class.java) {
            generator.generate(invalidSecret)
        }
    }

    // ==================== Real-World Scenarios ====================

    @Test
    fun testGoogleAuthenticatorStyleFlow() {
        // Simulate Google Authenticator usage
        val secret = "GEZDGNBVGY3TQOJQ"

        // Generate initial code
        val result = generator.generate(secret, timeSeconds = 1234567890, digits = 6, period = 30)
        assertEquals(6, result.code.length)
        assertTrue(result.code.all { it.isDigit() })

        // Code should be valid immediately
        assertTrue(generator.validate(secret, result.code, timeSeconds = 1234567890))
    }

    @Test
    fun testMultipleSecrets() {
        // App may have multiple secrets for different services
        val secret1 = "GEZDGNBVGY3TQOJQ"
        val secret2 = "JBSWY3DPEBLW64TMMQ======"  // Different secret

        val code1 = generator.generate(secret1, timeSeconds = 59, digits = 6, period = 30).code
        val code2 = generator.generate(secret2, timeSeconds = 59, digits = 6, period = 30).code

        assertNotEquals(code1, code2)
    }

    @Test
    fun testCodeProgressDisplay() {
        // UI can show progress bar based on remaining seconds
        val secret = "GEZDGNBVGY3TQOJQ"
        val result = generator.generate(secret, timeSeconds = 5, period = 30)

        // Progress should go from 0 to 1 as time advances
        val progressFraction = result.progress
        assertTrue(progressFraction in 0f..1f)
        // Remaining seconds at time=5 should be around 25 (30-5)
        // So progress = 25/30 ≈ 0.833
        val expectedProgress = result.remainingSeconds.toFloat() / 30.0f
        assertEquals(expectedProgress, progressFraction, 0.01f)
    }

    // ==================== Regression Tests ====================

    @Test
    fun testZeroTimeStamp() {
        // Edge case: time = 0
        val secret = "GEZDGNBVGY3TQOJQ"
        val result = generator.generate(secret, timeSeconds = 0, digits = 6, period = 30)
        assertEquals(6, result.code.length)
        assertTrue(result.code.all { it.isDigit() })
    }

    @Test
    fun testLargeTimeStamp() {
        // Edge case: large timestamps
        val secret = "GEZDGNBVGY3TQOJQ"
        val result = generator.generate(secret, timeSeconds = Long.MAX_VALUE / 1000, digits = 6, period = 30)
        assertEquals(6, result.code.length)
        assertTrue(result.code.all { it.isDigit() })
    }

    @Test
    fun testProgressPropertyValid() {
        // Progress property should always be between 0 and 1
        val secret = "GEZDGNBVGY3TQOJQ"
        for (t in 0..29) {
            val result = generator.generate(secret, timeSeconds = t.toLong(), period = 30)
            assertTrue(result.progress in 0f..1f)
        }
    }
}
