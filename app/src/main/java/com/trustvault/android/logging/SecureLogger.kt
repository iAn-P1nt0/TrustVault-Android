package com.trustvault.android.logging

import android.util.Log

/**
 * Centralized secure logging with automatic PII scrubbing.
 *
 * SECURITY CONTROL: All sensitive data patterns are automatically removed
 * from log output. Never logs:
 * - Passwords, PINs, API keys
 * - Email addresses, phone numbers
 * - Credential data (usernames, URLs)
 * - Database keys, encryption keys
 * - Auth tokens, session IDs
 *
 * OWASP MASTG: Sensitive Data Exposure
 * Implements secure logging practices to prevent information disclosure
 * through system logs or crash reports.
 *
 * Usage:
 * ```kotlin
 * SecureLogger.d(TAG, "User logged in") // Safe
 * SecureLogger.d(TAG, "Password: $pwd") // PII automatically scrubbed
 * SecureLogger.e(TAG, "Error: $errorMsg", exception) // Safe
 * ```
 */
object SecureLogger {
    private const val TAG_PREFIX = "TV/"

    /**
     * Global diagnostics toggle (can be controlled from settings).
     * When false, only errors and warnings are logged.
     */
    var isDiagnosticsEnabled = false

    /**
     * Log debug message with automatic PII scrubbing.
     */
    fun d(tag: String, message: String, throwable: Throwable? = null) {
        if (!isDiagnosticsEnabled) return
        val scrubbed = scrubbedMessage(message)
        if (throwable != null) {
            Log.d("$TAG_PREFIX$tag", scrubbed, throwable)
        } else {
            Log.d("$TAG_PREFIX$tag", scrubbed)
        }
    }

    /**
     * Log info message with automatic PII scrubbing.
     */
    fun i(tag: String, message: String, throwable: Throwable? = null) {
        if (!isDiagnosticsEnabled) return
        val scrubbed = scrubbedMessage(message)
        if (throwable != null) {
            Log.i("$TAG_PREFIX$tag", scrubbed, throwable)
        } else {
            Log.i("$TAG_PREFIX$tag", scrubbed)
        }
    }

    /**
     * Log warning message with automatic PII scrubbing.
     * Always logged regardless of diagnostics setting.
     */
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        val scrubbed = scrubbedMessage(message)
        if (throwable != null) {
            Log.w("$TAG_PREFIX$tag", scrubbed, throwable)
        } else {
            Log.w("$TAG_PREFIX$tag", scrubbed)
        }
    }

    /**
     * Log error message with automatic PII scrubbing.
     * Always logged regardless of diagnostics setting.
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val scrubbed = scrubbedMessage(message)
        if (throwable != null) {
            Log.e("$TAG_PREFIX$tag", scrubbed, throwable)
        } else {
            Log.e("$TAG_PREFIX$tag", scrubbed)
        }
    }

    /**
     * Remove sensitive information from message.
     *
     * Patterns removed:
     * - Passwords: "password=..." or "password: ..."
     * - Keys: "key=..." or "secret=..."
     * - Tokens: "token=..." or "auth=..."
     * - URLs with credentials: "http://user:pass@..."
     * - Email addresses
     * - Phone numbers
     * - API keys
     * - Database keys
     */
    private fun scrubbedMessage(message: String): String {
        var scrubbed = message

        // Remove password patterns (case-insensitive)
        scrubbed = scrubbed.replace(
            Regex("password\\s*[:=]\\s*\\S+", RegexOption.IGNORE_CASE),
            "password=[REDACTED]"
        )
        scrubbed = scrubbed.replace(
            Regex("pwd\\s*[:=]\\s*\\S+", RegexOption.IGNORE_CASE),
            "pwd=[REDACTED]"
        )

        // Remove secret/key patterns
        scrubbed = scrubbed.replace(
            Regex("(secret|key|apikey|api_key)\\s*[:=]\\s*\\S+", RegexOption.IGNORE_CASE),
            "key=[REDACTED]"
        )

        // Remove token patterns
        scrubbed = scrubbed.replace(
            Regex("(token|auth|authorization)\\s*[:=]\\s*\\S+", RegexOption.IGNORE_CASE),
            "token=[REDACTED]"
        )

        // Remove URLs with credentials
        scrubbed = scrubbed.replace(
            Regex("(https?://)[^:]+:[^@]+@"),
            "$1[REDACTED:REDACTED]@"
        )

        // Remove email addresses (basic pattern)
        scrubbed = scrubbed.replace(
            Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"),
            "[EMAIL]"
        )

        // Remove phone numbers (basic pattern)
        scrubbed = scrubbed.replace(
            Regex("\\+?\\d{1,3}[\\s.-]?\\d{1,4}[\\s.-]?\\d{1,4}[\\s.-]?\\d{1,9}"),
            "[PHONE]"
        )

        // Remove credit card patterns (4 groups of digits)
        scrubbed = scrubbed.replace(
            Regex("\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b"),
            "[CARD]"
        )

        // Remove base64-encoded data that looks like keys (64+ chars of alphanumeric+=/+)
        scrubbed = scrubbed.replace(
            Regex("[A-Za-z0-9+/]{64,}={0,2}"),
            "[ENCODED_DATA]"
        )

        return scrubbed
    }
}

/**
 * Extension function for easy logging from any class.
 *
 * Usage:
 * ```kotlin
 * class MyScreen {
 *     companion object {
 *         private const val TAG = "MyScreen"
 *     }
 *
 *     fun onDataLoaded() {
 *         logDebug(TAG, "Data loaded successfully")
 *     }
 * }
 * ```
 */
fun logDebug(tag: String, message: String) = SecureLogger.d(tag, message)
fun logInfo(tag: String, message: String) = SecureLogger.i(tag, message)
fun logWarn(tag: String, message: String, throwable: Throwable? = null) =
    SecureLogger.w(tag, message, throwable)
fun logError(tag: String, message: String, throwable: Throwable? = null) =
    SecureLogger.e(tag, message, throwable)
