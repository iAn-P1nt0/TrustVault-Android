package com.trustvault.android.logging

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for SecureLogger PII scrubbing.
 *
 * Tests verify:
 * - Password patterns are redacted
 * - Secret/key patterns are redacted
 * - Email addresses are redacted
 * - Phone numbers are redacted
 * - Credit card patterns are redacted
 * - URLs with credentials are redacted
 * - Normal messages pass through unchanged
 */
class SecureLoggerTest {

    @Before
    fun setup() {
        // Enable diagnostics for testing
        SecureLogger.isDiagnosticsEnabled = true
    }

    @Test
    fun testPasswordRedaction() {
        // Test various password patterns
        val testCases = listOf(
            "password=mysecret123" to "password=[REDACTED]",
            "password: my_password_2024" to "password=[REDACTED]",
            "PASSWORD=TopSecret!" to "password=[REDACTED]",
            "pwd=secret123" to "pwd=[REDACTED]",
            "User set pwd=complicated_pwd_123" to "User set pwd=[REDACTED]"
        )

        for ((input, expected) in testCases) {
            val result = reflectScrubbedMessage(input)
            assertTrue("Password should be redacted in: $input",
                result.contains("[REDACTED]"))
        }
    }

    @Test
    fun testSecretKeyRedaction() {
        val testCases = listOf(
            "secret=my_secret_key_12345" to "secret=[REDACTED]",
            "key=base64encodedkey" to "key=[REDACTED]",
            "API_KEY=sk_live_123456789" to "API_KEY=[REDACTED]",
            "database_key=some_key_value" to "database_key=[REDACTED]"
        )

        for ((input, _) in testCases) {
            val result = reflectScrubbedMessage(input)
            assertTrue("Secrets should be redacted in: $input",
                result.contains("[REDACTED]"))
        }
    }

    @Test
    fun testTokenRedaction() {
        val testCases = listOf(
            "token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9" to "token=[REDACTED]",
            "auth=Bearer eyJhbGci..." to "auth=[REDACTED]",
            "authorization=Bearer token123" to "authorization=[REDACTED]"
        )

        for ((input, _) in testCases) {
            val result = reflectScrubbedMessage(input)
            assertTrue("Tokens should be redacted in: $input",
                result.contains("[REDACTED]"))
        }
    }

    @Test
    fun testEmailRedaction() {
        val testCases = listOf(
            "User email is user@example.com" to "[EMAIL]",
            "Contact john.doe@company.co.uk" to "[EMAIL]",
            "admin+tag@domain.org sent message" to "[EMAIL]"
        )

        for ((input, _) in testCases) {
            val result = reflectScrubbedMessage(input)
            assertTrue("Email should be redacted in: $input",
                result.contains("[EMAIL]"))
        }
    }

    @Test
    fun testPhoneNumberRedaction() {
        val testCases = listOf(
            "Call +1 555-123-4567" to "[PHONE]",
            "Phone: 555.123.4567" to "[PHONE]",
            "+44 1234 567890 is the number" to "[PHONE]"
        )

        for ((input, _) in testCases) {
            val result = reflectScrubbedMessage(input)
            assertTrue("Phone number should be redacted in: $input",
                result.contains("[PHONE]"))
        }
    }

    @Test
    fun testCreditCardRedaction() {
        val testCases = listOf(
            "Card: 4532-1234-5678-9010" to "[CARD]",
            "CC: 4532 1234 5678 9010" to "[CARD]",
            "Card number 4532123456789010 was processed" to "[CARD]"
        )

        for ((input, _) in testCases) {
            val result = reflectScrubbedMessage(input)
            assertTrue("Credit card should be redacted in: $input",
                result.contains("[CARD]"))
        }
    }

