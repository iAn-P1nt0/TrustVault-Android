package com.trustvault.android.security

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Root detection utility for TrustVault.
 *
 * SECURITY NOTES:
 * - Detects common root indicators on Android devices
 * - This is NOT foolproof - advanced rooting tools can hide detection
 * - Should be used for user warning, NOT enforcement
 *
 * THREAT MODEL:
 * - Rooted devices can access app memory and extract encryption keys
 * - Magisk and other root-hiding tools can bypass this detection
 * - Physical access + root = full device compromise
 *
 * RECOMMENDATION:
 * - Warn users that rooted devices are less secure
 * - Do NOT block app usage (users own their devices)
 * - Consider Google Play Integrity API for better attestation
 */
@Singleton
class RootDetection @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Checks if the device is rooted.
     *
     * @return true if root indicators found, false otherwise
     */
    fun isDeviceRooted(): Boolean {
        return checkBuildTags() ||
               checkSuBinary() ||
               checkRootApps() ||
               checkRootCloakingApps() ||
               checkDangerousSystemProps()
    }

    /**
     * Gets detailed root detection results for debugging.
     *
     * @return Map of detection methods and their results
     */
    fun getRootDetectionDetails(): Map<String, Boolean> {
        return mapOf(
            "Build Tags" to checkBuildTags(),
            "SU Binary" to checkSuBinary(),
            "Root Apps" to checkRootApps(),
            "Root Cloaking" to checkRootCloakingApps(),
            "System Props" to checkDangerousSystemProps()
        )
    }

    /**
     * Checks if build tags indicate test/unofficial build.
     */
    private fun checkBuildTags(): Boolean {
        val buildTags = Build.TAGS
        return buildTags != null && buildTags.contains("test-keys")
    }

    /**
     * Checks for presence of su (superuser) binary in common locations.
     */
    private fun checkSuBinary(): Boolean {
        val suPaths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su"
        )

        for (path in suPaths) {
            if (File(path).exists()) {
                return true
            }
        }

        return false
    }

    /**
     * Checks for common root management apps.
     */
    private fun checkRootApps(): Boolean {
        val rootApps = arrayOf(
            "com.noshufou.android.su",
            "com.noshufou.android.su.elite",
            "eu.chainfire.supersu",
            "com.koushikdutta.superuser",
            "com.thirdparty.superuser",
            "com.yellowes.su",
            "com.topjohnwu.magisk"  // Magisk
        )

        val pm = context.packageManager
        for (packageName in rootApps) {
            try {
                pm.getPackageInfo(packageName, 0)
                return true  // Package found
            } catch (e: Exception) {
                // Package not found, continue
            }
        }

        return false
    }

    /**
     * Checks for root cloaking/hiding apps.
     */
    private fun checkRootCloakingApps(): Boolean {
        val cloakingApps = arrayOf(
            "com.devadvance.rootcloak",
            "com.devadvance.rootcloakplus",
            "de.robv.android.xposed.installer",
            "com.saurik.substrate",
            "com.zachspong.temprootremovejb",
            "com.amphoras.hidemyroot",
            "com.amphoras.hidemyrootadfree",
            "com.formyhm.hiderootPremium",
            "com.formyhm.hideroot"
        )

        val pm = context.packageManager
        for (packageName in cloakingApps) {
            try {
                pm.getPackageInfo(packageName, 0)
                return true  // Package found
            } catch (e: Exception) {
                // Package not found, continue
            }
        }

        return false
    }

    /**
     * Checks for dangerous system properties.
     */
    private fun checkDangerousSystemProps(): Boolean {
        val dangerousProps = mapOf(
            "ro.debuggable" to "1",
            "ro.secure" to "0"
        )

        for ((key, value) in dangerousProps) {
            val prop = getSystemProperty(key)
            if (prop == value) {
                return true
            }
        }

        return false
    }

    /**
     * Safely reads system property.
     */
    private fun getSystemProperty(key: String): String? {
        return try {
            val process = Runtime.getRuntime().exec("getprop $key")
            process.inputStream.bufferedReader().use { it.readText().trim() }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Gets a user-friendly security warning message.
     *
     * @return Security warning text for rooted devices
     */
    fun getRootWarningMessage(): String {
        return """
            ⚠️ SECURITY WARNING: Rooted Device Detected

            Your device appears to be rooted. While TrustVault will continue to work,
            rooted devices have reduced security:

            • Encryption keys may be accessible in memory
            • Malicious apps can access protected data
            • Hardware security may be compromised

            For maximum security, use TrustVault on a non-rooted device.
        """.trimIndent()
    }
}
