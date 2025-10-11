package com.trustvault.android.di

import android.content.Context
import androidx.room.Room
import com.trustvault.android.data.local.dao.CredentialDao
import com.trustvault.android.data.local.database.TrustVaultDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import javax.inject.Singleton

/**
 * Hilt module providing database dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideTrustVaultDatabase(
        @ApplicationContext context: Context
    ): TrustVaultDatabase {
        // Generate a secure passphrase for SQLCipher
        // In production, this should be derived from the master password
        val passphrase = SQLiteDatabase.getBytes("trustvault_db_key_v1".toCharArray())
        val factory = SupportFactory(passphrase)

        return Room.databaseBuilder(
            context,
            TrustVaultDatabase::class.java,
            "trustvault_database"
        )
            .openHelperFactory(factory)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideCredentialDao(database: TrustVaultDatabase): CredentialDao {
        return database.credentialDao()
    }
}
