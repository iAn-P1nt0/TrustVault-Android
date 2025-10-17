package com.trustvault.android.presentation.viewmodel

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trustvault.android.security.BiometricAuthManager
import com.trustvault.android.security.BiometricStatus
import com.trustvault.android.security.DatabaseKeyManager
import com.trustvault.android.security.PasswordHasher
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
    private val biometricAuthManager: BiometricAuthManager,
    private val databaseKeyManager: DatabaseKeyManager,
    private val biometricPasswordStorage: com.trustvault.android.security.BiometricPasswordStorage
) : ViewModel() {

    private val _uiState = MutableStateFlow(UnlockUiState())
    val uiState: StateFlow<UnlockUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val isBiometricEnabled = preferencesManager.isBiometricEnabled.first()
            val biometricStatus = biometricAuthManager.isBiometricAvailable()
            val shouldShowBiometric = biometricStatus == BiometricStatus.AVAILABLE && isBiometricEnabled

            _uiState.value = _uiState.value.copy(
                isBiometricAvailable = shouldShowBiometric,
                shouldShowBiometricPromptOnLaunch = shouldShowBiometric
            )
        }
    }

    /**
     * Shows biometric prompt for authentication.
     * This is the primary unlock method when biometrics are available.
     *
     * @param activity FragmentActivity context required for BiometricPrompt
     * @param onSuccess Callback when authentication succeeds
     */
    fun showBiometricPrompt(activity: FragmentActivity, onSuccess: () -> Unit) {
        biometricAuthManager.authenticate(
            activity = activity,
            onSuccess = {
                // Biometric authentication succeeded
                unlockWithBiometric(onSuccess)
            },
            onError = { errorMessage ->
                // Authentication error (hardware failure, etc.)
                _uiState.value = _uiState.value.copy(
                    error = errorMessage,
                    shouldShowBiometricPromptOnLaunch = false
                )
            },
            onUserCancelled = {
                // User cancelled - allow password input
                _uiState.value = _uiState.value.copy(
                    shouldShowBiometricPromptOnLaunch = false,
                    error = null
                )
            }
        )
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
                    // The database key is now derived from the authenticated master password
                    databaseKeyManager.initializeDatabase(passwordChars)

                    // Record unlock timestamp for auto-lock timeout
                    preferencesManager.setLastUnlockTimestamp(System.currentTimeMillis())

                    _uiState.value = _uiState.value.copy(isLoading = false)
                    onSuccess()
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
                // SECURITY CONTROL: Clear password from memory
                passwordChars.secureWipe()
            }
        }
    }

    /**
     * Unlocks the app using biometric authentication.
     *
     * SECURITY FIX: This method now properly initializes the database
     * with the stored master password after successful biometric authentication.
     *
     * The master password is stored encrypted in EncryptedSharedPreferences
     * when the user enables biometric authentication.
     */
    fun unlockWithBiometric(onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)

                // Retrieve encrypted master password
                val storedPassword = biometricPasswordStorage.getPassword()

                if (storedPassword == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Biometric password not found. Please unlock with password."
                    )
                    return@launch
                }

                // SECURITY: Convert password to CharArray for secure memory handling
                val passwordChars = storedPassword.toSecureCharArray()

                try {
                    // SECURITY: Initialize database with derived encryption key
                    // The database key is derived from the stored master password
                    databaseKeyManager.initializeDatabase(passwordChars)

                    // Record unlock timestamp for auto-lock timeout
                    preferencesManager.setLastUnlockTimestamp(System.currentTimeMillis())

                    _uiState.value = _uiState.value.copy(isLoading = false)
                    onSuccess()
                } finally {
                    // SECURITY CONTROL: Clear password from memory
                    passwordChars.secureWipe()
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Biometric unlock failed: ${e.message}"
                )
            }
        }
    }
}

data class UnlockUiState(
    val password: String = "",
    val isLoading: Boolean = false,
    val isBiometricAvailable: Boolean = false,
    val shouldShowBiometricPromptOnLaunch: Boolean = false,
    val error: String? = null
)
