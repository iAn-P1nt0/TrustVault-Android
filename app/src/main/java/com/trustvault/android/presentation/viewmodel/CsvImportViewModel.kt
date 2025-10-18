package com.trustvault.android.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trustvault.android.data.importexport.CsvFieldMapping
import com.trustvault.android.data.importexport.CsvImporter
import com.trustvault.android.data.importexport.ImportPreview
import com.trustvault.android.data.importexport.ImportValidator
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
 * ViewModel for CSV import workflow.
 * Handles field mapping, preview, and import execution.
 */
@HiltViewModel
class CsvImportViewModel @Inject constructor(
    private val csvImporter: CsvImporter,
    private val importValidator: ImportValidator,
    private val credentialRepository: CredentialRepository
) : ViewModel() {

    sealed class UiState {
        object Idle : UiState()
        object AwaitingFile : UiState()
        data class ParsedCsv(val preview: ImportPreview, val fieldMapping: CsvFieldMapping) : UiState()
        object Importing : UiState()
        data class ImportSuccess(val count: Int) : UiState()
        data class ImportError(val message: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var currentPreview: ImportPreview? = null
    private var currentFieldMapping: CsvFieldMapping? = null

    /**
     * Loads and parses CSV file content.
     * Auto-detects field mapping and validates credentials.
     */
    fun loadCsvFile(csvContent: String) {
        viewModelScope.launch {
            try {
                // Auto-detect field mapping from CSV headers
                val fieldMapping = csvImporter.autoDetectFieldMapping(csvContent)

                // Parse CSV with detected mapping
                val preview = csvImporter.import(csvContent, fieldMapping)

                // Validate against existing credentials
                val existingCredentials = credentialRepository.getAllCredentials().first()
                val validatedPreview = importValidator.validate(
                    preview.credentials,
                    existingCredentials,
                    "CSV"
                )

                currentPreview = validatedPreview
                currentFieldMapping = fieldMapping

                _uiState.value = UiState.ParsedCsv(validatedPreview, fieldMapping)
            } catch (e: Exception) {
                _uiState.value = UiState.ImportError("Failed to parse CSV: ${e.message}")
            }
        }
    }

    /**
     * Executes the import with current conflicts resolved.
     */
    fun executeImport() {
        val preview = currentPreview ?: run {
            _uiState.value = UiState.ImportError("No preview loaded")
            return
        }

        viewModelScope.launch {
            _uiState.value = UiState.Importing
            try {
                // Apply conflict resolutions
                val credentialsToImport = importValidator.applyResolutions(preview)

                // Import credentials
                var importedCount = 0
                for (credential in credentialsToImport) {
                    try {
                        credentialRepository.insertCredential(credential)
                        importedCount++
                    } catch (e: Exception) {
                        // Continue with next credential on error
                    }
                }

                _uiState.value = UiState.ImportSuccess(importedCount)
            } catch (e: Exception) {
                _uiState.value = UiState.ImportError("Import failed: ${e.message}")
            }
        }
    }

    /**
     * Updates conflict resolution for a specific credential.
     */
    fun resolveConflict(
        credentialId: Long,
        resolution: com.trustvault.android.data.importexport.ImportConflict.ConflictResolution
    ) {
        currentPreview?.let { preview ->
            val updated = preview.copy(
                conflicts = preview.conflicts.map { conflict ->
                    if (conflict.importedCredential.id == credentialId) {
                        conflict.copy(userResolution = resolution)
                    } else {
                        conflict
                    }
                }
            )
            currentPreview = updated
            _uiState.value = UiState.ParsedCsv(updated, currentFieldMapping ?: CsvFieldMapping())
        }
    }

    fun resetUiState() {
        _uiState.value = UiState.Idle
        currentPreview = null
        currentFieldMapping = null
    }
}
