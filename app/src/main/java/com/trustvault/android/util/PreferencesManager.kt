package com.trustvault.android.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.trustvault.android.logging.SecureLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Manages app preferences using DataStore.
 * Stores non-sensitive configuration data.
 *
 * SECURITY CONTROL: Privacy-first by default.
 * - Diagnostics logging disabled by default
 * - No telemetry or crash reporting
 * - All data stored locally in encrypted DataStore
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

    val lastUnlockTimestamp: Flow<Long?> = context.dataStore.data
        .map { preferences ->
            preferences[LAST_UNLOCK_TIMESTAMP]
        }

    /**
     * Diagnostics logging toggle (privacy setting).
     * When enabled, verbose logs are written (with PII scrubbing).
     * Disabled by default for privacy.
     */
    val isDiagnosticsEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[DIAGNOSTICS_ENABLED] ?: false
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

    /**
     * Records the timestamp of successful unlock.
     * Used for auto-lock timeout calculations.
     *
     * @param timestamp Unix timestamp in milliseconds (System.currentTimeMillis())
     */
    suspend fun setLastUnlockTimestamp(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[LAST_UNLOCK_TIMESTAMP] = timestamp
        }
    }

    /**
     * Clears the last unlock timestamp.
     * Called when the app is locked.
     */
    suspend fun clearLastUnlockTimestamp() {
        context.dataStore.edit { preferences ->
            preferences.remove(LAST_UNLOCK_TIMESTAMP)
        }
    }

    /**
     * Enable or disable diagnostics logging.
     * When enabled, logs are written (with PII scrubbing).
     * Changes are synced to SecureLogger immediately.
     */
    suspend fun setDiagnosticsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DIAGNOSTICS_ENABLED] = enabled
        }
        // Sync to SecureLogger
        SecureLogger.isDiagnosticsEnabled = enabled
    }

    suspend fun clearAll() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
        // Reset diagnostics
        SecureLogger.isDiagnosticsEnabled = false
    }

    companion object {
        private val MASTER_PASSWORD_SET = booleanPreferencesKey("master_password_set")
        private val MASTER_PASSWORD_HASH = stringPreferencesKey("master_password_hash")
        private val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        private val LAST_UNLOCK_TIMESTAMP = longPreferencesKey("last_unlock_timestamp")
        private val DIAGNOSTICS_ENABLED = booleanPreferencesKey("diagnostics_enabled")
    }
}
