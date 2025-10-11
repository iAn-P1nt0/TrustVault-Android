package com.trustvault.android.presentation.viewmodel

import androidx.lifecycle.ViewModel
import com.trustvault.android.util.PasswordGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class PasswordGeneratorViewModel @Inject constructor() : ViewModel() {

    private val passwordGenerator = PasswordGenerator()

    private val _uiState = MutableStateFlow(PasswordGeneratorUiState())
    val uiState: StateFlow<PasswordGeneratorUiState> = _uiState.asStateFlow()

    init {
        generatePassword()
    }

    fun onLengthChange(length: Int) {
        _uiState.value = _uiState.value.copy(length = length)
        generatePassword()
    }

    fun onIncludeUppercaseChange(include: Boolean) {
        _uiState.value = _uiState.value.copy(includeUppercase = include)
        generatePassword()
    }

    fun onIncludeLowercaseChange(include: Boolean) {
        _uiState.value = _uiState.value.copy(includeLowercase = include)
        generatePassword()
    }

    fun onIncludeNumbersChange(include: Boolean) {
        _uiState.value = _uiState.value.copy(includeNumbers = include)
        generatePassword()
    }

    fun onIncludeSymbolsChange(include: Boolean) {
        _uiState.value = _uiState.value.copy(includeSymbols = include)
        generatePassword()
    }

    fun generatePassword() {
        val state = _uiState.value
        
        if (!state.includeUppercase && !state.includeLowercase && 
            !state.includeNumbers && !state.includeSymbols) {
            _uiState.value = state.copy(error = "Select at least one character type")
            return
        }

        try {
            val password = passwordGenerator.generate(
                length = state.length,
                includeUppercase = state.includeUppercase,
                includeLowercase = state.includeLowercase,
                includeNumbers = state.includeNumbers,
                includeSymbols = state.includeSymbols
            )
            _uiState.value = state.copy(generatedPassword = password, error = null)
        } catch (e: Exception) {
            _uiState.value = state.copy(error = e.message)
        }
    }
}

data class PasswordGeneratorUiState(
    val generatedPassword: String = "",
    val length: Int = 16,
    val includeUppercase: Boolean = true,
    val includeLowercase: Boolean = true,
    val includeNumbers: Boolean = true,
    val includeSymbols: Boolean = true,
    val error: String? = null
)
