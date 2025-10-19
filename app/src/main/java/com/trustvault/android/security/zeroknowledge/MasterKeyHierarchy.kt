package com.trustvault.android.security.zeroknowledge

import android.util.Log
import com.trustvault.android.security.CryptoManager
import com.trustvault.android.security.DatabaseKeyDerivation
import com.trustvault.android.util.secureWipe
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MasterKeyHierarchy - Hierarchical Key Derivation for Zero-Knowledge Architecture
 *
 * Implements a secure key hierarchy where all keys are derived from the master password.
 * The server NEVER has access to any keys in this hierarchy.
 *
 * **Key Hierarchy:**
 * ```
 * Master Password (user-controlled, never stored)
 *        ↓
 * Master Encryption Key (MEK) ← PBKDF2 600K iterations
 *        ├→ Database Encryption Key (DEK)     [SQLCipher]
 *        ├→ Field Encryption Key (FEK)        [AES-256-GCM]
 *        ├→ Backup Encryption Key (BEK)       [Export/Backup]
 *        ├→ Sync Encryption Key (SEK)         [Future: Cloud Sync]
 *        └→ Sharing Encryption Key (ShEK)     [Future: Secure Sharing]
 * ```
 *
 * **Zero-Knowledge Principles:**
 * 1. All keys derived from master password (user-controlled)
 * 2. Keys exist only in memory during active session
 * 3. Keys cleared from memory on lock/logout
 * 4. No key material ever transmitted to server
 * 5. Each key purpose-specific (principle of least privilege)
 *
 * **Security Standards:**
 * - OWASP Password Storage Cheat Sheet 2025
 * - NIST SP 800-108 - Key Derivation Using Pseudorandom Functions
 * - NIST SP 800-132 - Password-Based Key Derivation
 * - Digital Personal Data Protection Act 2023 (India)
 *
 * @property databaseKeyDerivation PBKDF2 key derivation (600K iterations)
 * @property cryptoManager Cryptographic operations manager
 */
