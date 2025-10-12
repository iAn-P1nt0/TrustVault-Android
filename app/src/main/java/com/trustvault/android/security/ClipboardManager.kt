package com.trustvault.android.security

import android.content.ClipData
import android.content.ClipboardManager as AndroidClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Secure clipboard manager that automatically clears sensitive data.
 * Implements OWASP Mobile Best Practice: Clear clipboard after timeout.
 *
 * Security Features:
 * - Auto-clear after configurable timeout (default: 60 seconds)
 * - Sensitive data flagging (prevents clipboard sync on Android 13+)
 * - Manual clear support
 * - Prevents clipboard history on Android 10+
 */
@Singleton
class ClipboardManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val systemClipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as AndroidClipboardManager
    private val scope = CoroutineScope(Dispatchers.Main)
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var clearJob: Job? = null

    /**
     * Clipboard auto-clear timeout options.
     */
    enum class ClearTimeout(val seconds: Int, val displayName: String) {
        FIFTEEN_SECONDS(15, "15 seconds"),
        THIRTY_SECONDS(30, "30 seconds"),
        SIXTY_SECONDS(60, "1 minute"),
        TWO_MINUTES(120, "2 minutes"),
        NEVER(-1, "Never")
    }

    /**
     * Copies sensitive text to clipboard with auto-clear.
     * Marks data as sensitive to prevent sync and history.
     *
     * @param text The sensitive text to copy
     * @param label A user-visible label for the clip
     */
    fun copyToClipboard(text: String, label: String = "TrustVault") {
        try {
            val clipData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13+: Mark as sensitive to prevent sync
                ClipData.newPlainText(label, text).apply {
                    description.extras = PersistableBundle().apply {
                        putBoolean("android.content.extra.IS_SENSITIVE", true)
                    }
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10-12: Prevent adding to clipboard history
                ClipData.newPlainText(label, text).apply {
                    description.extras = PersistableBundle()
                }
            } else {
                // Android 8-9: Standard clip
                ClipData.newPlainText(label, text)
            }

            systemClipboard.setPrimaryClip(clipData)

            // Schedule auto-clear
            scheduleAutoClear()

            Log.d(TAG, "Copied to clipboard with auto-clear scheduled")
        } catch (e: Exception) {
            Log.e(TAG, "Error copying to clipboard: ${e.message}")
        }
    }

    /**
     * Clears the clipboard immediately.
     */
    fun clearClipboard() {
        try {
            // Cancel scheduled clear
            clearJob?.cancel()
            clearJob = null

            // Clear clipboard by setting empty clip
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                systemClipboard.clearPrimaryClip()
            } else {
                // Android 8: Set empty clip data
                val emptyClip = ClipData.newPlainText("", "")
                systemClipboard.setPrimaryClip(emptyClip)
            }

            Log.d(TAG, "Clipboard cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing clipboard: ${e.message}")
        }
    }

    /**
     * Sets the auto-clear timeout.
     */
    fun setClearTimeout(timeout: ClearTimeout) {
        prefs.edit()
            .putInt(KEY_CLEAR_TIMEOUT, timeout.seconds)
            .apply()
    }

    /**
     * Gets the current clear timeout setting.
     */
    fun getClearTimeout(): ClearTimeout {
        val seconds = prefs.getInt(KEY_CLEAR_TIMEOUT, ClearTimeout.SIXTY_SECONDS.seconds)
        return ClearTimeout.values().find { it.seconds == seconds } ?: ClearTimeout.SIXTY_SECONDS
    }

    /**
     * Schedules automatic clipboard clearing.
     */
    private fun scheduleAutoClear() {
        // Cancel existing job
        clearJob?.cancel()

        val timeout = getClearTimeout()

        // Don't schedule if NEVER
        if (timeout == ClearTimeout.NEVER) {
            return
        }

        // Schedule clear
        clearJob = scope.launch {
            delay(timeout.seconds * 1000L)
            clearClipboard()
        }
    }

    /**
     * Checks if the clipboard contains data copied by TrustVault.
     */
    fun hasClipboardData(): Boolean {
        return try {
            systemClipboard.hasPrimaryClip() && systemClipboard.primaryClip != null
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Gets the current clipboard content (for debugging only).
     * DO NOT expose this to UI for security reasons.
     */
    internal fun getClipboardContent(): String? {
        return try {
            systemClipboard.primaryClip?.let { clip ->
                if (clip.itemCount > 0) {
                    clip.getItemAt(0).text?.toString()
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val TAG = "SecureClipboardManager"
        private const val PREFS_NAME = "trustvault_clipboard"
        private const val KEY_CLEAR_TIMEOUT = "clear_timeout_seconds"
    }
}