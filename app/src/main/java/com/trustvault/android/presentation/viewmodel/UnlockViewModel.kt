package com.trustvault.android.presentation.viewmodel

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trustvault.android.security.DatabaseKeyManager
import com.trustvault.android.security.PasswordHasher
import com.trustvault.android.security.biometric.BiometricVaultUnlocker
import com.trustvault.android.security.zeroknowledge.MasterKeyHierarchy
import com.trustvault.android.util.PreferencesManager
import com.trustvault.android.util.secureWipe
import com.trustvault.android.util.toSecureCharArray
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UnlockViewModel @Inject constructor(
    private val passwordHasher: PasswordHasher,
    private val preferencesManager: PreferencesManager,
    private val databaseKeyManager: DatabaseKeyManager,
    private val biometricVaultUnlocker: BiometricVaultUnlocker,
    private val masterKeyHierarchy: MasterKeyHierarchy
) : ViewModel() {

    private val _uiState = MutableStateFlow(UnlockUiState())
    val uiState: StateFlow<UnlockUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val isAvailable = biometricVaultUnlocker.isBiometricUnlockAvailable()

            _uiState.value = _uiState.value.copy(
                isBiometricAvailable = isAvailable,
                shouldShowBiometricPromptOnLaunch = isAvailable
            )
        }
    }

    /**
     * Shows biometric prompt for authentication using BiometricVaultUnlocker.
     * This is the primary unlock method when biometrics are available.
     *
     * @param activity FragmentActivity context required for BiometricPrompt
     * @param onSuccess Callback when authentication succeeds
     */
    fun showBiometricPrompt(activity: FragmentActivity, onSuccess: () -> Unit) {
        viewModelScope.launch {
            biometricVaultUnlocker.unlockWithBiometric(
                activity = activity,
                onSuccess = { mek ->
                    viewModelScope.launch {
                        try {
                            // Initialize database with MEK
                            databaseKeyManager.initializeWithMEK(mek)

                            // Record unlock timestamp for auto-lock timeout
                            preferencesManager.setLastUnlockTimestamp(System.currentTimeMillis())

                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                shouldShowBiometricPromptOnLaunch = false
                            )
                            onSuccess()
                        } finally {
                            // SECURITY CRITICAL: Wipe MEK from memory
                            mek.secureWipe()
                        }
                    }
                },
                onError = { errorMessage ->
                    // Authentication error (hardware failure, etc.)
                    _uiState.value = _uiState.value.copy(
                        error = errorMessage,
                        shouldShowBiometricPromptOnLaunch = false,
                        isLoading = false
                    )
                },
                onUserCancelled = {
                    // User cancelled - allow password input
                    _uiState.value = _uiState.value.copy(
                        shouldShowBiometricPromptOnLaunch = false,
                        error = null,
                        isLoading = false
                    )
                },
                onBiometricInvalidated = {
                    // Biometric key invalidated - show alert
                    _uiState.value = _uiState.value.copy(
                        error = "Your biometrics have changed. Biometric unlock has been disabled for security. Please re-enable in Settings.",
                        shouldShowBiometricPromptOnLaunch = false,
                        isBiometricAvailable = false,
                        isLoading = false,
                        showBiometricInvalidatedDialog = true
                    )
                }
            )
        }
    }

    fun onPasswordChange(password: String) {
        _uiState.value = _uiState.value.copy(
            password = password,
            error = null
        )
    }

    fun unlock(onSuccess: () -> Unit) {
        viewModelScope.launch {
            // SECURITY: Convert password to CharArray for secure memory handling
            val passwordChars = _uiState.value.password.toSecureCharArray()

            try {
                _uiState.value = _uiState.value.copy(isLoading = true)
                val storedHash = preferencesManager.masterPasswordHash.first()

                if (storedHash == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Master password not set"
                    )
                    return@launch
                }

                val isValid = passwordHasher.verifyPassword(passwordChars, storedHash)

                if (isValid) {
                    // SECURITY: Initialize database with derived encryption key
                    val mek = masterKeyHierarchy.deriveMasterEncryptionKey(passwordChars)
                    try {
                        databaseKeyManager.initializeWithMEK(mek)

                        // Record unlock timestamp for auto-lock timeout
                        preferencesManager.setLastUnlockTimestamp(System.currentTimeMillis())

                        // Check if we should offer biometric setup
                        val isBiometricEnabled = preferencesManager.isBiometricEnabled.first()
                        val neverAskAgain = preferencesManager.biometricNeverAskAgain.first()

                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            shouldOfferBiometricSetup = !isBiometricEnabled && !neverAskAgain,
                            lastMasterPassword = passwordChars // Store for biometric setup
                        )

                        onSuccess()
                    } finally {
                        // SECURITY: Wipe MEK from memory
                        mek.secureWipe()
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Wrong password"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Authentication failed: ${e.message}"
                )
            } finally {
                // Note: Don't wipe passwordChars here if we need it for biometric setup
                if (!_uiState.value.shouldOfferBiometricSetup) {
                    passwordChars.secureWipe()
                }
            }
        }
    }

    fun dismissBiometricInvalidatedDialog() {
        _uiState.value = _uiState.value.copy(showBiometricInvalidatedDialog = false)
    }

    fun clearLastMasterPassword() {
        _uiState.value.lastMasterPassword?.secureWipe()
        _uiState.value = _uiState.value.copy(lastMasterPassword = null)
    }
}

data class UnlockUiState(
    val password: String = "",
    val isLoading: Boolean = false,
    val isBiometricAvailable: Boolean = false,
    val shouldShowBiometricPromptOnLaunch: Boolean = false,
    val shouldOfferBiometricSetup: Boolean = false,
    val showBiometricInvalidatedDialog: Boolean = false,
    val error: String? = null,
    val lastMasterPassword: CharArray? = null
)
