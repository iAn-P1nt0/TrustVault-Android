package com.trustvault.android.security

import com.trustvault.android.util.secureWipe
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles password hashing using Argon2 algorithm.
 * Provides secure password verification for master password.
 */
@Singleton
class PasswordHasher @Inject constructor() {

    /**
     * Pluggable engine to allow replacing Argon2 implementation in tests.
     */
    internal interface Engine {
        fun hash(password: ByteArray, salt: ByteArray, tCostInIterations: Int, mCostInKibibyte: Int): String
        fun verify(password: ByteArray, encoded: String): Boolean
    }

    // Lazily initialized engine to allow tests to inject without touching native libs
    private var engine: Engine? = null
    private fun getEngine(): Engine {
        val existing = engine
        if (existing != null) return existing
        // Reflective load to avoid class initialization during unit tests
        val real = Class.forName("com.trustvault.android.security.PasswordHasherRealEngine")
            .getDeclaredConstructor()
            .newInstance() as Engine
        engine = real
        return real
    }

    // Visible for unit tests to inject a fake engine and avoid native libs
    internal fun setTestEngine(forTests: Engine) {
        engine = forTests
    }

    /**
     * Hashes a password using Argon2id algorithm.
     * Returns the hash as a String that can be stored.
     *
     * SECURITY: Accepts CharArray for secure memory handling. The CharArray
     * should be wiped by the caller after this method returns.
     *
     * @param password The password to hash as CharArray
     * @return The Argon2id hash as a String (safe to store)
     */
    fun hashPassword(password: CharArray): String {
        // Convert CharArray to ByteArray for hashing
        val passwordBytes = String(password).toByteArray(Charsets.UTF_8)

        try {
            val hashEncoded = getEngine().hash(
                password = passwordBytes,
                salt = generateSalt(),
                tCostInIterations = T_COST,
                mCostInKibibyte = M_COST
            )
            return hashEncoded
        } finally {
            // SECURITY CONTROL: Clear password bytes from memory
            passwordBytes.secureWipe()
        }
    }

    /**
     * Verifies a password against a stored hash.
     *
     * SECURITY: Accepts CharArray for secure memory handling. The CharArray
     * should be wiped by the caller after this method returns.
     *
     * @param password The password to verify as CharArray
     * @param hash The stored Argon2id hash
     * @return true if password matches hash, false otherwise
     */
    fun verifyPassword(password: CharArray, hash: String): Boolean {
        return try {
            // Convert CharArray to ByteArray for verification
            val passwordBytes = String(password).toByteArray(Charsets.UTF_8)

            try {
                getEngine().verify(passwordBytes, hash)
            } finally {
                // SECURITY CONTROL: Clear password bytes from memory
                passwordBytes.secureWipe()
            }
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
