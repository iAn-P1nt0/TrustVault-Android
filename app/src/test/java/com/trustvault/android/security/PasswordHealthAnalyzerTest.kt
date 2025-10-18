package com.trustvault.android.security

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for PasswordHealthAnalyzer.
 * Tests password strength calculation, entropy, and pattern detection.
 */
class PasswordHealthAnalyzerTest {

    private lateinit var analyzer: PasswordHealthAnalyzer

    @Before
    fun setup() {
        analyzer = PasswordHealthAnalyzer()
    }

    @Test
    fun testEmptyPassword() {
        val result = analyzer.analyzePassword("")
        assertEquals(PasswordHealthAnalyzer.StrengthLevel.VERY_WEAK, result.strengthLevel)
        assertEquals(0, result.score)
        assertTrue(result.warnings.contains("Password must not be empty"))
    }

    @Test
    fun testWeakPassword() {
        val result = analyzer.analyzePassword("password")
        assertEquals(PasswordHealthAnalyzer.StrengthLevel.VERY_WEAK, result.strengthLevel)
        assertTrue(result.score < 30)
        assertTrue(result.warnings.any { it.contains("commonly used") })
    }

    @Test
    fun testStrongPassword() {
        val result = analyzer.analyzePassword("MyStr0ng!P@ssw0rd123")
        assertTrue(result.score >= 50)
        assertTrue(result.strengthLevel in listOf(
            PasswordHealthAnalyzer.StrengthLevel.GOOD,
            PasswordHealthAnalyzer.StrengthLevel.VERY_STRONG
        ))
    }

    @Test
    fun testVeryStrongPassword() {
        val result = analyzer.analyzePassword("x7K#mP2\$qL9@nR5&vT8!wU3%yZ")
        assertTrue(result.score >= 70)
        assertEquals(PasswordHealthAnalyzer.StrengthLevel.VERY_STRONG, result.strengthLevel)
    }

    @Test
    fun testEntropyCalculation() {
        val weak = analyzer.analyzePassword("abc")
        val strong = analyzer.analyzePassword("abc123ABC!@#")

        assertTrue(weak.entropyBits < strong.entropyBits)
    }

    @Test
    fun testSequentialCharacterDetection() {
        val result = analyzer.analyzePassword("MyPass123abc456")
        assertTrue(result.warnings.any { it.contains("sequential") })
    }

    @Test
    fun testRepeatedCharacterDetection() {
        val result = analyzer.analyzePassword("Mypassaaa123")
        assertTrue(result.warnings.any { it.contains("repeated") })
    }

    @Test
    fun testKeyboardPatternDetection() {
        val result = analyzer.analyzePassword("Myqwerty123")
        assertTrue(result.warnings.any { it.contains("keyboard pattern") })
    }

    @Test
    fun testCharacterTypeRequirement() {
        val lowercase = analyzer.analyzePassword("abcdefgh")
        assertTrue(lowercase.warnings.any { it.contains("uppercase") })

        val mixed = analyzer.analyzePassword("AbCdEfGh")
        // Should have fewer warnings
        assertTrue(mixed.warnings.size < lowercase.warnings.size)
    }

    @Test
    fun testUsernameInclusion() {
        val result = analyzer.analyzePassword("MyPassJohn123", username = "john")
        assertTrue(result.warnings.any { it.contains("username") })
    }

    @Test
    fun testEmailInclusion() {
        val result = analyzer.analyzePassword("Mypassjohn123", email = "john@example.com")
        assertTrue(result.warnings.any { it.contains("email") })
    }

    @Test
    fun testLengthRecommendation() {
        val short = analyzer.analyzePassword("MyPass1!")
        assertTrue(short.feedback.any { it.contains("12 characters") })

        val long = analyzer.analyzePassword("MyStr0ng!P@ssw0rd123ABC")
        assertTrue(long.feedback.isEmpty() || !long.feedback.any { it.contains("12 characters") })
    }

    @Test
    fun testCrackTime() {
        val weak = analyzer.analyzePassword("weak")
        val strong = analyzer.analyzePassword("x7K#mP2\$qL9@nR5&vT8!wU3%yZ")

        assertTrue(weak.crackTimeYears < strong.crackTimeYears)
    }

    @Test
    fun testMinimumLengthScore() {
        val result = analyzer.analyzePassword("x7K#mP2$")
        assertTrue(result.score < 80) // Should be lower due to length
    }

    @Test
    fun testAllCharacterTypes() {
        val result = analyzer.analyzePassword("MyPass123!@#")
        assertTrue(result.feedback.any { it.contains("Length") })
        // Should have higher score with all character types
        assertTrue(result.score > 40)
    }

    @Test
    fun testScoreBounds() {
        val veryWeak = analyzer.analyzePassword("")
        val veryStrong = analyzer.analyzePassword("x7K#mP2\$qL9@nR5&vT8!wU3%yZaB4cD5")

        assertTrue(veryWeak.score >= 0)
        assertTrue(veryWeak.score <= 100)
        assertTrue(veryStrong.score >= 0)
        assertTrue(veryStrong.score <= 100)
    }

    @Test
    fun testCommonPasswords() {
        val commonPasswords = listOf(
            "password", "123456", "password123", "admin", "letmein"
        )

        commonPasswords.forEach { password ->
            val result = analyzer.analyzePassword(password)
            assertTrue(
                "Password '$password' should be detected as common",
                result.warnings.any { it.contains("commonly") }
            )
        }
    }

    @Test
    fun testMixedCaseImportance() {
        val lowercase = analyzer.analyzePassword("mypassword123!@#")
        val mixedcase = analyzer.analyzePassword("MyPassword123!@#")

        // Mixed case should have better score
        assertTrue(mixedcase.score > lowercase.score)
    }

    @Test
    fun testSpecialCharacterImportance() {
        val noSpecial = analyzer.analyzePassword("MyPassword123")
        val withSpecial = analyzer.analyzePassword("MyPassword123!@#")

        // With special chars should have better score
        assertTrue(withSpecial.score > noSpecial.score)
    }
}
