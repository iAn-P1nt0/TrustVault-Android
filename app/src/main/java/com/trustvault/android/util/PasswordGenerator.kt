package com.trustvault.android.util

import java.security.SecureRandom

/**
 * Generates secure random passwords.
 */
class PasswordGenerator {

    fun generate(
        length: Int = 16,
        includeUppercase: Boolean = true,
        includeLowercase: Boolean = true,
        includeNumbers: Boolean = true,
        includeSymbols: Boolean = true
    ): String {
        require(length > 0) { "Password length must be greater than 0" }
        require(includeUppercase || includeLowercase || includeNumbers || includeSymbols) {
            "At least one character type must be selected"
        }

        val charset = buildString {
            if (includeUppercase) append(UPPERCASE)
            if (includeLowercase) append(LOWERCASE)
            if (includeNumbers) append(NUMBERS)
            if (includeSymbols) append(SYMBOLS)
        }

        val random = SecureRandom()
        return (1..length)
            .map { charset[random.nextInt(charset.length)] }
            .joinToString("")
    }

    companion object {
        private const val UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        private const val LOWERCASE = "abcdefghijklmnopqrstuvwxyz"
        private const val NUMBERS = "0123456789"
        private const val SYMBOLS = "!@#\$%^&*()_+-=[]{}|;:,.<>?"
    }
}
