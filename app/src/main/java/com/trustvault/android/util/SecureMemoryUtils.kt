package com.trustvault.android.util

import net.sqlcipher.database.SQLiteDatabase

/**
 * Utility functions for secure memory handling of sensitive data.
 *
 * These functions ensure that sensitive data (passwords, keys) are properly
 * cleared from memory after use to prevent memory dump attacks.
 *
 * Security Best Practices:
 * - Use CharArray instead of String for passwords (Strings are immutable)
 * - Clear arrays after use with secure wipe functions
 * - Convert to ByteArray only when necessary, then immediately clear
 */

/**
 * Securely wipes a CharArray by overwriting all characters with null characters.
 * SECURITY CONTROL: Prevents sensitive data from remaining in memory.
 *
 * @receiver CharArray to be wiped
 */
fun CharArray.secureWipe() {
    fill('\u0000')
}

/**
 * Securely wipes a ByteArray by overwriting all bytes with zeros.
 * SECURITY CONTROL: Prevents sensitive data from remaining in memory.
 *
 * @receiver ByteArray to be wiped
 */
fun ByteArray.secureWipe() {
    fill(0)
}

/**
 * Converts a CharArray to ByteArray suitable for SQLCipher.
 * Uses SQLiteDatabase.getBytes() which properly handles UTF-8 encoding.
 *
 * IMPORTANT: The returned ByteArray must be cleared after use with secureWipe().
 *
 * @receiver CharArray password to convert
 * @return ByteArray suitable for SQLCipher SupportFactory
 */
fun CharArray.toSQLCipherBytes(): ByteArray {
    return SQLiteDatabase.getBytes(this)
}

/**
 * Converts a String to CharArray for secure password handling.
 * The returned CharArray should be wiped after use.
 *
 * IMPORTANT: Only use this when you must accept String input (e.g., from UI).
 * Prefer CharArray throughout the application when possible.
 *
 * @receiver String password to convert
 * @return CharArray that must be wiped after use
 */
fun String.toSecureCharArray(): CharArray {
    return this.toCharArray()
}

/**
 * Executes a block with a CharArray and ensures it's securely wiped afterwards.
 * Use this for temporary password handling operations.
 *
 * Example:
 * ```kotlin
 * password.use { chars ->
 *     // Use chars here
 * } // chars automatically wiped
 * ```
 *
 * @param block Function to execute with the CharArray
 * @return Result of the block execution
 */
inline fun <T> CharArray.use(block: (CharArray) -> T): T {
    try {
        return block(this)
    } finally {
        secureWipe()
    }
}

/**
 * Executes a block with a ByteArray and ensures it's securely wiped afterwards.
 * Use this for temporary key/password byte operations.
 *
 * Example:
 * ```kotlin
 * keyBytes.use { bytes ->
 *     // Use bytes here
 * } // bytes automatically wiped
 * ```
 *
 * @param block Function to execute with the ByteArray
 * @return Result of the block execution
 */
inline fun <T> ByteArray.use(block: (ByteArray) -> T): T {
    try {
        return block(this)
    } finally {
        secureWipe()
    }
}