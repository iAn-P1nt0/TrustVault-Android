package com.trustvault.android.presentation.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trustvault.android.data.backup.BackupManager
import com.trustvault.android.data.importexport.CsvExporter
import com.trustvault.android.domain.model.Credential
import com.trustvault.android.domain.repository.CredentialRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * ViewModel for import/export operations.
 * Manages CSV export, file saving, and backup creation.
 */
@HiltViewModel
class ImportExportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val credentialRepository: CredentialRepository,
    private val csvExporter: CsvExporter,
    private val backupManager: BackupManager
) : ViewModel() {

    companion object {
        private const val TAG = "ImportExportViewModel"
        private const val EXPORT_DIR = "exports"
    }

    sealed class UiState {
        object Idle : UiState()
        object Loading : UiState()
        data class Success(val message: String) : UiState()
        data class Error(val message: String) : UiState()
        data class ExportReady(val csvContent: String, val fileName: String, val fileUri: Uri? = null) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /**
     * Exports all credentials to CSV format and saves to file.
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

                // Save to app cache directory
                val file = saveExportToFile(csvContent, fileName)
                val fileUri = getFileUri(file)

                Log.d(TAG, "CSV export saved: ${file.absolutePath}")
                _uiState.value = UiState.ExportReady(csvContent, fileName, fileUri)
            } catch (e: Exception) {
                Log.e(TAG, "Export failed", e)
                _uiState.value = UiState.Error("Export failed: ${e.message}")
            }
        }
    }

    /**
     * Saves export content to a file in the cache directory.
     */
    private fun saveExportToFile(content: String, fileName: String): File {
        val exportDir = File(context.cacheDir, EXPORT_DIR).apply {
            if (!exists()) {
                mkdirs()
            }
        }

        val file = File(exportDir, fileName)
        file.writeText(content, Charsets.UTF_8)
        return file
    }

    /**
     * Gets a content URI for a file using FileProvider.
     */
    private fun getFileUri(file: File): Uri {
        return try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get file URI", e)
            Uri.fromFile(file)
        }
    }

    /**
     * Creates a share intent for the exported CSV file.
     */
    fun getShareIntent(fileUri: Uri, fileName: String): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, fileUri)
            putExtra(Intent.EXTRA_SUBJECT, "TrustVault Export")
            putExtra(Intent.EXTRA_TEXT, "Exported credentials from TrustVault: $fileName")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
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
