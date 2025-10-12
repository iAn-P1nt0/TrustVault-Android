package com.trustvault.android.security.ocr

import android.net.Uri
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parses OCR-extracted text to identify credential fields (username, password, website).
 *
 * Uses regex patterns and contextual analysis to extract structured data from
 * unstructured text captured from browser login screens.
 *
 * SECURITY NOTE: This class handles plaintext credentials temporarily.
 * Returns CharArray (not String) for clearable memory.
 * No logging of extracted values.
 *
 * Parsing Strategy:
 * 1. Normalize text (whitespace, common OCR errors)
 * 2. Pattern matching (email regex, URL regex)
 * 3. Context-based extraction (keywords: "username", "password", etc.)
 * 4. Confidence scoring (future enhancement)
 *
 * @constructor Creates parser with no configuration (stateless)
 */
@Singleton
class CredentialFieldParser @Inject constructor() {

    companion object {
        private const val TAG = "CredentialFieldParser"

        // Email regex (RFC 5322 simplified for practical use)
        private val EMAIL_REGEX = Regex(
            """[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}"""
        )

        // URL regex (http/https with domain)
        private val URL_REGEX = Regex(
            """https?://[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}[^\s]*""",
            RegexOption.IGNORE_CASE
        )

        // Username context keywords (case-insensitive)
        private val USERNAME_KEYWORDS = listOf(
            "username", "user", "email", "login", "account", "id"
        )

        // Password context keywords (case-insensitive)
        private val PASSWORD_KEYWORDS = listOf(
            "password", "pass", "pwd", "passcode", "pin"
        )

        // Website/URL context keywords
        private val WEBSITE_KEYWORDS = listOf(
            "site", "website", "url", "address", "domain"
        )

        // Common OCR errors and corrections
        private val OCR_CORRECTIONS = mapOf(
            "l" to "1",     // lowercase L to 1 (in passwords)
            "O" to "0",     // uppercase O to 0 (in passwords)
            "I" to "1",     // uppercase I to 1 (in passwords)
            "S" to "5"      // S to 5 (in passwords, context-dependent)
        )
    }

    /**
     * Parse OCR text and extract credential fields.
     *
     * @param rawText Unstructured text from ML Kit text recognition
     * @return OcrResult containing extracted fields (nullable CharArrays)
     * @throws OcrException.ParsingFailedException if parsing encounters fatal error
     */
    fun parseText(rawText: String): OcrResult {
        try {
            // Log only text length, not content
            Log.d(TAG, "Parsing text (${rawText.length} chars)")

            // Step 1: Normalize text
            val normalized = normalizeText(rawText)
            val lines = normalized.lines()

            // Step 2: Extract fields
            val username = extractUsername(lines)
            val password = extractPassword(lines)
            val website = extractWebsite(lines)

            // Log only presence flags, not values
            Log.d(
                TAG, "Extraction complete: " +
                        "username=${username != null}, " +
                        "password=${password != null}, " +
                        "website=${website != null}"
            )

            return OcrResult(username, password, website)

        } catch (e: Exception) {
            Log.e(TAG, "Parsing failed: ${e.javaClass.simpleName}", e)
            throw OcrException.ParsingFailedException(
                "Failed to parse credential fields",
                e
            )
        }
    }

    /**
     * Normalize OCR text for better parsing.
     *
     * - Collapse multiple whitespace to single space
     * - Trim leading/trailing whitespace
     * - Preserve line breaks for context
     *
     * @param text Raw OCR text
     * @return Normalized text
     */
    private fun normalizeText(text: String): String {
        return text
            .lines()
            .map { line -> line.trim().replace(Regex("\\s+"), " ") }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }

    /**
     * Extract username/email field.
     *
     * Strategy:
     * 1. Look for email pattern (highest confidence)
     * 2. Look for username context keywords
     * 3. Check line after keyword (label: value pattern)
     * 4. Check same line after colon (inline pattern)
     *
     * @param lines Normalized text lines
     * @return Username as CharArray, or null if not found
     */
    private fun extractUsername(lines: List<String>): CharArray? {
        // Strategy 1: Email pattern (highest confidence)
        for (line in lines) {
            EMAIL_REGEX.find(line)?.let { match ->
                val email = match.value
                // Validate email has reasonable length
                if (email.length in 5..100) {
                    return email.toCharArray()
                }
            }
        }

        // Strategy 2: Context-based extraction
        for (i in lines.indices) {
            val line = lines[i].lowercase()

            // Check if line contains username keyword
            val hasKeyword = USERNAME_KEYWORDS.any { keyword ->
                line.contains(keyword)
            }

            if (hasKeyword) {
                // Check next line (label on one line, value on next)
                if (i + 1 < lines.size) {
                    val nextLine = lines[i + 1].trim()
                    if (isValidUsername(nextLine)) {
                        return nextLine.toCharArray()
                    }
                }

                // Check same line after colon (label: value pattern)
                val colonIndex = line.indexOf(':')
                if (colonIndex != -1 && colonIndex + 1 < line.length) {
                    val value = lines[i].substring(colonIndex + 1).trim()
                    if (isValidUsername(value)) {
                        return value.toCharArray()
                    }
                }

                // Check same line after keyword
                USERNAME_KEYWORDS.forEach { keyword ->
                    val keywordIndex = line.indexOf(keyword)
                    if (keywordIndex != -1) {
                        val afterKeyword = lines[i].substring(
                            keywordIndex + keyword.length
                        ).trim()
                        // Remove leading colon if present
                        val cleaned = afterKeyword.removePrefix(":")
                            .trim()
                        if (isValidUsername(cleaned)) {
                            return cleaned.toCharArray()
                        }
                    }
                }
            }
        }

        return null
    }

