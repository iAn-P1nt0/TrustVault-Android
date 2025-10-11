package com.trustvault.android.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trustvault.android.security.DatabaseKeyManager
import com.trustvault.android.security.PasswordHasher
import com.trustvault.android.security.PasswordStrength
import com.trustvault.android.util.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MasterPasswordViewModel @Inject constructor(
    private val passwordHasher: PasswordHasher,
    private val preferencesManager: PreferencesManager,
    private val databaseKeyManager: DatabaseKeyManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(MasterPasswordUiState())
    val uiState: StateFlow<MasterPasswordUiState> = _uiState.asStateFlow()

    fun onPasswordChange(password: String) {
        _uiState.value = _uiState.value.copy(
            password = password,
            passwordStrength = passwordHasher.evaluatePasswordStrength(password),
            error = null
        )
    }

    fun onConfirmPasswordChange(confirmPassword: String) {
        _uiState.value = _uiState.value.copy(
            confirmPassword = confirmPassword,
            error = null
        )
    }

    fun createMasterPassword(onSuccess: () -> Unit) {
        val state = _uiState.value

        if (state.password.length < 8) {
            _uiState.value = state.copy(error = "Password must be at least 8 characters")
            return
        }

        if (state.password != state.confirmPassword) {
            _uiState.value = state.copy(error = "Passwords don't match")
            return
        }

        viewModelScope.launch {
            try {
                _uiState.value = state.copy(isLoading = true)

                // Hash and store master password
                val hash = passwordHasher.hashPassword(state.password)
                preferencesManager.setMasterPasswordHash(hash)

                // SECURITY: Initialize database with derived encryption key
                // The database key is now derived from the master password
                // instead of being hardcoded
                databaseKeyManager.initializeDatabase(state.password)

                _uiState.value = state.copy(isLoading = false)
                onSuccess()
            } catch (e: Exception) {
                _uiState.value = state.copy(
                    isLoading = false,
                    error = "Failed to create master password: ${e.message}"
                )
            }
        }
    }
}

data class MasterPasswordUiState(
    val password: String = "",
    val confirmPassword: String = "",
    val passwordStrength: PasswordStrength = PasswordStrength.WEAK,
    val isLoading: Boolean = false,
    val error: String? = null
)
