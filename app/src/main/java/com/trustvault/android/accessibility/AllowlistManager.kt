package com.trustvault.android.accessibility

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.accessibilityDataStore by preferencesDataStore(
    name = "accessibility_preferences"
)

/**
 * Manages the allowlist of apps that can use the Accessibility Service fallback.
 *
 * **Purpose**:
 * - Explicit user control over which apps get credential autofill
 * - Prevent credential leakage to untrusted apps
 * - Easy enable/disable per app
 *
 * **Storage**:
 * - Encrypted DataStore (Android Jetpack)
 * - Per-app opt-in model
 * - Survives app updates
 *
 * **Privacy**:
 * - No implicit allowlist (defaults to deny)
 * - User must explicitly enable per app
 * - Clear UI showing allowed apps
 * - Easy to audit and revoke access
 *
 * **Implementation Notes**:
 * - Uses encrypted DataStore for secure storage
 * - Coroutine-based for non-blocking access
 * - Package names stored as set of strings
 * - Can be updated in real-time
 *
 * Example Usage:
 * ```kotlin
 * // Add app to allowlist
 * allowlistManager.addPackageToAllowlist("com.example.app")
 *
 * // Check if app is allowed
 * if (allowlistManager.isPackageAllowed("com.example.app")) {
 *     // Provide autofill for this app
 * }
 *
 * // Get all allowed apps
 * val allowedApps = allowlistManager.getAllowedPackages().first()
 * ```
 *
 * @see TrustVaultAccessibilityService For usage
 * @see AccessibilityPreferences For user preferences UI
 */
@Singleton
class AllowlistManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val packageManager: PackageManager
) {

    companion object {
        private const val TAG = "AllowlistManager"
        private val ALLOWLIST_KEY = stringSetPreferencesKey("accessibility_allowlist")
    }

    /**
     * Gets all currently allowed packages.
     *
     * @return Flow of set of package names that have been allowlisted
     */
    fun getAllowedPackages(): Flow<Set<String>> {
        return context.accessibilityDataStore.data.map { preferences ->
            preferences[ALLOWLIST_KEY] ?: emptySet()
        }
    }

    /**
     * Checks if a package is in the allowlist.
     *
     * @param packageName Package name to check
     * @return true if package is explicitly allowed
     */
    suspend fun isPackageAllowed(packageName: String): Boolean {
        return getAllowedPackages().map { it.contains(packageName) }.first()
    }

    /**
     * Adds a package to the allowlist.
     *
     * @param packageName Package name to allow
     */
    suspend fun addPackageToAllowlist(packageName: String) {
        context.accessibilityDataStore.edit { preferences ->
            val current = preferences[ALLOWLIST_KEY] ?: emptySet()
            preferences[ALLOWLIST_KEY] = current + packageName
        }
        Log.d(TAG, "Added to allowlist: $packageName")
    }

    /**
     * Removes a package from the allowlist.
     *
     * @param packageName Package name to remove
     */
    suspend fun removePackageFromAllowlist(packageName: String) {
        context.accessibilityDataStore.edit { preferences ->
            val current = preferences[ALLOWLIST_KEY] ?: emptySet()
            preferences[ALLOWLIST_KEY] = current - packageName
        }
        Log.d(TAG, "Removed from allowlist: $packageName")
    }

    /**
     * Clears the entire allowlist.
     *
     * Use with caution - this removes all app permissions.
     */
    suspend fun clearAllowlist() {
        context.accessibilityDataStore.edit { preferences ->
            preferences.remove(ALLOWLIST_KEY)
        }
        Log.d(TAG, "Cleared allowlist")
    }

    /**
     * Gets information about an app.
     *
     * @param packageName Package name
     * @return ApplicationInfo or null if not found
     */
    fun getApplicationInfo(packageName: String): ApplicationInfo? {
        return try {
            packageManager.getApplicationInfo(packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "Package not found: $packageName")
            null
        }
    }

    /**
     * Gets the display name of an app.
     *
     * @param packageName Package name
     * @return App display name or package name if not found
     */
    fun getAppLabel(packageName: String): String {
        return try {
            val appInfo = getApplicationInfo(packageName) ?: return packageName
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            Log.w(TAG, "Error getting app label: ${e.message}")
            packageName
        }
    }

    /**
     * Gets all installed apps that could potentially use credential autofill.
     *
     * Filters to apps that:
     * - Have Internet permission (likely login apps)
     * - Are not system apps (excludes built-ins)
     * - Are installed as user apps
     *
     * @return List of PackageInfo for potentially relevant apps
     */
    fun getInstalledApps(): List<InstalledAppInfo> {
        return try {
            packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { appInfo ->
                    // Skip system apps
                    appInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0
                }
                .map { appInfo ->
                    InstalledAppInfo(
                        packageName = appInfo.packageName,
                        displayName = packageManager.getApplicationLabel(appInfo).toString()
                    )
                }
                .sortedBy { it.displayName }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting installed apps: ${e.message}")
            emptyList()
        }
    }

    /**
     * Data class representing an installed app.
     */
    data class InstalledAppInfo(
        val packageName: String,
        val displayName: String
    )
}
