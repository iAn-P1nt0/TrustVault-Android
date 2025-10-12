package com.trustvault.android.security

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner as AndroidProcessLifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages automatic locking of the app after inactivity.
 * Implements OWASP Mobile Best Practice: Session timeout after inactivity.
 *
 * Security Features:
 * - Configurable timeout (1-30 minutes)
 * - Automatic lock on app backgrounding
 * - Manual lock support
 * - Lifecycle-aware implementation
 */
@Singleton
class AutoLockManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val databaseKeyManager: DatabaseKeyManager
) : DefaultLifecycleObserver {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.Main)
    private var lockJob: Job? = null
    private var lastActivityTime: Long = System.currentTimeMillis()

    /**
     * Auto-lock timeout options in seconds.
     */
    enum class LockTimeout(val seconds: Int, val displayName: String) {
        IMMEDIATE(0, "Immediately"),
        ONE_MINUTE(60, "1 minute"),
        TWO_MINUTES(120, "2 minutes"),
        FIVE_MINUTES(300, "5 minutes"),
        TEN_MINUTES(600, "10 minutes"),
        FIFTEEN_MINUTES(900, "15 minutes"),
        THIRTY_MINUTES(1800, "30 minutes"),
        NEVER(-1, "Never")
    }

    init {
        // Register lifecycle observer
        AndroidProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    /**
     * Sets the auto-lock timeout.
     */
    fun setLockTimeout(timeout: LockTimeout) {
        prefs.edit()
            .putInt(KEY_LOCK_TIMEOUT, timeout.seconds)
            .apply()

        // Restart timer with new timeout
        resetLockTimer()
    }

    /**
     * Gets the current lock timeout setting.
     */
    fun getLockTimeout(): LockTimeout {
        val seconds = prefs.getInt(KEY_LOCK_TIMEOUT, LockTimeout.FIVE_MINUTES.seconds)
        return LockTimeout.values().find { it.seconds == seconds } ?: LockTimeout.FIVE_MINUTES
    }

    /**
     * Enables or disables background lock.
     * When enabled, app locks immediately when moved to background.
     */
    fun setLockOnBackground(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_LOCK_ON_BACKGROUND, enabled)
            .apply()
    }

    /**
     * Checks if lock on background is enabled.
     */
    fun isLockOnBackgroundEnabled(): Boolean {
        return prefs.getBoolean(KEY_LOCK_ON_BACKGROUND, true) // Default: enabled
    }

    /**
     * Records user activity to reset the inactivity timer.
     * Call this on any user interaction (screen touch, button press, etc.)
     */
    fun recordActivity() {
        lastActivityTime = System.currentTimeMillis()
        resetLockTimer()
    }

    /**
     * Manually locks the app immediately.
     */
    fun lockNow() {
        lockApp()
    }

    /**
     * Checks if the app should be locked based on inactivity.
     */
    fun shouldLock(): Boolean {
        val timeout = getLockTimeout()

        // Never timeout
        if (timeout == LockTimeout.NEVER) {
            return false
        }

        // Immediate timeout
        if (timeout == LockTimeout.IMMEDIATE) {
            return true
        }

        // Check if timeout exceeded
        val elapsedSeconds = (System.currentTimeMillis() - lastActivityTime) / 1000
        return elapsedSeconds >= timeout.seconds
    }

    /**
     * Lifecycle: App moved to background
     */
    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)

        // Lock immediately if enabled
        if (isLockOnBackgroundEnabled()) {
            lockApp()
        } else {
            // Start inactivity timer
            resetLockTimer()
        }
    }

    /**
     * Lifecycle: App moved to foreground
     */
    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)

        // Check if we should lock based on time away
        if (shouldLock()) {
            lockApp()
        } else {
            // Reset timer on return
            recordActivity()
        }
    }

    /**
     * Resets the auto-lock countdown timer.
     */
    private fun resetLockTimer() {
        // Cancel existing timer
        lockJob?.cancel()

        val timeout = getLockTimeout()

        // Don't start timer if NEVER or IMMEDIATE
        if (timeout == LockTimeout.NEVER || timeout == LockTimeout.IMMEDIATE) {
            return
        }

        // Start new countdown
        lockJob = scope.launch {
            delay(timeout.seconds * 1000L)
            if (shouldLock()) {
                lockApp()
            }
        }
    }

    /**
     * Locks the app by clearing database keys and requiring re-authentication.
     */
    private fun lockApp() {
        lockJob?.cancel()
        lockJob = null

        // Clear database keys from memory
        if (databaseKeyManager.isDatabaseInitialized()) {
            databaseKeyManager.lockDatabase()
        }
    }

    companion object {
        private const val PREFS_NAME = "trustvault_autolock"
        private const val KEY_LOCK_TIMEOUT = "lock_timeout_seconds"
        private const val KEY_LOCK_ON_BACKGROUND = "lock_on_background"
    }
}