package com.trustvault.android.credentialmanager

/**
 * Domain model for WebAuthn/passkey public key credentials.
 *
 * Represents both attestation (registration) and assertion (authentication) responses.
 *
 * **Attestation Response** (credential creation):
 * - credentialId: Unique identifier for this credential
 * - publicKey: Public key extracted from attestation object
 * - attestationObject: CBOR-encoded attestation (for server verification)
 * - clientDataJson: Client data JSON (for server validation of challenge/origin)
 *
 * **Assertion Response** (authentication):
 * - credentialId: Which credential was used for signing
 * - signature: Cryptographic proof using private key
 * - authenticatorData: Flags and counter (prevents cloning)
 * - clientDataJson: Client data JSON (for server validation)
 *
 * **Security Notes**:
 * - Public keys are extracted from attestation objects during registration
 * - Private keys NEVER leave the device's secure enclave
 * - Signatures prove private key possession without exposure
 * - Counter values prevent credential cloning attacks
 *
 * **Server Integration**:
 * Server must:
 * 1. Verify challenge matches (prevent replay attacks)
 * 2. Verify origin matches expected origin
 * 3. Verify RP ID matches
 * 4. For attestation: verify attestationObject using FIDO2 library
 * 5. For attestation: extract and store public key
 * 6. For assertion: verify signature using stored public key
 * 7. For assertion: increment and verify counter (prevent cloning)
 *
 * Reference: https://www.w3.org/TR/webauthn-3/
 *
 * @param credentialId Unique identifier for this credential (base64url encoded)
 * @param userId User identifier (email or username)
 * @param publicKey Public key for signature verification (PEM or raw bytes)
 * @param attestationObject CBOR-encoded attestation (base64url, contains public key)
 * @param clientDataJson Client data JSON (base64url encoded)
 * @param algorithm COSE algorithm ID (-7 for ES256, -257 for RS256)
 * @param attestationFormat Attestation format ("direct", "packed", "fido-u2f", etc.)
 * @param rpId Relying Party identifier (e.g., "example.com")
 * @param createdAt Timestamp of credential creation
 * @param signature Cryptographic signature (assertion response only)
 * @param authenticatorData Raw authenticator data (assertion response only)
 * @param counter Signature counter for cloning prevention (assertion response only)
 */
data class PublicKeyCredentialModel(
    val credentialId: String,
    val userId: String,
    val publicKey: String = "",
    val attestationObject: String = "",
    val clientDataJson: String,
    val algorithm: Int = 0,
    val attestationFormat: String = "",
    val rpId: String,
    val createdAt: Long = System.currentTimeMillis(),
    // Assertion response fields
    val signature: String? = null,
    val authenticatorData: String? = null,
    val counter: Long = 0L
) {
    /**
     * Checks if this is an attestation response (from registration).
     *
     * Attestation responses contain:
     * - publicKey
     * - attestationObject
     * - attestationFormat
     *
     * @return true if this is an attestation response
     */
    fun isAttestationResponse(): Boolean {
        return attestationObject.isNotEmpty() && publicKey.isNotEmpty()
    }

    /**
     * Checks if this is an assertion response (from authentication).
     *
     * Assertion responses contain:
     * - signature
     * - authenticatorData
     *
     * @return true if this is an assertion response
     */
    fun isAssertionResponse(): Boolean {
        return signature != null && authenticatorData != null
    }

    /**
     * Extracts challenge from client data JSON.
     *
     * Client data JSON format (base64url encoded):
     * ```json
     * {
     *   "type": "webauthn.create|webauthn.get",
     *   "challenge": "base64url-encoded-challenge",
     *   "origin": "https://example.com"
     * }
     * ```
     *
     * @return Base64url-encoded challenge, or null if not found
     */
    fun extractChallenge(): String? {
        return try {
            val clientDataJson = String(
                android.util.Base64.decode(
                    clientDataJson,
                    android.util.Base64.DEFAULT
                )
            )
            val pattern = """"challenge"\s*:\s*"([^"]+)"""".toRegex()
            pattern.find(clientDataJson)?.groupValues?.get(1)
        } catch (@Suppress("UNUSED_PARAMETER") e: Exception) {
            null
        }
    }

    /**
     * Extracts origin from client data JSON.
     *
     * Must match expected origin (https://example.com).
     * Prevents registration/authentication on wrong origin.
     *
     * @return Origin string, or null if not found
     */
    fun extractOrigin(): String? {
        return try {
            val clientDataJson = String(
                android.util.Base64.decode(
                    clientDataJson,
                    android.util.Base64.DEFAULT
                )
            )
            val pattern = """"origin"\s*:\s*"([^"]+)"""".toRegex()
            pattern.find(clientDataJson)?.groupValues?.get(1)
        } catch (@Suppress("UNUSED_PARAMETER") e: Exception) {
            null
        }
    }

    /**
     * Extracts type from client data JSON.
     *
     * Should be:
     * - "webauthn.create" for attestation
     * - "webauthn.get" for assertion
     *
     * @return Type string, or null if not found
     */
    fun extractClientDataType(): String? {
        return try {
            val clientDataJson = String(
                android.util.Base64.decode(
                    clientDataJson,
                    android.util.Base64.DEFAULT
                )
            )
            val pattern = """"type"\s*:\s*"([^"]+)"""".toRegex()
            pattern.find(clientDataJson)?.groupValues?.get(1)
        } catch (@Suppress("UNUSED_PARAMETER") e: Exception) {
            null
        }
    }
}