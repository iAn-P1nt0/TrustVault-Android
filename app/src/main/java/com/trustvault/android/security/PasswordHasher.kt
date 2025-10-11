package com.trustvault.android.security

import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles password hashing using Argon2 algorithm.
 * Provides secure password verification for master password.
 */
@Singleton
class PasswordHasher @Inject constructor() {

    private val argon2Kt = Argon2Kt()

    /**
     * Hashes a password using Argon2id algorithm.
     * Returns the hash as a String that can be stored.
     */
    fun hashPassword(password: String): String {
        val passwordBytes = password.toByteArray()
        val hash = argon2Kt.hash(
            mode = Argon2Mode.ARGON2_ID,
            password = passwordBytes,
            salt = generateSalt(),
            tCostInIterations = T_COST,
            mCostInKibibyte = M_COST
        )
        return hash.encodedOutputAsString()
    }

    /**
     * Verifies a password against a stored hash.
     */
    fun verifyPassword(password: String, hash: String): Boolean {
        return try {
            val passwordBytes = password.toByteArray()
            argon2Kt.verify(
                mode = Argon2Mode.ARGON2_ID,
                encoded = hash,
                password = passwordBytes
            )
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Generates a cryptographically secure random salt.
     */
    private fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_LENGTH)
        java.security.SecureRandom().nextBytes(salt)
        return salt
    }

    /**
     * Evaluates password strength.
     */
    fun evaluatePasswordStrength(password: String): PasswordStrength {
        if (password.length < 8) return PasswordStrength.WEAK
        
        var score = 0
        if (password.length >= 12) score++
        if (password.length >= 16) score++
        if (password.any { it.isUpperCase() }) score++
        if (password.any { it.isLowerCase() }) score++
        if (password.any { it.isDigit() }) score++
        if (password.any { !it.isLetterOrDigit() }) score++
        
        return when (score) {
            in 0..2 -> PasswordStrength.WEAK
            in 3..4 -> PasswordStrength.MEDIUM
            else -> PasswordStrength.STRONG
        }
    }

    companion object {
        private const val T_COST = 3 // Number of iterations
        private const val M_COST = 65536 // Memory cost in KiB (64 MB)
        private const val SALT_LENGTH = 16 // Salt length in bytes
    }
}

enum class PasswordStrength {
    WEAK, MEDIUM, STRONG
}
