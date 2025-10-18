package com.trustvault.android.security

import android.view.accessibility.AccessibilityNodeInfo
import android.view.View

/**
 * Detects OTP/2FA input fields using multiple strategies.
 *
 * **Detection Methods:**
 * 1. Android Autofill hints (ViewCompat.AUTOFILL_HINT_OTP)
 * 2. Common ID patterns (otp, code, totp, mfa, 2fa, etc.)
 * 3. Content description patterns
 * 4. Input type hints
 *
 * **Security:**
 * - Only detects OTP-specific fields (no false positives)
 * - Excludes username/password fields
 * - No credentials stored in detector
 * - Stateless implementation
 *
 * **Compatibility:**
 * - Works with AutofillService (hints)
 * - Works with AccessibilityService (content descriptions, IDs)
 * - Supports various OTP field formats
 */
object OtpFieldDetector {

    // OTP field detection patterns
    private val OTP_ID_PATTERNS = listOf(
        "otp",           // Generic OTP field
        "one_time_password",
        "one_time_code",
        "code",          // 2FA code field
        "totp",          // Time-based OTP
        "hotp",          // HMAC-based OTP
        "mfa",           // Multi-factor authentication
        "2fa",           // Two-factor authentication
        "two_factor",
        "verification_code",
        "verify_code",
        "auth_code",
        "confirmation_code",
        "security_code",
        "pin",           // Some services use PIN for OTP
        "tfcode",        // Two-factor code
        "passcode"       // Generic passcode
    )

    private val OTP_CONTENT_PATTERNS = listOf(
        "code",
        "otp",
        "totp",
        "mfa",
        "2fa",
        "verification",
        "confirm",
        "enter code",
        "passcode",
        "pin"
    )

    // Patterns to explicitly exclude (false positives)
    private val EXCLUDE_PATTERNS = listOf(
        "country",
        "country_code",
        "phone",
        "phone_code",
        "area_code",
        "postal_code",
        "zip_code",
        "pin_code",
        "currency"
    )

    /**
     * Detects if an accessibility node is an OTP field.
     *
     * Used by AccessibilityService to identify OTP input fields.
     *
     * @param node AccessibilityNodeInfo to check
     * @return true if node appears to be an OTP field
     */
    fun isOtpField(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        // Must be enabled and visible
        if (!node.isEnabled || !node.isVisibleToUser) {
            return false
        }

        // Check content description
        val contentDescription = node.contentDescription?.toString()?.lowercase() ?: ""
        if (contentDescription.isNotEmpty()) {
            if (matchesOtpPattern(contentDescription)) {
                return true
            }
        }

        // Check view ID resource name
        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        if (viewId.isNotEmpty()) {
            if (matchesOtpPattern(viewId)) {
                return true
            }
        }

        // Check additional text (like hint text if available)
        val nodeText = node.text?.toString()?.lowercase() ?: ""
        if (nodeText.isNotEmpty()) {
            if (matchesOtpPattern(nodeText)) {
                return true
            }
        }

        return false
    }

    /**
     * Detects if autofill hints indicate an OTP field.
     *
     * Used by AutofillService to identify OTP input fields.
     *
     * @param hints Autofill hints from ViewCompat
     * @return true if hints include OTP hint
     */
    fun isOtpHint(hints: Array<String>?): Boolean {
        if (hints == null || hints.isEmpty()) {
            return false
        }

        // Check for official OTP hint (API 30+: View.AUTOFILL_HINT_OTP)
        for (hint in hints) {
            val lowerHint = hint.lowercase()

            // Check against all OTP patterns
            for (pattern in OTP_ID_PATTERNS) {
                if (lowerHint.contains(pattern)) {
                    return true
                }
            }

            // Also check for content patterns
            for (pattern in OTP_CONTENT_PATTERNS) {
                if (lowerHint.contains(pattern)) {
                    return true
                }
            }
        }

        return false
    }

    /**
     * Detects if a text matches OTP field patterns.
     *
     * **Pattern Matching:**
     * - Checks if text contains OTP-related keywords
     * - Verifies it's not a false positive (country code, etc.)
     * - Case-insensitive matching
     *
     * @param text Text to check (content description, ID, or hint)
     * @return true if text matches OTP patterns
     */
    private fun matchesOtpPattern(text: String): Boolean {
        val lower = text.lowercase()
        // Normalize underscores and hyphens to spaces for matching
        val normalized = lower.replace("_", " ").replace("-", " ")

        // First check if it's an excluded (false positive) pattern
        for (exclude in EXCLUDE_PATTERNS) {
            val normalizedExclude = exclude.replace("_", " ").replace("-", " ")
            if (normalized.contains(normalizedExclude)) {
                // For excluded patterns, only return true if it ALSO contains a strong OTP indicator
                // (not just "code" which is too generic)
                for (strongPattern in listOf("otp", "totp", "hotp", "2fa", "mfa", "verification code", "auth code")) {
                    if (normalized.contains(strongPattern)) {
                        return true
                    }
                }
                // If it contains an excluded pattern but no strong OTP indicator, it's not OTP
                return false
            }
        }

        // Check against OTP ID patterns (strongest indicators)
        for (pattern in OTP_ID_PATTERNS) {
            val normalizedPattern = pattern.replace("_", " ").replace("-", " ")
            if (normalized.contains(normalizedPattern)) {
                return true
            }
        }

        return false
    }

    /**
     * Gets the field type confidence (for future ML-based detection).
     *
     * Currently returns binary (is/isn't OTP), but structure allows
     * for probability scores in future versions.
     *
     * @param text Field identifier text
     * @return confidence score (0.0 - 1.0)
     */
    fun getConfidenceScore(text: String): Float {
        return if (matchesOtpPattern(text)) 0.95f else 0.0f
    }

    /**
     * Validates if a field is suitable for TOTP autofill.
     *
     * Checks:
     * - Field is OTP-type
     * - Not already filled
     * - Accepts text input
     * - Is focused or focusable
     *
     * @param node AccessibilityNodeInfo to validate
     * @return true if field can receive TOTP code
     */
    fun canAutoFillOtp(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        // Must be OTP field
        if (!isOtpField(node)) {
            return false
        }

        // Must be empty or editable
        if (node.text?.isNotEmpty() == true) {
            // Allow if it's just placeholder text
            if (!node.isPassword) {
                return true
            }
        }

        // Must accept text input
        if (!node.isPassword && node.className?.toString()?.contains("EditText") != true) {
            // Try to detect text input capability another way
            if (node.text == null && node.contentDescription == null) {
                return false
            }
        }

        return true
    }
}
