package com.trustvault.android.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.trustvault.android.data.local.dao.CredentialDao
import com.trustvault.android.data.local.entity.CredentialEntity

/**
 * Room database for TrustVault.
 * Encrypted using SQLCipher.
 *
 * Version History:
 * - v1: Initial schema (title, username, password, website, notes, category, timestamps)
 * - v2: Added packageName field for Android AutofillService support
 */
@Database(
    entities = [CredentialEntity::class],
    version = 2,
    exportSchema = false  // SECURITY: Don't export schema in production
)
abstract class TrustVaultDatabase : RoomDatabase() {
    abstract fun credentialDao(): CredentialDao
}
