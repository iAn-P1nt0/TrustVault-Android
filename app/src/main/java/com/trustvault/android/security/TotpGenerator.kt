package com.trustvault.android.security

import android.util.Base64
import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.experimental.and

/**
 * Time-based One-Time Password (TOTP) generator.
 * Implements RFC 6238 for 2FA token generation.
 *
 * Compatible with Google Authenticator, Authy, and other TOTP apps.
 * Used by password managers like Bitwarden and 1Password for 2FA support.
 *
 * Security Features:
 * - HMAC-SHA1 algorithm (standard for TOTP)
 * - 30-second time step (industry standard)
 * - 6-digit codes (configurable to 8)
 * - Secure secret storage
 */
@Singleton
class TotpGenerator @Inject constructor() {

    /**
     * Generates a TOTP code from a secret key.
     *
     * @param secret The Base32-encoded secret key (from QR code)
     * @param timeSeconds Current Unix timestamp in seconds (default: now)
     * @param digits Number of digits in the code (6 or 8)
     * @param period Time step in seconds (default: 30)
     * @return The generated TOTP code as a string
     */
    fun generate(
        secret: String,
        timeSeconds: Long = System.currentTimeMillis() / 1000,
        digits: Int = 6,
        period: Int = 30
    ): TotpResult {
        require(digits in 6..8) { "Digits must be 6, 7, or 8" }
        require(period > 0) { "Period must be positive" }

        try {
            // Decode Base32 secret
            val secretBytes = decodeBase32(secret)

            // Calculate time step
            val timeStep = timeSeconds / period

            // Generate HMAC-SHA1 hash
            val hash = generateHmacSha1(secretBytes, timeStep)

            // Extract dynamic binary code
            val code = extractCode(hash, digits)

            // Calculate time remaining
            val remainingSeconds = period - (timeSeconds % period).toInt()

            return TotpResult(
                code = code,
                remainingSeconds = remainingSeconds,
                period = period
            )
        } catch (e: Exception) {
            throw TotpException("Failed to generate TOTP: ${e.message}", e)
        }
    }

    /**
     * Generates a TOTP URI for QR code generation.
     * Format: otpauth://totp/Label?secret=SECRET&issuer=ISSUER
     *
     * @param secret The Base32-encoded secret
     * @param accountName Account/email identifier
     * @param issuer Service name (e.g., "TrustVault")
     * @return TOTP URI string
     */
    fun generateUri(
        secret: String,
        accountName: String,
        issuer: String = "TrustVault",
        digits: Int = 6,
        period: Int = 30
    ): String {
        val label = "${issuer}:${accountName}"
        return "otpauth://totp/${java.net.URLEncoder.encode(label, "UTF-8")}" +
                "?secret=$secret" +
                "&issuer=${java.net.URLEncoder.encode(issuer, "UTF-8")}" +
                "&digits=$digits" +
                "&period=$period" +
                "&algorithm=SHA1"
    }

    /**
     * Parses a TOTP URI and extracts secret and parameters.
     */
    fun parseUri(uri: String): TotpConfig? {
        try {
            if (!uri.startsWith("otpauth://totp/")) return null

            val url = java.net.URL(uri.replace("otpauth://", "https://"))
            val path = url.path.removePrefix("/")
            val query = url.query?.split("&")?.associate {
                val (key, value) = it.split("=")
                key to java.net.URLDecoder.decode(value, "UTF-8")
            } ?: emptyMap()

            val secret = query["secret"] ?: return null
            val issuer = query["issuer"] ?: "Unknown"
            val accountName = if (path.contains(":")) {
                path.substringAfter(":")
            } else {
                path
            }
            val digits = query["digits"]?.toIntOrNull() ?: 6
            val period = query["period"]?.toIntOrNull() ?: 30

            return TotpConfig(
                secret = secret,
                accountName = accountName,
                issuer = issuer,
                digits = digits,
                period = period
            )
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Validates a TOTP code against a secret.
     * Checks current time window and +/- 1 time window for clock drift.
     */
    fun validate(
        secret: String,
        code: String,
        timeSeconds: Long = System.currentTimeMillis() / 1000,
        digits: Int = 6,
        period: Int = 30,
        allowedTimeSkew: Int = 1
    ): Boolean {
        // Check current and nearby time windows
        for (i in -allowedTimeSkew..allowedTimeSkew) {
            val checkTime = timeSeconds + (i * period)
            val result = generate(secret, checkTime, digits, period)
            if (result.code == code) {
                return true
            }
        }
        return false
    }

    /**
     * Generates HMAC-SHA1 hash.
     */
    private fun generateHmacSha1(key: ByteArray, counter: Long): ByteArray {
        val data = ByteBuffer.allocate(8).putLong(counter).array()
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        return mac.doFinal(data)
    }

    /**
     * Extracts dynamic truncation code from HMAC hash.
     */
    private fun extractCode(hash: ByteArray, digits: Int): String {
        // Dynamic truncation
        val offset = (hash[hash.size - 1] and 0x0f).toInt()

        val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
                ((hash[offset + 1].toInt() and 0xff) shl 16) or
                ((hash[offset + 2].toInt() and 0xff) shl 8) or
                (hash[offset + 3].toInt() and 0xff)

        val code = binary % (10.0.pow(digits)).toInt()

        // Pad with leading zeros
        return code.toString().padStart(digits, '0')
    }

    /**
     * Decodes Base32-encoded string to bytes.
     * Base32 is used for TOTP secrets (easier to read than Base64).
     */
    private fun decodeBase32(input: String): ByteArray {
        val cleanInput = input.uppercase().replace(" ", "").replace("-", "")
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

        val bytes = mutableListOf<Byte>()
        var buffer = 0L
        var bitsLeft = 0

        for (char in cleanInput) {
            if (char == '=') break

            val value = alphabet.indexOf(char)
            if (value == -1) throw IllegalArgumentException("Invalid Base32 character: $char")

            buffer = (buffer shl 5) or value.toLong()
            bitsLeft += 5

            if (bitsLeft >= 8) {
                bytes.add((buffer shr (bitsLeft - 8)).toByte())
                bitsLeft -= 8
            }
        }

        return bytes.toByteArray()
    }

    /**
     * Extension function for power calculation.
     */
    private fun Double.pow(n: Int): Double {
        var result = 1.0
        repeat(n) { result *= this }
        return result
    }

    /**
     * TOTP generation result.
     */
    data class TotpResult(
        val code: String,
        val remainingSeconds: Int,
        val period: Int
    ) {
        val progress: Float
            get() = remainingSeconds.toFloat() / period.toFloat()
    }

    /**
     * TOTP configuration parsed from URI.
     */
    data class TotpConfig(
        val secret: String,
        val accountName: String,
        val issuer: String,
        val digits: Int = 6,
        val period: Int = 30
    )
}

/**
 * Exception thrown during TOTP operations.
 */
class TotpException(message: String, cause: Throwable? = null) : Exception(message, cause)