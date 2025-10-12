package com.trustvault.android.security

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.log2
import kotlin.math.pow

/**
 * Analyzes password strength using entropy calculation and common patterns.
 * Inspired by zxcvbn algorithm used in industry-leading password managers.
 *
 * Provides scores from 0 (very weak) to 4 (very strong) based on:
 * - Entropy (bits of randomness)
 * - Length
 * - Character diversity
 * - Common patterns and sequences
 */
@Singleton
class PasswordStrengthAnalyzer @Inject constructor() {

    /**
     * Password strength levels.
     */
    enum class StrengthLevel(val score: Int, val label: String, val color: String) {
        VERY_WEAK(0, "Very Weak", "#D32F2F"),      // Red
        WEAK(1, "Weak", "#F57C00"),                // Orange
        FAIR(2, "Fair", "#FBC02D"),                // Yellow
        STRONG(3, "Strong", "#689F38"),            // Light Green
        VERY_STRONG(4, "Very Strong", "#388E3C")   // Green
    }

    /**
     * Detailed password strength analysis result.
     */
    data class StrengthResult(
        val strength: StrengthLevel,
        val entropy: Double,
        val crackTimeSeconds: Long,
        val suggestions: List<String>,
        val warnings: List<String>
    )

    /**
     * Analyzes password and returns detailed strength information.
     */
    fun analyze(password: String): StrengthResult {
        val entropy = calculateEntropy(password)
        val crackTime = estimateCrackTime(entropy)
        val patterns = detectPatterns(password)
        val suggestions = generateSuggestions(password, patterns)
        val warnings = generateWarnings(patterns)
        val strength = determineStrength(entropy, password.length, patterns)

        return StrengthResult(
            strength = strength,
            entropy = entropy,
            crackTimeSeconds = crackTime,
            suggestions = suggestions,
            warnings = warnings
        )
    }

    /**
     * Calculates password entropy in bits.
     * Entropy = log2(charset_size^password_length)
     */
    private fun calculateEntropy(password: String): Double {
        if (password.isEmpty()) return 0.0

        val charsetSize = determineCharsetSize(password)
        return password.length * log2(charsetSize.toDouble())
    }

    /**
     * Determines the character set size based on character types used.
     */
    private fun determineCharsetSize(password: String): Int {
        var size = 0

        if (password.any { it.isLowerCase() }) size += 26  // a-z
        if (password.any { it.isUpperCase() }) size += 26  // A-Z
        if (password.any { it.isDigit() }) size += 10      // 0-9
        if (password.any { !it.isLetterOrDigit() }) size += 33  // Special characters

        return size.coerceAtLeast(1)
    }

    /**
     * Estimates time to crack password in seconds.
     * Assumes 10 billion guesses per second (modern GPU cracking speed).
     */
    private fun estimateCrackTime(entropy: Double): Long {
        val guessesPerSecond = 10_000_000_000.0 // 10 billion
        val totalCombinations = 2.0.pow(entropy)
        val seconds = (totalCombinations / 2.0) / guessesPerSecond // Average case

        return seconds.toLong().coerceAtLeast(0)
    }

    /**
     * Detects common weak patterns in password.
     */
    private fun detectPatterns(password: String): Set<Pattern> {
        val patterns = mutableSetOf<Pattern>()

        // Check length
        if (password.length < 8) patterns.add(Pattern.TOO_SHORT)
        if (password.length < 12) patterns.add(Pattern.SHORT)

        // Check character diversity
        if (!password.any { it.isLowerCase() }) patterns.add(Pattern.NO_LOWERCASE)
        if (!password.any { it.isUpperCase() }) patterns.add(Pattern.NO_UPPERCASE)
        if (!password.any { it.isDigit() }) patterns.add(Pattern.NO_NUMBERS)
        if (!password.any { !it.isLetterOrDigit() }) patterns.add(Pattern.NO_SYMBOLS)

        // Check for common patterns
        if (password.lowercase() in COMMON_PASSWORDS) patterns.add(Pattern.COMMON_PASSWORD)
        if (hasSequentialChars(password)) patterns.add(Pattern.SEQUENTIAL)
        if (hasRepeatedChars(password)) patterns.add(Pattern.REPEATED)
        if (hasKeyboardPattern(password)) patterns.add(Pattern.KEYBOARD_PATTERN)

        return patterns
    }

    /**
     * Checks for sequential characters (abc, 123, xyz, etc.)
     */
    private fun hasSequentialChars(password: String): Boolean {
        if (password.length < 3) return false

        for (i in 0 until password.length - 2) {
            val c1 = password[i].code
            val c2 = password[i + 1].code
            val c3 = password[i + 2].code

            // Check ascending sequence
            if (c2 == c1 + 1 && c3 == c2 + 1) return true
            // Check descending sequence
            if (c2 == c1 - 1 && c3 == c2 - 1) return true
        }

        return false
    }

    /**
     * Checks for repeated characters (aaa, 111, etc.)
     */
    private fun hasRepeatedChars(password: String): Boolean {
        if (password.length < 3) return false

        for (i in 0 until password.length - 2) {
            if (password[i] == password[i + 1] && password[i] == password[i + 2]) {
                return true
            }
        }

        return false
    }

