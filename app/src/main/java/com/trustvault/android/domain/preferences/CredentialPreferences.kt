package com.trustvault.android.domain.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.credentialPreferencesDataStore by preferencesDataStore(name = "credential_preferences")

/**
 * Manages user preferences for credential handling.
 *
 * Allows users to choose between:
 * - Credential Manager (Android 14+): Modern bottom sheet UI
 * - AutofillService (Android 8+): Classic autofill dropdown
 *
 * Default behavior:
 * - Android 14+: Prefer Credential Manager if available
 * - Android 13 and below: Use AutofillService only
 */
@Singleton
class CredentialPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private object PreferenceKeys {
        val USE_CREDENTIAL_MANAGER = booleanPreferencesKey("use_credential_manager")
        val CREDENTIAL_MANAGER_ENABLED = booleanPreferencesKey("credential_manager_enabled")
    }

    /**
     * Whether to prefer Credential Manager over Autofill when both are available.
     * Default: true on Android 14+, false otherwise
     */
    val useCredentialManager: Flow<Boolean> = context.credentialPreferencesDataStore.data
        .map { preferences ->
            preferences[PreferenceKeys.USE_CREDENTIAL_MANAGER] ?: isCredentialManagerSupported()
        }

    /**
     * Whether Credential Manager integration is enabled at all.
     * User can disable to use only AutofillService.
     */
    val credentialManagerEnabled: Flow<Boolean> = context.credentialPreferencesDataStore.data
        .map { preferences ->
            preferences[PreferenceKeys.CREDENTIAL_MANAGER_ENABLED] ?: true
        }

    suspend fun setUseCredentialManager(enabled: Boolean) {
        context.credentialPreferencesDataStore.edit { preferences ->
            preferences[PreferenceKeys.USE_CREDENTIAL_MANAGER] = enabled
        }
    }

    suspend fun setCredentialManagerEnabled(enabled: Boolean) {
        context.credentialPreferencesDataStore.edit { preferences ->
            preferences[PreferenceKeys.CREDENTIAL_MANAGER_ENABLED] = enabled
        }
    }

    /**
     * Checks if Credential Manager is supported on this device.
     * Requires Android 14+ (API 34+)
     */
    private fun isCredentialManagerSupported(): Boolean {
        return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
    }

    /**
     * Gets the recommended credential handling mode for the current device.
     */
    suspend fun getRecommendedMode(): CredentialMode {
        return if (isCredentialManagerSupported()) {
            CredentialMode.CREDENTIAL_MANAGER
        } else {
            CredentialMode.AUTOFILL_ONLY
        }
    }
}

enum class CredentialMode {
    /**
     * Use Credential Manager (Android 14+)
     */
    CREDENTIAL_MANAGER,

    /**
     * Use AutofillService only (Android 8+)
     */
    AUTOFILL_ONLY,

    /**
     * Use both - Credential Manager for supported apps, Autofill for others
     */
    HYBRID
}

