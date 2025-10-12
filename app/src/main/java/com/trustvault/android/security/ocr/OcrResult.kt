package com.trustvault.android.security.ocr

import java.io.FileOutputStream

/**
 * Secure container for OCR-extracted credential data.
 *
 * Uses CharArray (mutable) instead of String (immutable) to allow secure memory wiping.
 * Implements OWASP MASTG + SEI CERT secure data clearing patterns.
 *
 * SECURITY CRITICAL: This class handles plaintext credentials in memory.
 * - Credentials stored as CharArray (clearable, not String)
 * - Explicit clear() method with secure wiping
 * - toString() override prevents accidental logging
 * - Maximum lifetime: < 1 second (capture → populate → clear)
 *
 * References:
 * - OWASP MASTG-TEST-0011: Testing Memory for Sensitive Data
 * - SEI CERT MSC59-J: Limit the lifetime of sensitive data
 *
 * @property _username Extracted username/email (nullable, clearable)
 * @property _password Extracted password (nullable, clearable)
 * @property _website Extracted website URL (nullable, clearable)
 */
data class OcrResult(
    private var _username: CharArray?,
    private var _password: CharArray?,
    private var _website: CharArray?
) {
    /**
     * Get username as CharArray (nullable).
     * Caller is responsible for clearing after use.
     */
    fun getUsername(): CharArray? = _username

    /**
     * Get password as CharArray (nullable).
     * Caller is responsible for clearing after use.
     */
    fun getPassword(): CharArray? = _password

    /**
     * Get website as CharArray (nullable).
     * Caller is responsible for clearing after use.
     */
    fun getWebsite(): CharArray? = _website

    /**
     * Check if any field was extracted successfully.
     */
    fun hasData(): Boolean = _username != null || _password != null || _website != null

    /**
     * Securely clear all credential data from memory.
     *
     * SECURITY CONTROL: Implements defense-in-depth memory clearing:
     * 1. Overwrite with non-secret data (prevents memory scanning)
     * 2. Fill with zeros
     * 3. Nullify references (allow GC)
     *
     * This method MUST be called after credentials are used.
     * ProGuard is configured to prevent optimization of this method.
     */
    fun clear() {
        _username?.let { secureWipe(it) }
        _password?.let { secureWipe(it) }
        _website?.let { secureWipe(it) }

        _username = null
        _password = null
        _website = null
    }

    /**
     * Secure wipe implementation (OWASP MASTG + SEI CERT pattern).
     *
     * Step 1: Overwrite with non-secret data to prevent memory scanners
     *         from identifying sensitive data patterns.
     * Step 2: Fill with zeros for additional security.
     *
     * Note: Writing to /dev/null (from SEI CERT) is not possible in Android
     * app context, so we rely on overwrite + zero fill + GC.
     *
     * @param data CharArray to securely wipe
     */
    private fun secureWipe(data: CharArray) {
        // Step 1: Overwrite with non-secret data
        val nonSecret = "RuntimeException".toCharArray()
        for (i in data.indices) {
            data[i] = nonSecret[i % nonSecret.size]
        }

        // Step 2: Fill with zeros
        data.fill('\u0000')

        // Note: Cannot write to /dev/null from Android app context
        // JVM GC will eventually reclaim memory
    }

    /**
     * Override toString() to prevent accidental logging of sensitive data.
     *
     * SECURITY CONTROL: Prevents credentials from appearing in logs if
     * OcrResult is accidentally logged.
     *
     * @return Safe string representation without sensitive data
     */
    override fun toString(): String = "OcrResult(" +
            "username=${if (_username != null) "***" else "null"}, " +
            "password=${if (_password != null) "***" else "null"}, " +
            "website=${if (_website != null) "***" else "null"})"

    /**
     * Override equals() to work with CharArray fields.
     * Used for testing only - should not be used in production code.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as OcrResult

        if (_username != null) {
            if (other._username == null) return false
            if (!_username.contentEquals(other._username)) return false
        } else if (other._username != null) return false

        if (_password != null) {
            if (other._password == null) return false
            if (!_password.contentEquals(other._password)) return false
        } else if (other._password != null) return false

        if (_website != null) {
            if (other._website == null) return false
            if (!_website.contentEquals(other._website)) return false
        } else if (other._website != null) return false

        return true
    }

    /**
     * Override hashCode() to work with CharArray fields.
     * Used for testing only - should not be used in production code.
     */
    override fun hashCode(): Int {
        var result = _username?.contentHashCode() ?: 0
        result = 31 * result + (_password?.contentHashCode() ?: 0)
        result = 31 * result + (_website?.contentHashCode() ?: 0)
        return result
    }
}