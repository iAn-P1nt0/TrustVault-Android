package com.trustvault.android.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trustvault.android.compliance.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * PrivacyDashboardViewModel - Privacy Dashboard State Management
 *
 * Manages UI state for the Privacy Dashboard screen, providing comprehensive
 * privacy and compliance information to users.
 *
 * **GDPR Article 13-14:** Information to be provided to data subjects
 * **DPDP Act Section 4:** Providing information to data principals
 *
 * **Features:**
 * - Display data collection practices
 * - Show consent status for all processing purposes
 * - Display data retention policies
 * - Show data export history
 * - Provide access to data erasure
 * - Display compliance information
 *
 * @property privacyManager Privacy settings and consent management
 * @property consentManager Consent record management
 * @property dataRetentionManager Data retention policies
 * @property dataErasure Data erasure operations
 * @property dataPortability Data export operations
 */
@HiltViewModel
class PrivacyDashboardViewModel @Inject constructor(
    private val privacyManager: PrivacyManager,
    private val consentManager: ConsentManager,
    private val dataRetentionManager: DataRetentionManager,
    private val dataErasure: DataErasure,
    private val dataPortability: DataPortability
) : ViewModel() {

    companion object {
        private const val TAG = "PrivacyDashboardViewModel"
    }

    // UI State
    private val _uiState = MutableStateFlow(PrivacyDashboardUiState())
    val uiState: StateFlow<PrivacyDashboardUiState> = _uiState.asStateFlow()

    init {
        loadPrivacyData()
        observeConsentChanges()
    }

    /**
     * Privacy Dashboard UI State.
     */
    data class PrivacyDashboardUiState(
        val isLoading: Boolean = true,
        val dashboardData: PrivacyManager.PrivacyDashboardData? = null,
        val consentRecords: Map<PrivacyManager.DataProcessingPurpose, ConsentManager.ConsentRecord> = emptyMap(),
        val retentionPolicy: DataRetentionManager.RetentionPolicy? = null,
        val expiredCredentialsCount: Int = 0,
        val showConsentDialog: PrivacyManager.DataProcessingPurpose? = null,
        val showRetentionPolicyDialog: Boolean = false,
        val showDataExportDialog: Boolean = false,
        val showDataErasureDialog: Boolean = false,
        val exportInProgress: Boolean = false,
        val erasureInProgress: Boolean = false,
        val exportResult: DataPortability.ExportResult? = null,
        val erasureResult: DataErasure.ErasureResult? = null,
        val error: String? = null
    )

    /**
     * Loads all privacy dashboard data.
     */
    private fun loadPrivacyData() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null) }

                // Load dashboard data
                val dashboardData = privacyManager.getPrivacyDashboardData()

                // Load consent records
                val consentRecords = consentManager.getAllConsentRecords()

                // Load retention policy
                val retentionPolicy = dataRetentionManager.getRetentionPolicyConfig()

                // Count expired credentials
                val expiredCredentials = dataRetentionManager.findExpiredCredentials()

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        dashboardData = dashboardData,
                        consentRecords = consentRecords,
                        retentionPolicy = retentionPolicy,
                        expiredCredentialsCount = expiredCredentials.size
                    )
                }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Error loading privacy data: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Observes consent changes in real-time.
     */
    private fun observeConsentChanges() {
        viewModelScope.launch {
            consentManager.consentState.collect { consentRecords ->
                _uiState.update {
                    it.copy(consentRecords = consentRecords)
                }
            }
        }
    }

    /**
     * Shows consent dialog for a specific purpose.
     */
    fun showConsentDialog(purpose: PrivacyManager.DataProcessingPurpose) {
        _uiState.update { it.copy(showConsentDialog = purpose) }
    }

    /**
     * Hides consent dialog.
     */
    fun hideConsentDialog() {
        _uiState.update { it.copy(showConsentDialog = null) }
    }

    /**
     * Updates consent for a purpose.
     */
    fun updateConsent(purpose: PrivacyManager.DataProcessingPurpose, granted: Boolean) {
        viewModelScope.launch {
            try {
                privacyManager.setConsent(purpose, granted)
                hideConsentDialog()
                loadPrivacyData() // Refresh data
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = "Error updating consent: ${e.message}")
                }
            }
        }
    }

    /**
     * Withdraws consent for a purpose.
     */
    fun withdrawConsent(purpose: PrivacyManager.DataProcessingPurpose) {
        viewModelScope.launch {
            try {
                if (privacyManager.withdrawConsent(purpose)) {
                    loadPrivacyData() // Refresh data
                } else {
                    _uiState.update {
                        it.copy(error = "Cannot withdraw consent for required purpose")
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = "Error withdrawing consent: ${e.message}")
                }
            }
        }
    }

    /**
     * Shows data retention policy dialog.
     */
    fun showRetentionPolicyDialog() {
        _uiState.update { it.copy(showRetentionPolicyDialog = true) }
    }

    /**
     * Hides data retention policy dialog.
     */
    fun hideRetentionPolicyDialog() {
        _uiState.update { it.copy(showRetentionPolicyDialog = false) }
    }

    /**
     * Updates data retention policy.
     */
    fun updateRetentionPolicy(retentionDays: Int) {
        viewModelScope.launch {
            try {
                if (privacyManager.setDataRetentionPolicy(retentionDays)) {
                    hideRetentionPolicyDialog()
                    loadPrivacyData() // Refresh data
                } else {
                    _uiState.update {
                        it.copy(error = "Error setting retention policy")
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = "Error updating retention policy: ${e.message}")
                }
            }
        }
    }

    /**
     * Shows data export dialog.
     */
    fun showDataExportDialog() {
        _uiState.update { it.copy(showDataExportDialog = true, exportResult = null) }
    }

    /**
     * Hides data export dialog.
     */
    fun hideDataExportDialog() {
        _uiState.update { it.copy(showDataExportDialog = false, exportResult = null) }
    }

    /**
     * Exports user data.
     *
     * GDPR Article 20: Right to data portability
     */
    fun exportData(
        format: DataPortability.ExportFormat,
        scope: DataPortability.ExportScope = DataPortability.ExportScope.ALL_DATA,
        encrypt: Boolean = false,
        password: CharArray? = null
    ) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(exportInProgress = true, error = null) }

                val result = dataPortability.exportUserData(
                    format = format,
                    scope = scope,
                    encrypt = encrypt,
                    password = password
                )

                _uiState.update {
                    it.copy(
                        exportInProgress = false,
                        exportResult = result
                    )
                }

                if (result.success) {
                    loadPrivacyData() // Refresh to update last export date
                }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        exportInProgress = false,
                        error = "Error exporting data: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Shows data erasure confirmation dialog.
     */
    fun showDataErasureDialog() {
        _uiState.update { it.copy(showDataErasureDialog = true, erasureResult = null) }
    }

    /**
     * Hides data erasure dialog.
     */
    fun hideDataErasureDialog() {
        _uiState.update { it.copy(showDataErasureDialog = false, erasureResult = null) }
    }

    /**
     * Executes complete data erasure (right to be forgotten).
     *
     * **WARNING:** This is IRREVERSIBLE.
     *
     * GDPR Article 17: Right to erasure ("right to be forgotten")
     * DPDP Act Section 9: Right to erasure
     */
    fun executeDataErasure(
        deleteMasterPassword: Boolean = true,
        deleteAuditLogs: Boolean = false,
        masterPassword: CharArray? = null
    ) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(erasureInProgress = true, error = null) }

                val result = dataErasure.executeCompleteErasure(
                    deleteMasterPassword = deleteMasterPassword,
                    deleteAuditLogs = deleteAuditLogs,
                    masterPassword = masterPassword
                )

                _uiState.update {
                    it.copy(
                        erasureInProgress = false,
                        erasureResult = result
                    )
                }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        erasureInProgress = false,
                        error = "Error during data erasure: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Enforces data retention policy (deletes expired credentials).
     */
    fun enforceRetentionPolicy() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(error = null) }

                val result = dataRetentionManager.enforceRetentionPolicy(dryRun = false)

                if (result.deletedCount > 0) {
                    loadPrivacyData() // Refresh to update counts
                }

                _uiState.update {
                    it.copy(
                        error = if (result.errors.isEmpty()) {
                            null
                        } else {
                            "Retention policy executed with ${result.errors.size} errors"
                        }
                    )
                }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = "Error enforcing retention policy: ${e.message}")
                }
            }
        }
    }

    /**
     * Clears error message.
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Refreshes privacy dashboard data.
     */
    fun refresh() {
        loadPrivacyData()
    }
}