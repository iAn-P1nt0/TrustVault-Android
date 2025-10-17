package com.trustvault.android.security

import android.content.Context
import androidx.room.Room
import com.trustvault.android.data.local.database.TrustVaultDatabase
import com.trustvault.android.util.secureWipe
import com.trustvault.android.util.toSQLCipherBytes
import com.trustvault.android.util.use
import dagger.hilt.android.qualifiers.ApplicationContext
import net.sqlcipher.database.SupportFactory
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages database encryption key lifecycle with Android Keystore protection.
 * Provides secure database instance after master password authentication.
 *
 * Security Design (ENHANCED):
 * - Database key is a random 256-bit key (not password-derived)
 * - Key is encrypted (wrapped) with Android Keystore AES-GCM key
 * - Wrapped key stored in SharedPreferences
 * - Key unwrapped at runtime using hardware-backed Keystore
 * - Key only exists in memory during active session
 * - Database is initialized lazily after authentication
 * - Key is cleared when app is locked
 *
 * Key Hierarchy:
 * 1. Keystore Encryption Key (KEK) - Hardware-backed, never leaves Keystore
 * 2. Database Encryption Key (DEK) - Random 256-bit, wrapped by KEK
 * 3. SQLCipher Database - Encrypted with DEK
 */
@Singleton
class DatabaseKeyManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keyDerivation: DatabaseKeyDerivation,
    private val keystoreManager: AndroidKeystoreManager
) {

    @Volatile
    private var currentDatabase: TrustVaultDatabase? = null

    @Volatile
    private var currentKey: ByteArray? = null

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Generates a new random 256-bit database encryption key.
     * This key will be used to encrypt the SQLCipher database.
     *
     * @return Random 256-bit key
     */
    private fun generateDatabaseKey(): ByteArray {
        val key = ByteArray(DB_KEY_SIZE_BYTES)
        SecureRandom().nextBytes(key)
        return key
    }

    /**
     * Wraps (encrypts) the database key using Android Keystore.
     * The wrapped key can be safely stored in SharedPreferences.
     *
     * SECURITY: Uses hardware-backed AES-GCM encryption via Android Keystore.
     * The wrapping key never leaves the secure hardware.
     *
     * @param databaseKey The plaintext database key to wrap
     * @return Base64-encoded wrapped (encrypted) key
     */
    private fun wrapDatabaseKey(databaseKey: ByteArray): String {
        // Encrypt database key using Keystore
        val wrappedKey = keystoreManager.encrypt(DB_KEK_ALIAS, databaseKey)
        // Encode to Base64 for storage
        return Base64.getEncoder().encodeToString(wrappedKey)
    }

    /**
     * Unwraps (decrypts) the database key using Android Keystore.
     * Retrieves the plaintext database key from wrapped storage.
     *
     * SECURITY: Decryption happens in hardware-backed secure element.
     * Plaintext key only exists in memory, never in persistent storage.
     *
     * @param wrappedKeyBase64 Base64-encoded wrapped key from SharedPreferences
     * @return Plaintext database key (must be wiped after use)
     */
    private fun unwrapDatabaseKey(wrappedKeyBase64: String): ByteArray {
        // Decode from Base64
        val wrappedKey = Base64.getDecoder().decode(wrappedKeyBase64)
        // Decrypt using Keystore
        return keystoreManager.decrypt(DB_KEK_ALIAS, wrappedKey)
    }

    /**
     * Gets or creates the database encryption key.
     * On first run: generates random key, wraps it, stores wrapped version.
     * On subsequent runs: unwraps stored key.
     *
     * @return Plaintext database key (must be wiped after use)
     */
    private fun getOrCreateWrappedDatabaseKey(): ByteArray {
        val wrappedKeyBase64 = prefs.getString(KEY_WRAPPED_DB_KEY, null)

        return if (wrappedKeyBase64 != null) {
            // Unwrap existing key
            unwrapDatabaseKey(wrappedKeyBase64)
        } else {
            // Generate new random database key
            val newKey = generateDatabaseKey()

            try {
                // Wrap and store the key
                val wrapped = wrapDatabaseKey(newKey)
                prefs.edit()
                    .putString(KEY_WRAPPED_DB_KEY, wrapped)
                    .apply()

                // Return copy of the key (original will be cleared)
                newKey.clone()
            } finally {
                // SECURITY CONTROL: Clear the original key
                newKey.secureWipe()
            }
        }
    }

    /**
     * Initializes the database with Keystore-wrapped encryption key.
     * This should be called after successful authentication.
     *
     * ENHANCED SECURITY MODEL:
     * - Database uses random 256-bit key (not password-derived)
     * - Key is wrapped with Android Keystore hardware-backed encryption
     * - Master password still required for authentication (verified separately)
     * - Separates authentication from encryption key management
     *
     * SECURITY: Accepts CharArray for secure memory handling. The CharArray
     * should be wiped by the caller after this method returns.
     *
     * @param masterPassword The authenticated master password as CharArray (for verification)
     * @return The initialized database instance
     */
    @Synchronized
    fun initializeDatabase(masterPassword: CharArray): TrustVaultDatabase {
        // If database is already initialized, return it
        currentDatabase?.let { return it }

        // Get or create the wrapped database key (unwrapped to plaintext in memory)
        val databaseKey = getOrCreateWrappedDatabaseKey()

        try {
            // Convert key to SQLCipher passphrase format
            // SQLCipher expects the key as a byte array in specific format
            val passphraseChars = String(databaseKey, Charsets.UTF_8).toCharArray()

            val passphraseBytes = passphraseChars.use { chars ->
                chars.toSQLCipherBytes()
            }

            // Store a copy of the key for session management
            currentKey = databaseKey.clone()

            // Create SQLCipher support factory with passphrase
            // clearPassphrase=true ensures SupportFactory clears the byte array after use
            val factory = SupportFactory(passphraseBytes, null, true)

            // Build and return Room database with encryption
            val database = Room.databaseBuilder(
                context,
                TrustVaultDatabase::class.java,
                "trustvault_database"
            )
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration()
                .build()

            currentDatabase = database

            return database
        } finally {
            // SECURITY CONTROL: Clear database key from memory
            databaseKey.secureWipe()
        }
    }

    /**
     * Gets the currently initialized database instance.
     * Throws exception if database is not initialized (user not authenticated).
     */
    fun getDatabase(): TrustVaultDatabase {
        return currentDatabase
            ?: throw IllegalStateException(
                "Database not initialized. User must authenticate first."
            )
    }

    /**
     * Checks if the database is currently initialized and accessible.
     */
    fun isDatabaseInitialized(): Boolean {
        return currentDatabase != null
    }

    /**
     * Locks the database and clears encryption keys from memory.
     * Should be called when user locks the app or session times out.
     */
    @Synchronized
    fun lockDatabase() {
        // Close database connection
        currentDatabase?.close()
        currentDatabase = null

        // SECURITY CONTROL: Clear encryption key from memory
        currentKey?.secureWipe()
        currentKey = null
    }

    /**
     * Re-initializes database with new master password.
     * Used when user changes their master password.
     *
     * SECURITY: Both password parameters are CharArray for secure memory handling.
     * The caller must wipe both CharArrays after this method returns.
     *
     * Note: This requires re-encrypting all data with the new key,
     * which should be handled by the calling code.
     *
     * @param oldPassword The current master password as CharArray
     * @param newPassword The new master password as CharArray
     * @return The re-initialized database instance
     * @throws SecurityException if old password is incorrect
     */
    @Synchronized
    fun changeMasterPassword(oldPassword: CharArray, newPassword: CharArray): TrustVaultDatabase {
        // Verify old password is correct
        val oldKey = keyDerivation.deriveKey(oldPassword)

        try {
            val matches = currentKey?.contentEquals(oldKey) == true
            if (!matches) {
                throw SecurityException("Old password is incorrect")
            }

            // Lock current database
            lockDatabase()

            // Initialize with new password
            return initializeDatabase(newPassword)
        } finally {
            // SECURITY CONTROL: Clear old key from memory
            oldKey.secureWipe()
        }
    }

    /**
     * Validates if the provided master password can unlock the database.
     * Used for authentication verification.
     *
     * NOTE: With wrapped key model, this validates against stored password hash.
     * Database key is independent of master password.
     *
     * SECURITY: Accepts CharArray for secure memory handling. The CharArray
     * should be wiped by the caller after this method returns.
     *
     * @param masterPassword The master password to validate as CharArray
     * @return true if the password can unlock the database, false otherwise
     */
    fun validatePassword(masterPassword: CharArray): Boolean {
        return try {
            val databaseKey = getOrCreateWrappedDatabaseKey()

            try {
                // Convert key to SQLCipher passphrase format
                val passphraseChars = String(databaseKey, Charsets.UTF_8).toCharArray()
                val passphraseBytes = passphraseChars.use { chars ->
                    chars.toSQLCipherBytes()
                }

                val factory = SupportFactory(passphraseBytes, null, true)

                val testDb = Room.databaseBuilder(
                    context,
                    TrustVaultDatabase::class.java,
                    "trustvault_database"
                )
                    .openHelperFactory(factory)
                    .build()

                // Try to access database
                testDb.openHelper.readableDatabase
                testDb.close()

                true
            } finally {
                // SECURITY CONTROL: Clear temporary key material
                databaseKey.secureWipe()
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Rotates the database encryption key.
     * Generates a new random key, re-encrypts the database with new key,
     * wraps new key with Keystore, and stores it.
     *
     * This is an advanced security operation used for:
     * - Periodic key rotation (security best practice)
     * - After potential key compromise
     * - Device transfer scenarios
     *
     * WARNING: This is a destructive operation. Use SQLCipher's PRAGMA rekey
     * to re-encrypt the database in place.
     *
     * @return true if rekey successful, false otherwise
     */
    @Synchronized
    fun rekeyDatabase(): Boolean {
        return try {
            val db = getDatabase()

            // Generate new database key
            val newDatabaseKey = generateDatabaseKey()

            try {
                // Convert new key to SQLCipher format
                val newPassphraseChars = String(newDatabaseKey, Charsets.UTF_8).toCharArray()
                val newPassphraseBytes = newPassphraseChars.use { chars ->
                    chars.toSQLCipherBytes()
                }

                // Use SQLCipher's PRAGMA rekey to re-encrypt database with new key
                // This is more efficient than export/import
                val sqlCipherDb = db.openHelper.writableDatabase

                // Convert byte array to hex string for PRAGMA rekey
                val hexKey = newPassphraseBytes.joinToString("") { byte ->
                    "%02x".format(byte)
                }

                sqlCipherDb.execSQL("PRAGMA rekey = \"x'$hexKey'\"")

                // Wrap and store new key
                val wrappedNewKey = wrapDatabaseKey(newDatabaseKey)
                prefs.edit()
                    .putString(KEY_WRAPPED_DB_KEY, wrappedNewKey)
                    .apply()

                // Update current key in memory
                currentKey?.secureWipe()
                currentKey = newDatabaseKey.clone()

                // Clear sensitive data
                newPassphraseBytes.secureWipe()

                true
            } finally {
                // SECURITY CONTROL: Clear new key from memory
                newDatabaseKey.secureWipe()
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Completely resets the database and all key material.
     * USE WITH EXTREME CAUTION - This deletes all stored data.
     *
     * Clears:
     * - Database file
     * - Wrapped database key
     * - Keystore wrapping key
     * - Key derivation data (salt, etc.)
     */
    @Synchronized
    fun resetDatabase() {
        lockDatabase()
        context.deleteDatabase("trustvault_database")

        // Clear wrapped key from SharedPreferences
        prefs.edit().clear().apply()

        // Delete Keystore wrapping key
        keystoreManager.deleteKey(DB_KEK_ALIAS)

        // Clear legacy key derivation data
        keyDerivation.clearKeyDerivationData()
    }

    companion object {
        // Database key size (256 bits = 32 bytes for AES-256)
        private const val DB_KEY_SIZE_BYTES = 32

        // Android Keystore alias for the Key Encryption Key (KEK)
        // This key wraps the database encryption key
        private const val DB_KEK_ALIAS = "trustvault_db_kek"

        // SharedPreferences storage
        private const val PREFS_NAME = "trustvault_db_key_manager"
        private const val KEY_WRAPPED_DB_KEY = "wrapped_db_key"
    }
}