@Singleton
class MasterKeyHierarchy @Inject constructor(
    private val databaseKeyDerivation: DatabaseKeyDerivation,
    private val cryptoManager: CryptoManager
) {

    companion object {
        private const val TAG = "MasterKeyHierarchy"

        // Key derivation contexts (NIST SP 800-108 domain separation)
        private const val CONTEXT_DATABASE = "TRUSTVAULT_DATABASE_KEY"
        private const val CONTEXT_FIELD = "TRUSTVAULT_FIELD_KEY"
        private const val CONTEXT_BACKUP = "TRUSTVAULT_BACKUP_KEY"
        private const val CONTEXT_SYNC = "TRUSTVAULT_SYNC_KEY"
        private const val CONTEXT_SHARING = "TRUSTVAULT_SHARING_KEY"
        private const val CONTEXT_EXPORT = "TRUSTVAULT_EXPORT_KEY"

        // Key sizes (all 256-bit)
        private const val KEY_SIZE_BYTES = 32
    }

    /**
     * Key purpose enumeration for domain separation.
     */
    enum class KeyPurpose {
        /** Database encryption (SQLCipher) */
        DATABASE,

        /** Field-level encryption (credentials) */
        FIELD_ENCRYPTION,

        /** Backup encryption */
        BACKUP,

        /** Cloud sync encryption (future) */
        SYNC,

        /** Secure sharing (future) */
        SHARING,

        /** Export encryption */
        EXPORT
    }

    /**
     * Derived key container with secure memory management.
     *
     * **SECURITY CRITICAL:**
     * - ALWAYS call secureWipe() after use
     * - Never log or transmit key material
     * - Keep in memory for minimal time
     *
     * @property purpose Key purpose
     * @property keyMaterial Encryption key (MUST be wiped after use)
     * @property context Derivation context (public)
     */
    data class DerivedKey(
        val purpose: KeyPurpose,
        val keyMaterial: ByteArray,
        val context: String
    ) {
        /**
         * Securely wipes key material from memory.
         * SECURITY CONTROL: Call this when key is no longer needed.
         */
        fun secureWipe() {
            keyMaterial.secureWipe()
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as DerivedKey
            if (purpose != other.purpose) return false
            if (!keyMaterial.contentEquals(other.keyMaterial)) return false
            if (context != other.context) return false
            return true
        }

        override fun hashCode(): Int {
            var result = purpose.hashCode()
            result = 31 * result + keyMaterial.contentHashCode()
            result = 31 * result + context.hashCode()
            return result
        }
    }

    // ========================================================================
    // HIERARCHICAL KEY DERIVATION
    // ========================================================================

    /**
     * Derives Master Encryption Key (MEK) from master password.
     *
     * **Security Properties:**
     * - PBKDF2-HMAC-SHA256 with 600,000 iterations (OWASP 2025)
     * - Device-specific salt binding
     * - 256-bit output key
     * - Constant-time operations
     *
     * **Zero-Knowledge:**
     * - Derivation happens entirely on client
     * - Server never sees master password or MEK
     * - MEK never stored, only exists in memory
     *
     * @param masterPassword Master password
     * @return Master Encryption Key (MUST be wiped after use)
     */
    fun deriveMasterEncryptionKey(masterPassword: CharArray): ByteArray {
        require(masterPassword.isNotEmpty()) { "Master password required" }

        return try {
            Log.d(TAG, "Deriving Master Encryption Key (MEK)")

            // Use existing PBKDF2 implementation (600K iterations)
            val mek = databaseKeyDerivation.deriveKey(masterPassword)

            if (mek.size != KEY_SIZE_BYTES) {
                throw IllegalStateException("Invalid MEK size: ${mek.size} (expected $KEY_SIZE_BYTES)")
            }

            Log.d(TAG, "MEK derived successfully (${mek.size} bytes)")
            mek

        } catch (e: Exception) {
            Log.e(TAG, "MEK derivation failed: ${e.message}")
            throw e
        }
    }

    /**
     * Derives purpose-specific key from Master Encryption Key.
     *
     * **NIST SP 800-108 KDF in Counter Mode:**
     * ```
     * DerivedKey = HMAC-SHA256(MEK, counter || context || 0x00 || keyLength)
     * ```
     *
     * **Domain Separation:**
     * - Each purpose has unique context string
     * - Prevents key reuse across different purposes
     * - Follows principle of least privilege
     *
     * @param mek Master Encryption Key
     * @param purpose Key purpose for domain separation
     * @return Purpose-specific derived key
     */
    fun deriveKey(mek: ByteArray, purpose: KeyPurpose): DerivedKey {
        require(mek.size == KEY_SIZE_BYTES) { "Invalid MEK size" }

        return try {
            val context = getContextString(purpose)
            Log.d(TAG, "Deriving key for purpose: $purpose")

            // NIST SP 800-108: KDF in Counter Mode
            val derivedKeyMaterial = deriveKeyUsingHMAC(mek, context)

            if (derivedKeyMaterial.size != KEY_SIZE_BYTES) {
                throw IllegalStateException("Invalid derived key size: ${derivedKeyMaterial.size}")
            }

            DerivedKey(
                purpose = purpose,
                keyMaterial = derivedKeyMaterial,
                context = context
            )

        } catch (e: Exception) {
            Log.e(TAG, "Key derivation failed for $purpose: ${e.message}")
            throw e
        }
    }

    /**
     * Derives all keys in the hierarchy from master password.
     *
     * **Use Case:**
     * - Session initialization
     * - After successful authentication
     * - Before database operations
     *
     * **Returns:**
     * Map of KeyPurpose to DerivedKey
     * SECURITY: Caller MUST wipe all keys after use
     *
     * @param masterPassword Master password
     * @return Map of all derived keys
     */
    fun deriveAllKeys(masterPassword: CharArray): Map<KeyPurpose, DerivedKey> {
        val mek = deriveMasterEncryptionKey(masterPassword)

        return try {
            val keys = mutableMapOf<KeyPurpose, DerivedKey>()

            // Derive all purpose-specific keys
            KeyPurpose.values().forEach { purpose ->
                keys[purpose] = deriveKey(mek, purpose)
            }

            Log.d(TAG, "All keys derived successfully (${keys.size} keys)")
            keys

        } finally {
            // SECURITY: Wipe MEK from memory
            mek.secureWipe()
        }
    }

    // ========================================================================
    // CONVENIENCE METHODS
    // ========================================================================

    /**
     * Derives database encryption key from master password.
     *
     * @param masterPassword Master password
     * @return Database Encryption Key (for SQLCipher)
     */
    fun deriveDatabaseKey(masterPassword: CharArray): ByteArray {
        val mek = deriveMasterEncryptionKey(masterPassword)
        return try {
            val dek = deriveKey(mek, KeyPurpose.DATABASE)
            dek.keyMaterial.copyOf()  // Return copy, original will be wiped
        } finally {
            mek.secureWipe()
        }
    }

    /**
     * Derives backup encryption key from master password.
     *
     * @param masterPassword Master password
     * @return Backup Encryption Key
     */
    fun deriveBackupKey(masterPassword: CharArray): ByteArray {
        val mek = deriveMasterEncryptionKey(masterPassword)
        return try {
            val bek = deriveKey(mek, KeyPurpose.BACKUP)
            bek.keyMaterial.copyOf()
        } finally {
            mek.secureWipe()
        }
    }

    /**
     * Derives export encryption key from master password.
     *
     * @param masterPassword Master password
     * @return Export Encryption Key
     */
    fun deriveExportKey(masterPassword: CharArray): ByteArray {
        val mek = deriveMasterEncryptionKey(masterPassword)
        return try {
            val eek = deriveKey(mek, KeyPurpose.EXPORT)
            eek.keyMaterial.copyOf()
        } finally {
            mek.secureWipe()
        }
    }

    // ========================================================================
    // KEY DERIVATION IMPLEMENTATION (NIST SP 800-108)
    // ========================================================================

    /**
     * Derives key using HMAC-SHA256 (NIST SP 800-108 KDF in Counter Mode).
     *
     * **Algorithm:**
     * ```
     * K_i = HMAC(K_in, [i]_2 || Label || 0x00 || Context || [L]_2)
     * ```
     *
     * Where:
     * - K_in: Input keying material (MEK)
     * - [i]_2: Counter (32-bit, big-endian)
     * - Label: Purpose identifier
     * - 0x00: Separator byte
     * - Context: Domain separation string
     * - [L]_2: Output length in bits (32-bit, big-endian)
     *
     * @param keyMaterial Input keying material (MEK)
     * @param context Domain separation context
     * @return Derived key material
     */
    private fun deriveKeyUsingHMAC(keyMaterial: ByteArray, context: String): ByteArray {
        return try {
            val mac = javax.crypto.Mac.getInstance("HmacSHA256")
            val keySpec = javax.crypto.spec.SecretKeySpec(keyMaterial, "HmacSHA256")
            mac.init(keySpec)

            // Construct NIST SP 800-108 input
            // [i]_2 || Label || 0x00 || Context || [L]_2
            val counter = ByteArray(4).apply {
                // Counter = 1 (big-endian)
                this[3] = 0x01
            }
            val separator = byteArrayOf(0x00)
            val outputLength = ByteArray(4).apply {
                // 256 bits = 0x00000100 (big-endian)
                this[2] = 0x01
            }

            // Update HMAC with all components
            mac.update(counter)
            mac.update(context.toByteArray())
            mac.update(separator)
            mac.update(outputLength)

            // Generate derived key
            val derivedKey = mac.doFinal()

            if (derivedKey.size != KEY_SIZE_BYTES) {
                throw IllegalStateException("HMAC output size mismatch: ${derivedKey.size}")
            }

            derivedKey

        } catch (e: Exception) {
            Log.e(TAG, "HMAC-based key derivation failed: ${e.message}")
            throw e
        }
    }

    /**
     * Gets context string for key purpose.
     *
     * @param purpose Key purpose
     * @return Domain separation context string
     */
    private fun getContextString(purpose: KeyPurpose): String {
        return when (purpose) {
            KeyPurpose.DATABASE -> CONTEXT_DATABASE
            KeyPurpose.FIELD_ENCRYPTION -> CONTEXT_FIELD
            KeyPurpose.BACKUP -> CONTEXT_BACKUP
            KeyPurpose.SYNC -> CONTEXT_SYNC
            KeyPurpose.SHARING -> CONTEXT_SHARING
            KeyPurpose.EXPORT -> CONTEXT_EXPORT
        }
    }

    // ========================================================================
    // KEY ROTATION (Future Enhancement)
    // ========================================================================

    /**
     * Rotates all derived keys by re-deriving from new master password.
     *
     * **Use Case:**
     * - Master password change
     * - Periodic key rotation policy
     * - Security incident response
     *
     * **Process:**
     * 1. Decrypt all data with old keys
     * 2. Derive new keys from new master password
     * 3. Re-encrypt all data with new keys
     * 4. Securely wipe old keys
     *
     * **WARNING:** This is a future enhancement. Current implementation
     * does not support key rotation without re-encryption of all data.
     *
     * @param oldMasterPassword Current master password
     * @param newMasterPassword New master password
     * @return Success/failure result
     */
    fun rotateKeys(
        oldMasterPassword: CharArray,
        newMasterPassword: CharArray
    ): Result<Unit> {
        // TODO: Implement full key rotation
        // This requires re-encrypting the entire database
        Log.w(TAG, "Key rotation not yet implemented - requires database re-encryption")
        return Result.failure(UnsupportedOperationException("Key rotation not yet implemented"))
    }
}
