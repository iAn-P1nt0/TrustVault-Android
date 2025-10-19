package com.trustvault.android.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.MessageDigest

class PasswordHasherTest {

    private val hasher = PasswordHasher()

    // Simple fake engine that encodes as FAKE|hex(salt)|hex(sha256(password))
    private class FakeEngine : PasswordHasher.Engine {
        override fun hash(
            password: ByteArray,
            salt: ByteArray,
            tCostInIterations: Int,
            mCostInKibibyte: Int,
            parallelism: Int
        ): String {
            val pwdHash = sha256(password)
            return "FAKE|${salt.toHex()}|${pwdHash.toHex()}"
        }
        override fun verify(password: ByteArray, encoded: String): Boolean {
            val parts = encoded.split("|")
            if (parts.size != 3 || parts[0] != "FAKE") return false
            val expectedPwdHashHex = parts[2]
            val actualPwdHashHex = sha256(password).toHex()
            return expectedPwdHashHex == actualPwdHashHex
        }
        private fun sha256(input: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(input)
        private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    }

    @Before
    fun setUp() {
        hasher.setTestEngine(FakeEngine())
    }

    @Test
    fun `hash and verify succeeds for correct password`() {
        val pwd = "CorrectHorseBatteryStaple".toCharArray()
        val hash = hasher.hashPassword(pwd)
        // Do not reuse pwd; verify with a fresh char array
        val verifyPwd = "CorrectHorseBatteryStaple".toCharArray()
        assertTrue(hasher.verifyPassword(verifyPwd, hash))
    }

    @Test
    fun `verify fails for wrong password`() {
        val hash = hasher.hashPassword("top_secret".toCharArray())
        assertFalse(hasher.verifyPassword("not_it".toCharArray(), hash))
    }

    @Test
    fun `handles empty and long passwords`() {
        val empty = CharArray(0)
        val hashEmpty = hasher.hashPassword(empty)
        assertTrue(hasher.verifyPassword(CharArray(0), hashEmpty))

        val longPwd = ("a".repeat(1000)).toCharArray()
        val hashLong = hasher.hashPassword(longPwd)
        assertTrue(hasher.verifyPassword(("a".repeat(1000)).toCharArray(), hashLong))
    }
}
