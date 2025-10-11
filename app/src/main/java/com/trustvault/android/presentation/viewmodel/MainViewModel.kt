package com.trustvault.android.presentation.viewmodel

import androidx.lifecycle.ViewModel
import com.trustvault.android.security.DatabaseKeyManager
import com.trustvault.android.util.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val databaseKeyManager: DatabaseKeyManager
) : ViewModel() {
    val isMasterPasswordSet: Flow<Boolean> = preferencesManager.isMasterPasswordSet

    /**
     * Locks the database and clears encryption keys from memory.
     * Should be called when app goes to background or user logs out.
     *
     * SECURITY: This ensures the database encryption key is not kept in memory
     * when the app is not actively being used.
     */
    fun lockDatabase() {
        databaseKeyManager.lockDatabase()
    }

    /**
     * Checks if the database is currently unlocked and accessible.
     */
    fun isDatabaseUnlocked(): Boolean {
        return databaseKeyManager.isDatabaseInitialized()
    }

    override fun onCleared() {
        super.onCleared()
        // Ensure database is locked when ViewModel is destroyed
        lockDatabase()
    }
}
