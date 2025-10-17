package com.trustvault.android.security

import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode

/**
 * Production Argon2 engine implementation, loaded reflectively by PasswordHasher.
 */
class PasswordHasherRealEngine : PasswordHasher.Engine {
    private val argon2Kt = Argon2Kt()

    override fun hash(password: ByteArray, salt: ByteArray, tCostInIterations: Int, mCostInKibibyte: Int): String {
        val hash = argon2Kt.hash(
            mode = Argon2Mode.ARGON2_ID,
            password = password,
            salt = salt,
            tCostInIterations = tCostInIterations,
            mCostInKibibyte = mCostInKibibyte
        )
        return hash.encodedOutputAsString()
    }

    override fun verify(password: ByteArray, encoded: String): Boolean {
        return argon2Kt.verify(
            mode = Argon2Mode.ARGON2_ID,
            encoded = encoded,
            password = password
        )
    }
}

