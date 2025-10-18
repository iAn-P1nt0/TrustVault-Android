package com.trustvault.android.util

/**
 * Test-friendly time provider for mocking current time in unit tests.
 *
 * SECURITY NOTE: This interface allows time manipulation for testing
 * cache expiration, auto-lock timeouts, and other time-based operations.
 * Never use in production; use System.currentTimeMillis() directly.
 *
 * Usage:
 * ```kotlin
 * val testTime = TestTimeProvider()
 * testTime.setCurrentTimeMillis(1000L)
 * assertEquals(1000L, testTime.currentTimeMillis())
 * testTime.advanceTimeMillis(500L)
 * assertEquals(1500L, testTime.currentTimeMillis())
 * ```
 */
interface TimeProvider {
    /**
     * Returns current time in milliseconds since epoch.
     */
    fun currentTimeMillis(): Long
}

/**
 * Production implementation using System.currentTimeMillis().
 */
class SystemTimeProvider : TimeProvider {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}

/**
 * Test implementation with manual time control.
 */
class TestTimeProvider : TimeProvider {
    private var currentTime: Long = System.currentTimeMillis()

    /**
     * Manually set current time.
     *
     * @param timeMillis Time in milliseconds since epoch
     */
    fun setCurrentTimeMillis(timeMillis: Long) {
        currentTime = timeMillis
    }

    /**
     * Advance time by specified milliseconds.
     *
     * @param deltaMillis Amount to advance time
     */
    fun advanceTimeMillis(deltaMillis: Long) {
        currentTime += deltaMillis
    }

    /**
     * Reset to system current time.
     */
    fun reset() {
        currentTime = System.currentTimeMillis()
    }

    override fun currentTimeMillis(): Long = currentTime
}
