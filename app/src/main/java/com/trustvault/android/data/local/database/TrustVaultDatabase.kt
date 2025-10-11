package com.trustvault.android.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.trustvault.android.data.local.dao.CredentialDao
import com.trustvault.android.data.local.entity.CredentialEntity

/**
 * Room database for TrustVault.
 * Encrypted using SQLCipher.
 */
@Database(
    entities = [CredentialEntity::class],
    version = 1,
    exportSchema = true
)
abstract class TrustVaultDatabase : RoomDatabase() {
    abstract fun credentialDao(): CredentialDao
}
