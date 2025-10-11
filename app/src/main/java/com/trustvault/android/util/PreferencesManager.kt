package com.trustvault.android.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Manages app preferences using DataStore.
 * Stores non-sensitive configuration data.
 */
class PreferencesManager(private val context: Context) {

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "trustvault_preferences")

    val isMasterPasswordSet: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[MASTER_PASSWORD_SET] ?: false
        }

    val masterPasswordHash: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[MASTER_PASSWORD_HASH]
        }

    val isBiometricEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[BIOMETRIC_ENABLED] ?: false
        }

    suspend fun setMasterPasswordHash(hash: String) {
        context.dataStore.edit { preferences ->
            preferences[MASTER_PASSWORD_SET] = true
            preferences[MASTER_PASSWORD_HASH] = hash
        }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[BIOMETRIC_ENABLED] = enabled
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    companion object {
        private val MASTER_PASSWORD_SET = booleanPreferencesKey("master_password_set")
        private val MASTER_PASSWORD_HASH = stringPreferencesKey("master_password_hash")
        private val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
    }
}
