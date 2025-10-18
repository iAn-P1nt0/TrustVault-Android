package com.trustvault.android.util

import java.security.SecureRandom

/**
 * Deterministic secure random for testing cryptographic operations.
 *
 * SECURITY NOTE: This should NEVER be used in production code.
 * It provides predictable random values for reproducible test results
 * in crypto-dependent code (key derivation, IV generation, etc.).
 *
 * Production code must use System.getSecureRandom() or similar.
 *
 * Usage:
 * ```kotlin
 * val testRandom = DeterministicSecureRandom(seed = 42)
 * val bytes = ByteArray(16)
 * testRandom.nextBytes(bytes)
 * // bytes will always be the same for seed=42
 * ```
 */
class DeterministicSecureRandom(private val seedValue: Long = 0L) : SecureRandom() {
    private var nextGaussian: Double? = null

    init {
        setSeed(seedValue)
    }

    /**
     * Fills buffer with pseudo-random bytes.
     * Pattern: bytes at index i will be (seed + i) % 256
     */
    override fun nextBytes(bytes: ByteArray) {
        for (i in bytes.indices) {
            bytes[i] = ((seedValue + i) % 256).toByte()
        }
    }

    /**
     * Set the seed for reproducibility.
     */
    override fun setSeed(seed: Long) {
        super.setSeed(seed)
    }
}

/**
 * Test-friendly random number generator factory.
 * Allows injection of deterministic random for reproducible tests.
 */
interface RandomProvider {
    /**
     * Get a SecureRandom instance for cryptographic operations.
     */
    fun getSecureRandom(): SecureRandom
}

/**
 * Production implementation using standard SecureRandom.
 */
class SystemRandomProvider : RandomProvider {
    override fun getSecureRandom(): SecureRandom = SecureRandom()
}

/**
 * Test implementation using deterministic random.
 * Each call returns a new instance seeded with an incrementing counter
 * for variation across multiple random number generations in a single test.
 */
class TestRandomProvider(private val baseSeed: Long = 0L) : RandomProvider {
    private var callCount = 0L

    override fun getSecureRandom(): SecureRandom {
        val seed = baseSeed + callCount++
        return DeterministicSecureRandom(seed)
    }

    /**
     * Reset the call counter.
     */
    fun reset() {
        callCount = 0L
    }
}
