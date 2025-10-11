package com.trustvault.android.di

import android.content.Context
import com.trustvault.android.data.repository.CredentialRepositoryImpl
import com.trustvault.android.domain.repository.CredentialRepository
import com.trustvault.android.util.PreferencesManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing app-level dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindCredentialRepository(
        impl: CredentialRepositoryImpl
    ): CredentialRepository

    companion object {
        @Provides
        @Singleton
        fun providePreferencesManager(
            @ApplicationContext context: Context
        ): PreferencesManager {
            return PreferencesManager(context)
        }
    }
}