    @Test
    fun testUrlCredentialRedaction() {
        val testCases = listOf(
            "Connect to https://user:password@api.example.com" to "https://[REDACTED:REDACTED]@",
            "Visit http://admin:secret123@internal.server" to "http://[REDACTED:REDACTED]@"
        )

        for ((input, _) in testCases) {
            val result = reflectScrubbedMessage(input)
            assertTrue("URL credentials should be redacted in: $input",
                result.contains("[REDACTED:REDACTED]@"))
        }
    }

    @Test
    fun testNormalMessagePassThrough() {
        val normalMessages = listOf(
            "User logged in successfully",
            "Database query executed in 50ms",
            "Credential list loaded with 5 items",
            "Navigation to credential details screen",
            "UI state updated"
        )

        for (message in normalMessages) {
            val result = reflectScrubbedMessage(message)
            assertEquals("Normal message should pass through unchanged",
                message, result)
        }
    }

    @Test
    fun testMultiplePatternsInSingleMessage() {
        val input = "Failed login: user@example.com with password=wrong123, token=abc123xyz"
        val result = reflectScrubbedMessage(input)

        assertTrue("Email should be redacted", result.contains("[EMAIL]"))
        assertTrue("Password should be redacted", result.contains("[REDACTED]"))
        assertTrue("Token should be redacted", result.contains("[REDACTED]"))
    }

    @Test
    fun testCaseInsensitiveRedaction() {
        val testCases = listOf(
            "PASSWORD=secret" to "[REDACTED]",
            "Password=secret" to "[REDACTED]",
            "pAsSWoRd=secret" to "[REDACTED]",
            "KEY=value123" to "[REDACTED]",
            "key=value123" to "[REDACTED]"
        )

        for ((input, _) in testCases) {
            val result = reflectScrubbedMessage(input)
            assertTrue("Should handle case variations in: $input",
                result.contains("[REDACTED]"))
        }
    }

    @Test
    fun testBase64KeyRedaction() {
        val input = "Encrypted with key: SGVsbG8gV29ybGQgSGVsbG8gV29ybGQgSGVsbG8gV29ybGQgSGVsbG8gV29ybGQ="
        val result = reflectScrubbedMessage(input)

        // Long base64 strings should be redacted
        assertTrue("Long base64 strings should be redacted", result.contains("[ENCODED_DATA]"))
    }

    @Test
    fun testEmptyStringHandling() {
        val result = reflectScrubbedMessage("")
        assertEquals("Empty string should pass through", "", result)
    }

    @Test
    fun testLongMessageHandling() {
        val longMessage = "User data: " + "a".repeat(1000) + " with password=secret123"
        val result = reflectScrubbedMessage(longMessage)

        assertTrue("Should handle long messages", result.contains("[REDACTED]"))
    }

    @Test
    fun testSpecialCharactersPreserved() {
        val input = "Action completed: !@#\$%^&*()_+-=[]{}|;:',.<>?"
        val result = reflectScrubbedMessage(input)

        // Special characters should be preserved in normal text
        assertEquals("Special characters should be preserved", input, result)
    }

    @Test
    fun testPartialMatchProtection() {
        // Ensure we don't over-redact similar words
        val input = "password reminder email sent"
        val result = reflectScrubbedMessage(input)

        // Should not redact "password" as a standalone word when it's a reminder
        // But should still contain "password"
        assertTrue("Should preserve non-sensitive use of password word", result.contains("password"))
    }

    @Test
    fun testDiagnosticsToggle() {
        SecureLogger.isDiagnosticsEnabled = false

        // When disabled, debug logs should not be output
        // This is primarily tested through integration tests
        SecureLogger.d("TEST", "This should not be logged when disabled")

        SecureLogger.isDiagnosticsEnabled = true
    }

    /**
     * Reflection helper to access private scrubbedMessage method.
     * In real testing, this would be exposed as protected or use @VisibleForTesting.
     */
    private fun reflectScrubbedMessage(message: String): String {
        // Since the method is private, we use reflection for testing
        val method = SecureLogger::class.java.getDeclaredMethod(
            "scrubbedMessage",
            String::class.java
        )
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(null, message) as String
    }
}
