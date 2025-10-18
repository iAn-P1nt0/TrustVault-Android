package com.trustvault.android.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trustvault.android.data.backup.BackupManager
import com.trustvault.android.data.importexport.CsvExporter
import com.trustvault.android.domain.model.Credential
import com.trustvault.android.domain.repository.CredentialRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for import/export operations.
 * Manages CSV export and backup creation.
 */
@HiltViewModel
class ImportExportViewModel @Inject constructor(
    private val credentialRepository: CredentialRepository,
    private val csvExporter: CsvExporter,
    private val backupManager: BackupManager
) : ViewModel() {

    sealed class UiState {
        object Idle : UiState()
        object Loading : UiState()
        data class Success(val message: String) : UiState()
        data class Error(val message: String) : UiState()
        data class ExportReady(val csvContent: String, val fileName: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /**
     * Exports all credentials to CSV format.
     */
    fun exportToCSV() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val credentials = credentialRepository.getAllCredentials().first()
                if (credentials.isEmpty()) {
                    _uiState.value = UiState.Error("No credentials to export")
                    return@launch
                }

                val csvContent = csvExporter.exportToString(credentials)
                val fileName = "trustvault_export_${System.currentTimeMillis()}.csv"
                _uiState.value = UiState.ExportReady(csvContent, fileName)
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Export failed: ${e.message}")
            }
        }
    }

    /**
     * Creates an encrypted backup of all credentials.
     */
    fun createBackup(masterPassword: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val result = backupManager.createBackup(masterPassword)
                result.onSuccess { backupFile ->
                    _uiState.value = UiState.Success(
                        "Backup created: ${backupFile.name}"
                    )
                }.onFailure { exception ->
                    _uiState.value = UiState.Error(
                        "Backup failed: ${exception.message}"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Backup error: ${e.message}")
            }
        }
    }

    fun resetUiState() {
        _uiState.value = UiState.Idle
    }
}
