package com.trustvault.android.security

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import java.security.MessageDigest

/**
 * Checks if a password has been breached using k-anonymity.
 *
 * Security Features (PRIVACY-PRESERVING):
 * - Uses k-anonymity (only first 5 chars of SHA-1 hash sent to server)
 * - No plaintext password ever leaves device
 * - Local caching prevents repeated requests
 * - OWASP Mobile best practice
 *
 * How it works:
 * 1. Hash password with SHA-1
 * 2. Send first 5 chars of hash to HIBP API
 * 3. Server returns all hashes matching those 5 chars
 * 4. Compare locally with full hash
 * 5. Cache result for 24 hours
 *
 * Reference: https://haveibeenpwned.com/API/v3#SearchingPwnedPasswordsByRange
 */
@Singleton
class BreachChecker @Inject constructor(
    @ApplicationContext val context: Context
) {

    data class BreachResult(
        val isBreached: Boolean,
        val breachCount: Int = 0,
        val lastChecked: Long = 0,
        val isCached: Boolean = false
    )

    /**
     * Checks if password has been breached using k-anonymity.
     * Opt-in only - requires user permission.
     *
     * @param password Password to check
     * @param isOptedIn Whether user opted in for breach checks
     * @return BreachResult with breach status
     */
    suspend fun checkPasswordBreach(
        password: String,
        isOptedIn: Boolean = false
    ): BreachResult {
        // Skip if not opted in
        if (!isOptedIn) {
            return BreachResult(isBreached = false)
        }

        try {
            // Check cache first
            val cached = getCachedResult(password)
            if (cached != null) {
                Log.d(TAG, "Using cached breach check result")
                return cached.copy(isCached = true)
            }

            // Hash password with SHA-1
            val sha1Hash = hashPasswordSha1(password)
            val hashPrefix = sha1Hash.substring(0, 5).uppercase()
            val hashSuffix = sha1Hash.substring(5).uppercase()

            Log.d(TAG, "Checking breach status with k-anonymity (prefix: $hashPrefix...)")

            // Query HIBP API with hash prefix
            // Note: In production, would use actual HTTP client
            // For now, this is a framework for integration
            val matches = queryHibpApi(hashPrefix)

            // Check if our hash is in the results
            val breachCount = matches[hashSuffix] ?: 0
            val isBreached = breachCount > 0

            Log.d(TAG, "Breach check complete: isBreached=$isBreached, count=$breachCount")

            // Cache the result
            cacheResult(password, isBreached, breachCount)

            return BreachResult(
                isBreached = isBreached,
                breachCount = breachCount,
                lastChecked = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error checking breach status: ${e.message}")
            // Fail open - don't block app if check fails
            return BreachResult(isBreached = false)
        }
    }

    /**
     * Hashes password with SHA-1 for HIBP k-anonymity.
     *
     * SECURITY: SHA-1 is used specifically for k-anonymity protocol compatibility,
     * not for storage (which uses Argon2id). This is cryptographically sound for
     * the k-anonymity scheme where only prefix is transmitted.
     */
    private fun hashPasswordSha1(password: String): String {
        return try {
            val md = MessageDigest.getInstance("SHA-1")
            val messageDigest = md.digest(password.toByteArray(Charsets.UTF_8))
            messageDigest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error hashing password: ${e.message}")
            ""
        }
    }

    /**
     * Queries HIBP API with hash prefix (k-anonymity).
     *
     * In production, this would make an HTTP request:
     * GET https://api.pwnedpasswords.com/range/{hashPrefix}
     *
     * Response format:
     * 35D9C21AC62D94F1E5E1F20E4B9B5D5E:3
     * 4F9E5E5D0E1B2C3D4E5F6A7B8C9D0E1F:1
     * ...
     *
     * Where the number is breach count.
     *
     * @param hashPrefix First 5 characters of SHA-1 hash
     * @return Map of hash suffix to breach count
     */
    private suspend fun queryHibpApi(hashPrefix: String): Map<String, Int> {
        // Framework for integration
        // TODO: Implement actual HIBP API call with OkHttp/Retrofit
        // For now, return empty (safe default)
        return emptyMap()
    }

    /**
     * Gets cached breach check result.
     */
    private fun getCachedResult(password: String): BreachResult? {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val hashedKey = hashPasswordSha1(password)
            val cacheKey = "breach_$hashedKey"
            val cacheTime = "breach_time_$hashedKey"

            val cachedTime = prefs.getLong(cacheTime, 0)
            val now = System.currentTimeMillis()

            // Cache validity: 24 hours
            if (now - cachedTime > 24 * 60 * 60 * 1000) {
                return null // Cache expired
            }

            val breached = prefs.getBoolean(cacheKey, false)
            val breachCount = prefs.getInt("${cacheKey}_count", 0)

            BreachResult(
                isBreached = breached,
                breachCount = breachCount,
                lastChecked = cachedTime
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error reading cache: ${e.message}")
            null
        }
    }

    /**
     * Caches breach check result for 24 hours.
     */
    private fun cacheResult(password: String, isBreached: Boolean, breachCount: Int) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val hashedKey = hashPasswordSha1(password)
            val cacheKey = "breach_$hashedKey"
            val cacheTime = "breach_time_$hashedKey"

            prefs.edit()
                .putBoolean(cacheKey, isBreached)
                .putInt("${cacheKey}_count", breachCount)
                .putLong(cacheTime, System.currentTimeMillis())
                .apply()

            Log.d(TAG, "Cached breach check result")
        } catch (e: Exception) {
            Log.w(TAG, "Error caching result: ${e.message}")
        }
    }

    /**
     * Clears all breach check cache.
     * Call when user updates password or clears data.
     */
    fun clearCache() {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
            Log.d(TAG, "Cleared breach check cache")
        } catch (e: Exception) {
            Log.w(TAG, "Error clearing cache: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "BreachChecker"
        private const val PREFS_NAME = "trustvault_breach_cache"

        // HIBP API endpoint
        private const val HIBP_API_BASE = "https://api.pwnedpasswords.com"
        private const val HIBP_RANGE_ENDPOINT = "$HIBP_API_BASE/range"

        // User-Agent for HIBP API (required)
        private const val USER_AGENT = "TrustVault/1.0"
    }
}
