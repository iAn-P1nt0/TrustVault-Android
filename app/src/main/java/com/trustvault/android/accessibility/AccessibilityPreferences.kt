package com.trustvault.android.accessibility

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.accessibilityPrefsDataStore by preferencesDataStore(
    name = "accessibility_service_prefs"
)

/**
 * Manages user preferences for the Accessibility Service fallback.
 *
 * **Preferences**:
 * - `accessibility_service_enabled`: Enable/disable the entire service
 *
 * **Design**:
 * - Service is **disabled by default** (requires explicit user opt-in)
 * - User can disable immediately from system settings or app settings
 * - Changes take effect immediately (no restart needed)
 *
 * **Privacy First**:
 * - Default OFF - user must explicitly enable
 * - No implicit enablement
 * - Audit trail: Check which preferences are enabled
 * - Easy toggle on/off
 *
 * **Storage**:
 * - Encrypted DataStore (Android Jetpack)
 * - Persistent across app updates
 * - User can wipe at any time
 *
 * Implementation Notes:
 * - Uses Flow for reactive updates
 * - Thread-safe (handled by DataStore)
 * - No callbacks - use Flow collectors
 * - Non-blocking (suspending functions)
 *
 * Example Usage:
 * ```kotlin
 * // Check if enabled
 * val isEnabled = prefs.isAccessibilityServiceEnabled().first()
 *
 * // Listen for changes
 * prefs.isAccessibilityServiceEnabled().collect { enabled ->
 *     updateUI(enabled)
 * }
 *
 * // Enable/disable
 * prefs.setAccessibilityServiceEnabled(true)
 * ```
 *
 * @see TrustVaultAccessibilityService For service implementation
 * @see AllowlistManager For per-app allowlisting
 */
@Singleton
class AccessibilityPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "AccessibilityPreferences"
        private val ACCESSIBILITY_SERVICE_ENABLED = booleanPreferencesKey(
            "accessibility_service_enabled"
        )
    }

    /**
     * Gets the enabled state of the Accessibility Service.
     *
     * **Default**: false (disabled by default)
     *
     * @return Flow that emits true if service is enabled, false otherwise
     */
    fun isAccessibilityServiceEnabled(): Flow<Boolean> {
        return context.accessibilityPrefsDataStore.data.map { preferences ->
            preferences[ACCESSIBILITY_SERVICE_ENABLED] ?: false
        }
    }

    /**
     * Gets the current enabled state.
     *
     * Note: For reactive updates, use isAccessibilityServiceEnabled() with Flow.
     *
     * @return Flow that emits true if service is enabled
     */
    fun getAccessibilityServiceEnabled(): Flow<Boolean> {
        return isAccessibilityServiceEnabled()
    }

    /**
     * Sets the enabled state of the Accessibility Service.
     *
     * **Important**:
     * - When disabled, service stops immediately
     * - When enabled, user must also enable in system settings
     * - Changes take effect immediately
     * - No restart needed
     *
     * @param enabled true to enable, false to disable
     */
    suspend fun setAccessibilityServiceEnabled(enabled: Boolean) {
        context.accessibilityPrefsDataStore.edit { preferences ->
            preferences[ACCESSIBILITY_SERVICE_ENABLED] = enabled
        }
        Log.d(TAG, "Accessibility service enabled: $enabled")
    }

    /**
     * Disables the Accessibility Service.
     *
     * Equivalent to setAccessibilityServiceEnabled(false).
     * Use this for immediate disable from settings.
     */
    suspend fun disableAccessibilityService() {
        setAccessibilityServiceEnabled(false)
    }

    /**
     * Enables the Accessibility Service.
     *
     * Equivalent to setAccessibilityServiceEnabled(true).
     * Note: User must also enable in system settings.
     */
    suspend fun enableAccessibilityService() {
        setAccessibilityServiceEnabled(true)
    }

    /**
     * Resets all accessibility preferences to defaults.
     *
     * Disables the service and clears any customizations.
     */
    suspend fun resetToDefaults() {
        context.accessibilityPrefsDataStore.edit { preferences ->
            preferences.clear()
        }
        Log.d(TAG, "Reset accessibility preferences to defaults")
    }
}
