package com.trustvault.android.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trustvault.android.domain.model.Credential
import com.trustvault.android.domain.model.CredentialCategory
import com.trustvault.android.domain.repository.CredentialRepository
import com.trustvault.android.domain.usecase.SaveCredentialUseCase
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
                    category = state.category
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
    val isEditing: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)