    /**
     * Extract password field.
     *
     * Strategy:
     * 1. Look for password context keywords
     * 2. Check line after keyword
     * 3. Check same line after colon
     * 4. Validate password characteristics (length, complexity)
     *
     * Note: Passwords are often masked (********) in screenshots,
     * so detection may be limited.
     *
     * @param lines Normalized text lines
     * @return Password as CharArray, or null if not found
     */
    private fun extractPassword(lines: List<String>): CharArray? {
        for (i in lines.indices) {
            val line = lines[i].lowercase()

            // Check if line contains password keyword
            val hasKeyword = PASSWORD_KEYWORDS.any { keyword ->
                line.contains(keyword)
            }

            if (hasKeyword) {
                // Check next line
                if (i + 1 < lines.size) {
                    val nextLine = lines[i + 1].trim()
                    if (isValidPassword(nextLine)) {
                        return nextLine.toCharArray()
                    }
                }

                // Check same line after colon
                val colonIndex = line.indexOf(':')
                if (colonIndex != -1 && colonIndex + 1 < line.length) {
                    val value = lines[i].substring(colonIndex + 1).trim()
                    if (isValidPassword(value)) {
                        return value.toCharArray()
                    }
                }

                // Check same line after keyword
                PASSWORD_KEYWORDS.forEach { keyword ->
                    val keywordIndex = line.indexOf(keyword)
                    if (keywordIndex != -1) {
                        val afterKeyword = lines[i].substring(
                            keywordIndex + keyword.length
                        ).trim()
                        val cleaned = afterKeyword.removePrefix(":")
                            .trim()
                        if (isValidPassword(cleaned)) {
                            return cleaned.toCharArray()
                        }
                    }
                }
            }
        }

        return null
    }

    /**
     * Extract website URL field.
     *
     * Strategy:
     * 1. Look for URL pattern (https://...)
     * 2. Look for website context keywords
     * 3. Validate URL format
     *
     * @param lines Normalized text lines
     * @return Website URL as CharArray, or null if not found
     */
    private fun extractWebsite(lines: List<String>): CharArray? {
        // Strategy 1: URL pattern (highest confidence)
        for (line in lines) {
            URL_REGEX.find(line)?.let { match ->
                val url = match.value
                if (isValidUrl(url)) {
                    return url.toCharArray()
                }
            }
        }

        // Strategy 2: Context-based extraction
        for (i in lines.indices) {
            val line = lines[i].lowercase()

            val hasKeyword = WEBSITE_KEYWORDS.any { keyword ->
                line.contains(keyword)
            }

            if (hasKeyword) {
                // Check next line
                if (i + 1 < lines.size) {
                    val nextLine = lines[i + 1].trim()
                    if (isValidUrl(nextLine)) {
                        return nextLine.toCharArray()
                    }
                }

                // Check same line after colon
                val colonIndex = line.indexOf(':')
                if (colonIndex != -1 && colonIndex + 1 < line.length) {
                    val value = lines[i].substring(colonIndex + 1).trim()
                    if (isValidUrl(value)) {
                        return value.toCharArray()
                    }
                }
            }
        }

        return null
    }

    /**
     * Validate if string looks like a username.
     *
     * Rules:
     * - Not empty
     * - Length between 3 and 100 characters
     * - Not all special characters
     * - Not a keyword itself
     *
     * @param value Candidate username string
     * @return True if valid username format
     */
    private fun isValidUsername(value: String): Boolean {
        if (value.isEmpty() || value.length < 3 || value.length > 100) {
            return false
        }

        // Not a keyword itself
        val lowerValue = value.lowercase()
        if (USERNAME_KEYWORDS.any { it == lowerValue } ||
            PASSWORD_KEYWORDS.any { it == lowerValue }) {
            return false
        }

        // Not all special characters
        if (value.all { !it.isLetterOrDigit() }) {
            return false
        }

        // Not masked value
        if (value.all { it == '*' || it == '•' || it == '●' }) {
            return false
        }

        return true
    }

    /**
     * Validate if string looks like a password.
     *
     * Rules:
     * - Length at least 6 characters (most sites require 8+, but allow 6 for tolerance)
     * - Not empty
     * - Not all asterisks (masked password)
     * - Contains mix of character types (letters, numbers, symbols)
     *
     * @param value Candidate password string
     * @return True if valid password format
     */
    private fun isValidPassword(value: String): Boolean {
        if (value.isEmpty() || value.length < 6 || value.length > 128) {
            return false
        }

        // Not masked value
        if (value.all { it == '*' || it == '•' || it == '●' }) {
            return false
        }

        // Not a keyword itself
        val lowerValue = value.lowercase()
        if (PASSWORD_KEYWORDS.any { it == lowerValue }) {
            return false
        }

        // Prefer passwords with some complexity (letters, numbers, or symbols)
        // But don't require all three (too restrictive)
        val hasLetter = value.any { it.isLetter() }
        val hasDigit = value.any { it.isDigit() }
        val hasSpecial = value.any { !it.isLetterOrDigit() }

        // At least one of: letters, digits, or symbols
        return hasLetter || hasDigit || hasSpecial
    }

    /**
     * Validate if string is a valid URL.
     *
     * Uses Android Uri parser for validation.
     *
     * @param url Candidate URL string
     * @return True if valid URL with scheme and host
     */
    private fun isValidUrl(url: String): Boolean {
        return try {
            val uri = Uri.parse(url)
            // Must have scheme (http/https) and host
            uri.scheme != null &&
                    uri.host != null &&
                    (uri.scheme == "http" || uri.scheme == "https")
        } catch (e: Exception) {
            false
        }
    }
}