package com.trustvault.android.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trustvault.android.domain.model.Credential
import com.trustvault.android.domain.model.CredentialCategory
import com.trustvault.android.domain.repository.CredentialRepository
import com.trustvault.android.domain.usecase.SaveCredentialUseCase
import com.trustvault.android.security.ocr.OcrResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddEditCredentialViewModel @Inject constructor(
    private val saveCredentialUseCase: SaveCredentialUseCase,
    private val repository: CredentialRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val credentialId: Long? = savedStateHandle.get<String>("credentialId")?.toLongOrNull()

    private val _uiState = MutableStateFlow(AddEditCredentialUiState())
    val uiState: StateFlow<AddEditCredentialUiState> = _uiState.asStateFlow()

    init {
        credentialId?.let { loadCredential(it) }
    }

    private fun loadCredential(id: Long) {
        viewModelScope.launch {
            val credential = repository.getCredentialById(id)
            credential?.let {
                _uiState.value = _uiState.value.copy(
                    credentialId = it.id,
                    title = it.title,
                    username = it.username,
                    password = it.password,
                    website = it.website,
                    notes = it.notes,
                    category = it.category,
                    otpSecret = it.otpSecret ?: "",
                    isEditing = true
                )
            }
        }
    }

    fun onTitleChange(title: String) {
        _uiState.value = _uiState.value.copy(title = title, error = null)
    }

    fun onUsernameChange(username: String) {
        _uiState.value = _uiState.value.copy(username = username)
    }

    fun onPasswordChange(password: String) {
        _uiState.value = _uiState.value.copy(password = password)
    }

    fun onWebsiteChange(website: String) {
        _uiState.value = _uiState.value.copy(website = website)
    }

    fun onNotesChange(notes: String) {
        _uiState.value = _uiState.value.copy(notes = notes)
    }

    fun onCategoryChange(category: CredentialCategory) {
        _uiState.value = _uiState.value.copy(category = category)
    }

    fun onOtpSecretChange(otpSecret: String) {
        _uiState.value = _uiState.value.copy(otpSecret = otpSecret, error = null)
    }

    /**
     * Parses a TOTP URI from QR code and populates TOTP fields.
     *
     * @param uri otpauth:// URI from QR code
     * @return true if parsed successfully, false otherwise
     */
    fun parseTotpUri(uri: String): Boolean {
        return try {
            // Note: TotpGenerator must be injected for this to work in production
            // For now, we'll parse manually or return false
            // In production, inject TotpGenerator and use: totpGenerator.parseUri(uri)
            false
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(error = "Invalid TOTP URI: ${e.message}")
            false
        }
    }

    /**
     * Populate fields from OCR result.
     *
     * SECURITY CONTROL: Clears OcrResult after population to minimize
     * plaintext credential lifetime in memory.
     *
     * @param ocrResult Extracted credentials from OCR processing
     */
    fun populateFromOcrResult(ocrResult: OcrResult) {
        try {
            // Convert CharArray to String for UI state
            val username = ocrResult.getUsername()?.let { String(it) } ?: _uiState.value.username
            val password = ocrResult.getPassword()?.let { String(it) } ?: _uiState.value.password
            val website = ocrResult.getWebsite()?.let { String(it) } ?: _uiState.value.website

            // Update UI state with extracted data
            _uiState.value = _uiState.value.copy(
                username = username,
                password = password,
                website = website
            )

        } finally {
            // SECURITY CONTROL: Clear OCR result immediately after population
            ocrResult.clear()
        }
    }

    fun saveCredential(onSuccess: () -> Unit) {
        val state = _uiState.value

        if (state.title.isBlank()) {
            _uiState.value = state.copy(error = "Title is required")
            return
        }

        viewModelScope.launch {
            try {
                _uiState.value = state.copy(isLoading = true)
                val credential = Credential(
                    id = state.credentialId,
                    title = state.title,
                    username = state.username,
                    password = state.password,
                    website = state.website,
                    notes = state.notes,
                    category = state.category,
                    otpSecret = state.otpSecret.takeIf { it.isNotBlank() }
                )
                saveCredentialUseCase(credential)
                _uiState.value = state.copy(isLoading = false)
                onSuccess()
            } catch (e: Exception) {
                _uiState.value = state.copy(
                    isLoading = false,
                    error = "Failed to save credential: ${e.message}"
                )
            }
        }
    }
}

data class AddEditCredentialUiState(
    val credentialId: Long = 0,
    val title: String = "",
    val username: String = "",
    val password: String = "",
    val website: String = "",
    val notes: String = "",
    val category: CredentialCategory = CredentialCategory.LOGIN,
    val otpSecret: String = "", // Base32-encoded TOTP secret from QR code
    val isEditing: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val totpIssuer: String = "", // Display only: issuer from otpauth:// URI
    val totpAccount: String = "" // Display only: account from otpauth:// URI
)
