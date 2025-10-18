package com.trustvault.android.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trustvault.android.data.backup.BackupInfo
import com.trustvault.android.data.backup.BackupManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * ViewModel for backup management operations.
 * Handles creating, restoring, and managing backups.
 */
@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupManager: BackupManager
) : ViewModel() {

    sealed class UiState {
        object Idle : UiState()
        data class BackupList(val backups: List<BackupInfo>) : UiState()
        object CreatingBackup : UiState()
        data class BackupCreated(val fileName: String) : UiState()
        object RestoringBackup : UiState()
        data class BackupRestored(val count: Int) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /**
     * Loads all available backups.
     */
    fun loadBackups() {
        viewModelScope.launch {
            try {
                val backups = backupManager.listBackups()
                _uiState.value = UiState.BackupList(backups)
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Failed to load backups: ${e.message}")
            }
        }
    }

    /**
     * Creates a new encrypted backup.
     */
    fun createBackup(masterPassword: String) {
        viewModelScope.launch {
            _uiState.value = UiState.CreatingBackup
            try {
                val result = backupManager.createBackup(masterPassword)
                result.onSuccess { backupFile ->
                    _uiState.value = UiState.BackupCreated(backupFile.name)
                    // Reload backups list
                    loadBackups()
                }.onFailure { exception ->
                    _uiState.value = UiState.Error("Backup failed: ${exception.message}")
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Backup error: ${e.message}")
            }
        }
    }

    /**
     * Restores credentials from a backup file.
     */
    fun restoreBackup(backupFile: File, masterPassword: String) {
        viewModelScope.launch {
            _uiState.value = UiState.RestoringBackup
            try {
                val result = backupManager.restoreBackup(backupFile, masterPassword)
                result.onSuccess { restoreResult ->
                    _uiState.value = UiState.BackupRestored(restoreResult.credentialsRestored)
                }.onFailure { exception ->
                    _uiState.value = UiState.Error("Restore failed: ${exception.message}")
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Restore error: ${e.message}")
            }
        }
    }

    /**
     * Deletes a backup file.
     */
    fun deleteBackup(backupFile: File) {
        viewModelScope.launch {
            try {
                if (backupManager.deleteBackup(backupFile)) {
                    // Reload backups list
                    loadBackups()
                } else {
                    _uiState.value = UiState.Error("Failed to delete backup")
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Delete error: ${e.message}")
            }
        }
    }

    fun resetUiState() {
        _uiState.value = UiState.Idle
    }
}
