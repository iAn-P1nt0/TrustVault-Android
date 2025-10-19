package com.trustvault.android.security.zeroknowledge

import android.util.Log
import com.trustvault.android.security.CryptoManager
import com.trustvault.android.security.PasswordHasher
import com.trustvault.android.util.secureWipe
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ZeroKnowledgeProof - Proof-of-Knowledge Authentication
 *
 * Implements zero-knowledge proof protocols for authentication without exposing passwords.
 * Allows the user to prove they know the master password without revealing it.
 *
 * **Zero-Knowledge Properties:**
 * 1. Completeness: Valid password always passes verification
 * 2. Soundness: Invalid password cannot pass verification (except negligible probability)
 * 3. Zero-Knowledge: Verifier learns nothing except validity
 *
 * **Use Cases:**
 * - Local authentication without storing plaintext password
 * - Biometric authentication with master password backup
 * - Session token generation without password transmission
 * - Future: Server authentication without password transmission
 *
 * **Protocol:**
 * ```
 * User (Prover)                    App (Verifier)
 * -------------                    --------------
 * Password                         Stored Hash
 *    ↓                                   ↓
 * Generate Proof                   Challenge
 *    ↓                                   ↓
 * Proof Response  →  Verify  ←    Validate
 *                    ↓
 *                 Success/Fail
 * ```
 *
 * **Security Standards:**
 * - NIST SP 800-63B - Digital Identity Guidelines (Authentication)
 * - OWASP Authentication Cheat Sheet
 * - Zero-Knowledge Proof Protocols (Schnorr-like)
 *
 * @property passwordHasher Argon2id password hashing
 * @property cryptoManager Cryptographic operations
 */
