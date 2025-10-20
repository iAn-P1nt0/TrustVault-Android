package com.trustvault.android.presentation.viewmodel

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trustvault.android.security.biometric.BiometricAvailability
import com.trustvault.android.security.biometric.BiometricVaultUnlocker
import com.trustvault.android.security.biometric.EnhancedBiometricAuthManager
import com.trustvault.android.util.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * BiometricViewModel - Manages UI state for biometric authentication features.
 *
 * **Responsibilities:**
 * - Check biometric availability
 * - Manage biometric setup dialog state
 * - Handle biometric enable/disable
 * - Track biometric unlock errors
 */
@HiltViewModel
class BiometricViewModel @Inject constructor(
    private val biometricVaultUnlocker: BiometricVaultUnlocker,
    private val biometricAuthManager: EnhancedBiometricAuthManager,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(BiometricUiState())
    val uiState: StateFlow<BiometricUiState> = _uiState.asStateFlow()

    init {
        checkBiometricAvailability()
    }

    /**
     * Checks biometric availability and updates UI state.
     */
    fun checkBiometricAvailability() {
        viewModelScope.launch {
            val availability = biometricAuthManager.checkBiometricAvailability()
            val isEnabled = preferencesManager.isBiometricEnabled.first()
            val isAvailable = biometricVaultUnlocker.isBiometricUnlockAvailable()

            _uiState.value = _uiState.value.copy(
                biometricAvailability = availability,
                isBiometricEnabled = isEnabled,
                isBiometricUnlockAvailable = isAvailable
            )
        }
    }

    /**
     * Shows the biometric setup dialog.
     */
    fun showSetupDialog() {
        _uiState.value = _uiState.value.copy(showSetupDialog = true)
    }

    /**
     * Hides the biometric setup dialog.
     */
    fun hideSetupDialog() {
        _uiState.value = _uiState.value.copy(showSetupDialog = false)
    }

    /**
     * Sets up biometric unlock with the provided master password.
     *
     * @param activity FragmentActivity for biometric prompt
     * @param masterPassword User's master password (will NOT be wiped)
     * @param onSuccess Callback on successful setup
     */
    fun setupBiometricUnlock(
        activity: FragmentActivity,
        masterPassword: CharArray,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            biometricVaultUnlocker.setupBiometricUnlock(
                activity = activity,
                masterPassword = masterPassword,
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isBiometricEnabled = true,
                        showSetupDialog = false,
                        successMessage = "Biometric unlock enabled successfully"
                    )
                    checkBiometricAvailability()
                    onSuccess()
                },
                onError = { errorMsg ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = errorMsg
                    )
                },
                onUserCancelled = {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        showSetupDialog = false
                    )
                }
            )
        }
    }

    /**
     * Disables biometric unlock.
     */
    fun disableBiometricUnlock() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            try {
                biometricVaultUnlocker.disableBiometricUnlock()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isBiometricEnabled = false,
                    isBiometricUnlockAvailable = false,
                    successMessage = "Biometric unlock disabled"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to disable biometric unlock: ${e.message}"
                )
            }
        }
    }

    /**
     * Clears error and success messages.
     */
    fun clearMessages() {
        _uiState.value = _uiState.value.copy(
            errorMessage = null,
            successMessage = null
        )
    }

    /**
     * Sets the "never ask again" preference for biometric setup.
     */
    fun setNeverAskAgain() {
        viewModelScope.launch {
            preferencesManager.setBiometricNeverAskAgain(true)
            _uiState.value = _uiState.value.copy(showSetupDialog = false)
        }
    }
}

/**
 * UI state for biometric features.
 */
data class BiometricUiState(
    val biometricAvailability: BiometricAvailability = BiometricAvailability.Unknown,
    val isBiometricEnabled: Boolean = false,
    val isBiometricUnlockAvailable: Boolean = false,
    val showSetupDialog: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

