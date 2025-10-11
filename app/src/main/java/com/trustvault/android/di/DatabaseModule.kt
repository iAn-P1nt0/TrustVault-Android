package com.trustvault.android.di

import com.trustvault.android.data.local.dao.CredentialDao
import com.trustvault.android.security.DatabaseKeyManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing database dependencies.
 *
 * SECURITY: Database encryption key is now derived from master password
 * instead of being hardcoded. The database is initialized lazily after
 * user authentication.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * Provides CredentialDao that retrieves the database from DatabaseKeyManager.
     * The database must be initialized after authentication before DAO can be used.
     */
    @Provides
    @Singleton
    fun provideCredentialDao(databaseKeyManager: DatabaseKeyManager): CredentialDao {
        return databaseKeyManager.getDatabase().credentialDao()
    }
}
