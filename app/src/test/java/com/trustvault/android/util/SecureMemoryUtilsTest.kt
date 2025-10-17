package com.trustvault.android.util

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for SecureMemoryUtils extension functions.
 *
 * Tests verify:
 * - CharArray secure wipe works correctly
 * - ByteArray secure wipe works correctly
 * - Use functions properly execute and clean up
 */
class SecureMemoryUtilsTest {

    @Test
    fun `CharArray secureWipe clears all characters`() {
        val chars = "TestPassword123!".toCharArray()

        chars.secureWipe()

        // Verify all characters are null
        assertTrue("All characters should be null after wipe",
            chars.all { it == '\u0000' })
    }

    @Test
    fun `ByteArray secureWipe clears all bytes`() {
        val bytes = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)

        bytes.secureWipe()

        // Verify all bytes are zero
        assertTrue("All bytes should be zero after wipe",
            bytes.all { it == 0.toByte() })
    }

    @Test
    fun `CharArray use executes block and wipes array`() {
        val originalChars = "TestPassword123!".toCharArray()
        val chars = originalChars.copyOf()
        var blockExecuted = false

        val result = chars.use { array ->
            blockExecuted = true
            // Verify array is still intact inside block
            assertArrayEquals("Array should be intact inside use block", originalChars, array)
            "result"
        }

        // Verify block was executed
        assertTrue("Block should have been executed", blockExecuted)
        assertEquals("Result should be returned", "result", result)

        // Verify array was wiped after block
        assertTrue("Array should be wiped after use block",
            chars.all { it == '\u0000' })
    }

    @Test
    fun `ByteArray use executes block and wipes array`() {
        val originalBytes = byteArrayOf(1, 2, 3, 4, 5)
        val bytes = originalBytes.copyOf()
        var blockExecuted = false

        val result = bytes.use { array ->
            blockExecuted = true
            // Verify array is still intact inside block
            assertArrayEquals("Array should be intact inside use block", originalBytes, array)
            42
        }

        // Verify block was executed
        assertTrue("Block should have been executed", blockExecuted)
        assertEquals("Result should be returned", 42, result)

        // Verify array was wiped after block
        assertTrue("Array should be wiped after use block",
            bytes.all { it == 0.toByte() })
    }

    @Test
    fun `CharArray use wipes array even when exception is thrown`() {
        val chars = "TestPassword123!".toCharArray()

        try {
            chars.use {
                throw RuntimeException("Test exception")
            }
            fail("Exception should have been thrown")
        } catch (e: RuntimeException) {
            assertEquals("Test exception", e.message)
        }

        // Verify array was wiped despite exception
        assertTrue("Array should be wiped even when exception is thrown",
            chars.all { it == '\u0000' })
    }

    @Test
    fun `ByteArray use wipes array even when exception is thrown`() {
        val bytes = byteArrayOf(1, 2, 3, 4, 5)

        try {
            bytes.use {
                throw RuntimeException("Test exception")
            }
            fail("Exception should have been thrown")
        } catch (e: RuntimeException) {
            assertEquals("Test exception", e.message)
        }

        // Verify array was wiped despite exception
        assertTrue("Array should be wiped even when exception is thrown",
            bytes.all { it == 0.toByte() })
    }

    @Test
    fun `String toSecureCharArray creates CharArray`() {
        val password = "TestPassword123!"
        val chars = password.toSecureCharArray()

        assertArrayEquals("CharArray should match original string",
            password.toCharArray(), chars)

        // Clean up
        chars.secureWipe()
    }

    @Test
    fun `secureWipe handles empty CharArray`() {
        val chars = CharArray(0)

        // Should not throw exception
        chars.secureWipe()

        assertEquals("Empty array should remain empty", 0, chars.size)
    }

    @Test
    fun `secureWipe handles empty ByteArray`() {
        val bytes = ByteArray(0)

        // Should not throw exception
        bytes.secureWipe()

        assertEquals("Empty array should remain empty", 0, bytes.size)
    }

    @Test
    fun `secureWipe handles large CharArray`() {
        val chars = CharArray(10000) { 'a' }

        chars.secureWipe()

        assertTrue("All characters in large array should be null",
            chars.all { it == '\u0000' })
    }

    @Test
    fun `secureWipe handles large ByteArray`() {
        val bytes = ByteArray(10000) { 42 }

        bytes.secureWipe()

        assertTrue("All bytes in large array should be zero",
            bytes.all { it == 0.toByte() })
    }
}
