package com.trustvault.android.security

import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode

/**
 * Production Argon2 engine implementation, loaded reflectively by PasswordHasher.
 *
 * Uses Argon2id with OWASP 2025 recommended parameters:
 * - Mode: Argon2id (hybrid mode, resistant to both side-channel and GPU attacks)
 * - Memory: 64 MB (65536 KiB)
 * - Iterations: 3
 * - Parallelism: 4 threads
 *
 * OWASP Reference:
 * https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html
 */
class PasswordHasherRealEngine : PasswordHasher.Engine {
    private val argon2Kt = Argon2Kt()

    override fun hash(
        password: ByteArray,
        salt: ByteArray,
        tCostInIterations: Int,
        mCostInKibibyte: Int,
        parallelism: Int
    ): String {
        val hash = argon2Kt.hash(
            mode = Argon2Mode.ARGON2_ID,
            password = password,
            salt = salt,
            tCostInIterations = tCostInIterations,
            mCostInKibibyte = mCostInKibibyte,
            parallelism = parallelism
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