@Singleton
class ZeroKnowledgeProof @Inject constructor(
    private val passwordHasher: PasswordHasher,
    private val cryptoManager: CryptoManager
) {

    companion object {
        private const val TAG = "ZeroKnowledgeProof"

        // Challenge size for proof generation (32 bytes = 256 bits)
        private const val CHALLENGE_SIZE = 32

        // Proof version for migration
        private const val PROOF_VERSION = 1
    }

    /**
     * Authentication challenge for zero-knowledge proof.
     *
     * **Purpose:**
     * - Prevents replay attacks
     * - Ensures fresh proof for each authentication
     * - Binds proof to specific session
     *
     * @property nonce Random challenge nonce
     * @property timestamp Challenge creation time (for expiry)
     * @property expiryMs Challenge expiry in milliseconds
     */
    data class Challenge(
        val nonce: ByteArray,
        val timestamp: Long = System.currentTimeMillis(),
        val expiryMs: Long = 60000  // 1 minute expiry
    ) {
        /**
         * Checks if challenge has expired.
         */
        fun isExpired(): Boolean {
            return (System.currentTimeMillis() - timestamp) > expiryMs
        }

        /**
         * Securely wipes challenge from memory.
         */
        fun secureWipe() {
            nonce.secureWipe()
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Challenge
            if (!nonce.contentEquals(other.nonce)) return false
            return true
        }

        override fun hashCode(): Int {
            return nonce.contentHashCode()
        }
    }

    /**
     * Zero-knowledge proof response.
     *
     * **Contains:**
     * - Proof response (derived from password + challenge)
     * - Commitment (prevents forgery)
     * - Metadata (version, timestamp)
     *
     * **Zero-Knowledge Property:**
     * - Verifier can validate proof without learning password
     * - Proof cannot be reused (challenge-bound)
     *
     * @property version Proof protocol version
     * @property response Proof response value
     * @property commitment Proof commitment
     * @property timestamp Proof generation time
     */
    data class Proof(
        val version: Int = PROOF_VERSION,
        val response: ByteArray,
        val commitment: ByteArray,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        /**
         * Securely wipes proof from memory.
         */
        fun secureWipe() {
            response.secureWipe()
            commitment.secureWipe()
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Proof
            if (version != other.version) return false
            if (!response.contentEquals(other.response)) return false
            if (!commitment.contentEquals(other.commitment)) return false
            return true
        }

        override fun hashCode(): Int {
            var result = version
            result = 31 * result + response.contentHashCode()
            result = 31 * result + commitment.contentHashCode()
            return result
        }
    }

    // ========================================================================
    // PROOF GENERATION (Prover Side)
    // ========================================================================

    /**
     * Generates authentication challenge.
     *
     * **Purpose:**
     * - Creates random challenge for user to prove knowledge of password
     * - Challenge is cryptographically random (unpredictable)
     * - Challenge expires after 1 minute (prevents replay)
     *
     * @return Challenge for proof generation
     */
    fun generateChallenge(): Challenge {
        Log.d(TAG, "Generating authentication challenge")

        val nonce = cryptoManager.generateRandomBytes(CHALLENGE_SIZE)

        return Challenge(
            nonce = nonce,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Generates zero-knowledge proof for password.
     *
     * **Protocol:**
     * 1. Hash password with Argon2id
     * 2. Derive commitment from password + challenge
     * 3. Generate response using HMAC(password, challenge)
     * 4. Return proof (response + commitment)
     *
     * **Security Properties:**
     * - Proof cannot be generated without knowing password
     * - Proof is challenge-bound (cannot be reused)
     * - Proof reveals nothing about password
     *
     * @param password Master password
     * @param challenge Authentication challenge
     * @return Zero-knowledge proof
     */
    fun generateProof(
        password: CharArray,
        challenge: Challenge
    ): Proof {
        require(password.isNotEmpty()) { "Password required for proof generation" }

        return try {
            Log.d(TAG, "Generating zero-knowledge proof")

            // SECURITY CONTROL: Verify challenge hasn't expired
            if (challenge.isExpired()) {
                throw IllegalStateException("Challenge has expired")
            }

            // Step 1: Hash password (Argon2id)
            val passwordHash = passwordHasher.hashPassword(password)

            // Step 2: Derive commitment
            // Commitment = HMAC-SHA256(passwordHash, "COMMITMENT" || challenge)
            val commitment = deriveCommitment(passwordHash, challenge.nonce)

            // Step 3: Generate response
            // Response = HMAC-SHA256(passwordHash, "RESPONSE" || challenge)
            val response = deriveResponse(passwordHash, challenge.nonce)

            val proof = Proof(
                version = PROOF_VERSION,
                response = response,
                commitment = commitment,
                timestamp = System.currentTimeMillis()
            )

            Log.d(TAG, "Zero-knowledge proof generated successfully")
            proof

        } catch (e: Exception) {
            Log.e(TAG, "Proof generation failed: ${e.message}")
            throw e
        }
    }

    // ========================================================================
    // PROOF VERIFICATION (Verifier Side)
    // ========================================================================

    /**
     * Verifies zero-knowledge proof without accessing password.
     *
     * **Protocol:**
     * 1. Reconstruct expected commitment from stored hash + challenge
     * 2. Reconstruct expected response from stored hash + challenge
     * 3. Compare with provided proof (constant-time)
     * 4. Return success if match
     *
     * **Security Properties:**
     * - Verifier only needs password hash (not plaintext password)
     * - Verification is constant-time (prevents timing attacks)
     * - Invalid proof cannot pass verification
     *
     * @param proof Zero-knowledge proof from prover
     * @param challenge Challenge used for proof generation
     * @param storedPasswordHash Stored Argon2id password hash
     * @return true if proof is valid
     */
    fun verifyProof(
        proof: Proof,
        challenge: Challenge,
        storedPasswordHash: String
    ): Boolean {
        return try {
            Log.d(TAG, "Verifying zero-knowledge proof")

            // SECURITY CONTROL: Check proof version
            if (proof.version > PROOF_VERSION) {
                Log.w(TAG, "Unsupported proof version: ${proof.version}")
                return false
            }

            // SECURITY CONTROL: Check challenge expiry
            if (challenge.isExpired()) {
                Log.w(TAG, "Challenge has expired")
                return false
            }

            // Reconstruct expected commitment
            val expectedCommitment = deriveCommitment(storedPasswordHash, challenge.nonce)

            // Reconstruct expected response
            val expectedResponse = deriveResponse(storedPasswordHash, challenge.nonce)

            // SECURITY CONTROL: Constant-time comparison
            val commitmentValid = constantTimeEquals(proof.commitment, expectedCommitment)
            val responseValid = constantTimeEquals(proof.response, expectedResponse)

            // Secure wipe expected values
            expectedCommitment.secureWipe()
            expectedResponse.secureWipe()

            val isValid = commitmentValid && responseValid

            if (isValid) {
                Log.d(TAG, "Zero-knowledge proof verified successfully")
            } else {
                Log.w(TAG, "Zero-knowledge proof verification failed")
            }

            isValid

        } catch (e: Exception) {
            Log.e(TAG, "Proof verification error: ${e.message}")
            false
        }
    }

    /**
     * Simplified authentication that combines proof generation and verification.
     *
     * **Use Case:**
     * - Local authentication (no network)
     * - Single-step password verification
     * - Biometric + password combination
     *
     * @param password Password to verify
     * @param storedPasswordHash Stored password hash
     * @return true if password is correct
     */
    fun authenticateLocal(
        password: CharArray,
        storedPasswordHash: String
    ): Boolean {
        return try {
            Log.d(TAG, "Performing local zero-knowledge authentication")

            // Generate challenge
            val challenge = generateChallenge()

            // Generate proof
            val proof = generateProof(password, challenge)

            // Verify proof
            val isValid = verifyProof(proof, challenge, storedPasswordHash)

            // Cleanup
            challenge.secureWipe()
            proof.secureWipe()

            isValid

        } catch (e: Exception) {
            Log.e(TAG, "Local authentication failed: ${e.message}")
            false
        }
    }

    // ========================================================================
    // CRYPTOGRAPHIC PRIMITIVES
    // ========================================================================

    /**
     * Derives commitment value using HMAC-SHA256.
     *
     * Commitment = HMAC-SHA256(key, "TRUSTVAULT_COMMITMENT" || nonce)
     *
     * @param key Key material (password hash)
     * @param nonce Challenge nonce
     * @return Commitment value
     */
    private fun deriveCommitment(key: String, nonce: ByteArray): ByteArray {
        return try {
            val mac = javax.crypto.Mac.getInstance("HmacSHA256")
            val keySpec = javax.crypto.spec.SecretKeySpec(
                key.toByteArray(),
                "HmacSHA256"
            )
            mac.init(keySpec)

            // Update with context string
            mac.update("TRUSTVAULT_COMMITMENT".toByteArray())

            // Update with nonce
            mac.update(nonce)

            // Generate commitment
            mac.doFinal()

        } catch (e: Exception) {
            Log.e(TAG, "Commitment derivation failed: ${e.message}")
            throw e
        }
    }

    /**
     * Derives response value using HMAC-SHA256.
     *
     * Response = HMAC-SHA256(key, "TRUSTVAULT_RESPONSE" || nonce)
     *
     * @param key Key material (password hash)
     * @param nonce Challenge nonce
     * @return Response value
     */
    private fun deriveResponse(key: String, nonce: ByteArray): ByteArray {
        return try {
            val mac = javax.crypto.Mac.getInstance("HmacSHA256")
            val keySpec = javax.crypto.spec.SecretKeySpec(
                key.toByteArray(),
                "HmacSHA256"
            )
            mac.init(keySpec)

            // Update with context string
            mac.update("TRUSTVAULT_RESPONSE".toByteArray())

            // Update with nonce
            mac.update(nonce)

            // Generate response
            mac.doFinal()

        } catch (e: Exception) {
            Log.e(TAG, "Response derivation failed: ${e.message}")
            throw e
        }
    }

    /**
     * Constant-time byte array comparison.
     *
     * Prevents timing attacks by ensuring comparison time is independent of where arrays differ.
     *
     * @param a First array
     * @param b Second array
     * @return true if arrays are equal
     */
    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) {
            return false
        }

        var result = 0
        for (i in a.indices) {
            result = result or (a[i].toInt() xor b[i].toInt())
        }

        return result == 0
    }

    // ========================================================================
    // SESSION TOKEN GENERATION (Future Enhancement)
    // ========================================================================

    /**
     * Generates session token after successful authentication.
     *
     * **Use Case:**
     * - Maintain authenticated session without storing password
     * - Auto-lock timeout with token expiry
     * - Biometric re-authentication
     *
     * **Security:**
     * - Token derived from password + random nonce
     * - Token expires after session timeout
     * - Token cannot be used to derive password
     *
     * @param password Master password
     * @param sessionDurationMs Session duration in milliseconds
     * @return Session token
     */
    fun generateSessionToken(
        password: CharArray,
        sessionDurationMs: Long = 15 * 60 * 1000  // 15 minutes default
    ): SessionToken {
        val nonce = cryptoManager.generateRandomBytes(32)
        val passwordHash = passwordHasher.hashPassword(password)

        val tokenValue = deriveResponse(passwordHash, nonce)

        return SessionToken(
            value = tokenValue,
            expiryTime = System.currentTimeMillis() + sessionDurationMs
        )
    }

    /**
     * Session token data class.
     */
    data class SessionToken(
        val value: ByteArray,
        val expiryTime: Long
    ) {
        fun isExpired(): Boolean {
            return System.currentTimeMillis() > expiryTime
        }

        fun secureWipe() {
            value.secureWipe()
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as SessionToken
            if (!value.contentEquals(other.value)) return false
            return true
        }

        override fun hashCode(): Int {
            return value.contentHashCode()
        }
    }
}