    /**
     * Checks for common keyboard patterns (qwerty, asdfgh, etc.)
     */
    private fun hasKeyboardPattern(password: String): Boolean {
        val lowerPassword = password.lowercase()
        return KEYBOARD_PATTERNS.any { lowerPassword.contains(it) }
    }

    /**
     * Generates suggestions to improve password strength.
     */
    private fun generateSuggestions(password: String, patterns: Set<Pattern>): List<String> {
        val suggestions = mutableListOf<String>()

        if (Pattern.TOO_SHORT in patterns) {
            suggestions.add("Use at least 12 characters for better security")
        } else if (Pattern.SHORT in patterns) {
            suggestions.add("Consider using a longer password (16+ characters)")
        }

        if (Pattern.NO_UPPERCASE in patterns) {
            suggestions.add("Add uppercase letters (A-Z)")
        }

        if (Pattern.NO_LOWERCASE in patterns) {
            suggestions.add("Add lowercase letters (a-z)")
        }

        if (Pattern.NO_NUMBERS in patterns) {
            suggestions.add("Add numbers (0-9)")
        }

        if (Pattern.NO_SYMBOLS in patterns) {
            suggestions.add("Add special characters (!@#$%^&*)")
        }

        if (Pattern.COMMON_PASSWORD in patterns) {
            suggestions.add("This is a common password - use a unique one")
        }

        if (Pattern.SEQUENTIAL in patterns || Pattern.KEYBOARD_PATTERN in patterns) {
            suggestions.add("Avoid predictable patterns and sequences")
        }

        if (Pattern.REPEATED in patterns) {
            suggestions.add("Avoid repeated characters")
        }

        if (suggestions.isEmpty() && password.length < 16) {
            suggestions.add("Excellent! Consider making it even longer for maximum security")
        }

        return suggestions
    }

    /**
     * Generates warnings for critical weaknesses.
     */
    private fun generateWarnings(patterns: Set<Pattern>): List<String> {
        val warnings = mutableListOf<String>()

        if (Pattern.TOO_SHORT in patterns) {
            warnings.add("Password is too short - minimum 8 characters required")
        }

        if (Pattern.COMMON_PASSWORD in patterns) {
            warnings.add("This password is commonly used and easily guessed")
        }

        return warnings
    }

    /**
     * Determines overall strength level.
     */
    private fun determineStrength(
        entropy: Double,
        length: Int,
        patterns: Set<Pattern>
    ): StrengthLevel {
        // Critical weaknesses = very weak
        if (Pattern.TOO_SHORT in patterns || Pattern.COMMON_PASSWORD in patterns) {
            return StrengthLevel.VERY_WEAK
        }

        // Entropy-based scoring
        return when {
            entropy >= 80 && length >= 14 -> StrengthLevel.VERY_STRONG  // ~128 bits for 16 chars mixed
            entropy >= 60 && length >= 12 -> StrengthLevel.STRONG        // ~80 bits for 12 chars mixed
            entropy >= 40 && length >= 10 -> StrengthLevel.FAIR          // ~50 bits for 10 chars mixed
            entropy >= 28 -> StrengthLevel.WEAK                          // ~35 bits minimum
            else -> StrengthLevel.VERY_WEAK
        }
    }

    /**
     * Formats crack time into human-readable string.
     */
    fun formatCrackTime(seconds: Long): String {
        return when {
            seconds < 1 -> "Instantly"
            seconds < 60 -> "$seconds seconds"
            seconds < 3600 -> "${seconds / 60} minutes"
            seconds < 86400 -> "${seconds / 3600} hours"
            seconds < 2592000 -> "${seconds / 86400} days"
            seconds < 31536000 -> "${seconds / 2592000} months"
            seconds < 3153600000 -> "${seconds / 31536000} years"
            else -> "Centuries"
        }
    }

    /**
     * Detected password patterns.
     */
    private enum class Pattern {
        TOO_SHORT,
        SHORT,
        NO_LOWERCASE,
        NO_UPPERCASE,
        NO_NUMBERS,
        NO_SYMBOLS,
        COMMON_PASSWORD,
        SEQUENTIAL,
        REPEATED,
        KEYBOARD_PATTERN
    }

    companion object {
        // Top 100 most common passwords (subset)
        private val COMMON_PASSWORDS = setOf(
            "password", "123456", "12345678", "qwerty", "abc123", "monkey",
            "1234567", "letmein", "trustno1", "dragon", "baseball", "111111",
            "iloveyou", "master", "sunshine", "ashley", "bailey", "passw0rd",
            "shadow", "123123", "654321", "superman", "qazwsx", "michael",
            "football", "password1", "welcome", "admin", "user", "root"
        )

        // Common keyboard patterns
        private val KEYBOARD_PATTERNS = setOf(
            "qwerty", "asdfgh", "zxcvbn", "qwertyuiop", "asdfghjkl",
            "zxcvbnm", "1qaz2wsx", "qazwsx", "0987654321"
        )
    }
}