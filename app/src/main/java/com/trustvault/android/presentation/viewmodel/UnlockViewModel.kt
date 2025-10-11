package com.trustvault.android.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trustvault.android.security.BiometricAuthManager
import com.trustvault.android.security.BiometricStatus
import com.trustvault.android.security.PasswordHasher
import com.trustvault.android.util.PreferencesManager
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
    private val biometricAuthManager: BiometricAuthManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(UnlockUiState())
    val uiState: StateFlow<UnlockUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val isBiometricEnabled = preferencesManager.isBiometricEnabled.first()
            val biometricStatus = biometricAuthManager.isBiometricAvailable()
            _uiState.value = _uiState.value.copy(
                isBiometricAvailable = biometricStatus == BiometricStatus.AVAILABLE && isBiometricEnabled
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

                val isValid = passwordHasher.verifyPassword(_uiState.value.password, storedHash)
                
                if (isValid) {
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
            }
        }
    }

    fun unlockWithBiometric(onSuccess: () -> Unit) {
        onSuccess()
    }
}

data class UnlockUiState(
    val password: String = "",
    val isLoading: Boolean = false,
    val isBiometricAvailable: Boolean = false,
    val error: String? = null
)
