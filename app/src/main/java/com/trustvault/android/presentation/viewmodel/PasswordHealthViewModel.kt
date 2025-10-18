package com.trustvault.android.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trustvault.android.security.BreachChecker
import com.trustvault.android.security.PasswordHealthAnalyzer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for password health analysis and breach checking.
 */
@HiltViewModel
class PasswordHealthViewModel @Inject constructor(
    private val healthAnalyzer: PasswordHealthAnalyzer,
    private val breachChecker: BreachChecker
) : ViewModel() {

    sealed class UiState {
        object Idle : UiState()
        object Analyzing : UiState()
        data class HealthReady(
            val health: PasswordHealthAnalyzer.PasswordHealth,
            val isBreached: Boolean = false,
            val breachCount: Int = 0,
            val breachCached: Boolean = false
        ) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _isBreachCheckOptedIn = MutableStateFlow(false)
    val isBreachCheckOptedIn: StateFlow<Boolean> = _isBreachCheckOptedIn.asStateFlow()

    /**
     * Analyzes password health and optionally checks for breaches.
     */
    fun analyzePassword(
        password: String,
        username: String = "",
        email: String = ""
    ) {
        viewModelScope.launch {
            _uiState.value = UiState.Analyzing

            try {
                // Analyze password health locally
                val health = healthAnalyzer.analyzePassword(password, username, email)

                // Check for breaches if opted in
                val breachResult = if (_isBreachCheckOptedIn.value) {
                    breachChecker.checkPasswordBreach(password, isOptedIn = true)
                } else {
                    BreachChecker.BreachResult(isBreached = false)
                }

                _uiState.value = UiState.HealthReady(
                    health = health,
                    isBreached = breachResult.isBreached,
                    breachCount = breachResult.breachCount,
                    breachCached = breachResult.isCached
                )
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Analysis failed: ${e.message}")
            }
        }
    }

    /**
     * Sets whether user has opted in for breach checks.
     */
    fun setBreachCheckOptedIn(optedIn: Boolean) {
        _isBreachCheckOptedIn.value = optedIn
    }

    /**
     * Clears breach check cache.
     */
    fun clearBreachCache() {
        viewModelScope.launch {
            try {
                breachChecker.clearCache()
            } catch (e: Exception) {
                // Silently fail
            }
        }
    }

    fun resetUiState() {
        _uiState.value = UiState.Idle
    }
}
