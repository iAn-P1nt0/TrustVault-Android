package com.trustvault.android.security

import android.content.Context
import androidx.room.Room
import com.trustvault.android.data.local.database.TrustVaultDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import net.sqlcipher.database.SupportFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages database encryption key lifecycle.
 * Provides secure database instance after master password authentication.
 *
 * Security Design:
 * - Database key is derived from master password (not hardcoded)
 * - Key is only kept in memory during active session
 * - Database is initialized lazily after authentication
 * - Key is cleared when app is locked
 */
@Singleton
class DatabaseKeyManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keyDerivation: DatabaseKeyDerivation
) {

    @Volatile
    private var currentDatabase: TrustVaultDatabase? = null

    @Volatile
    private var currentKey: ByteArray? = null

    /**
     * Initializes the database with a key derived from the master password.
     * This should be called after successful authentication.
     *
     * @param masterPassword The authenticated master password
     * @return The initialized database instance
     */
    @Synchronized
    fun initializeDatabase(masterPassword: String): TrustVaultDatabase {
        // If database is already initialized with this password, return it
        currentDatabase?.let { return it }

        // Derive encryption key from master password
        val key = keyDerivation.deriveKey(masterPassword)
        currentKey = key

        // Create SQLCipher support factory with derived key (ByteArray)
        val factory = SupportFactory(key)

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

        // Clear encryption key from memory
        currentKey?.fill(0)
        currentKey = null
    }

    /**
     * Re-initializes database with new master password.
     * Used when user changes their master password.
     *
     * Note: This requires re-encrypting all data with the new key,
     * which should be handled by the calling code.
     */
    @Synchronized
    fun changeMasterPassword(oldPassword: String, newPassword: String): TrustVaultDatabase {
        // Verify old password is correct
        val oldKey = keyDerivation.deriveKey(oldPassword)
        val matches = currentKey?.contentEquals(oldKey) == true
        // Clear oldKey from memory
        oldKey.fill(0)
        if (!matches) {
            throw SecurityException("Old password is incorrect")
        }

        // Lock current database
        lockDatabase()

        // Initialize with new password
        return initializeDatabase(newPassword)
    }

    /**
     * Validates if the provided master password can unlock the database.
     * Used for authentication verification.
     */
    fun validatePassword(masterPassword: String): Boolean {
        return try {
            val key = keyDerivation.deriveKey(masterPassword)

            val factory = SupportFactory(key)

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

            // Clean up temporary key material
            key.fill(0)

            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Completely resets the database and all key derivation data.
     * USE WITH EXTREME CAUTION - This deletes all stored data.
     */
    @Synchronized
    fun resetDatabase() {
        lockDatabase()
        context.deleteDatabase("trustvault_database")
        keyDerivation.clearKeyDerivationData()
    }
}
