package com.trustvault.android.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trustvault.android.domain.model.Credential
import com.trustvault.android.domain.model.CredentialCategory
import com.trustvault.android.domain.usecase.DeleteCredentialUseCase
import com.trustvault.android.domain.usecase.GetAllCredentialsUseCase
import com.trustvault.android.domain.usecase.SearchCredentialsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CredentialListViewModel @Inject constructor(
    private val getAllCredentialsUseCase: GetAllCredentialsUseCase,
    private val searchCredentialsUseCase: SearchCredentialsUseCase,
    private val deleteCredentialUseCase: DeleteCredentialUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(CredentialListUiState())
    val uiState: StateFlow<CredentialListUiState> = _uiState.asStateFlow()

    init {
        loadCredentials()
    }

    private fun loadCredentials() {
        getAllCredentialsUseCase()
            .onEach { credentials ->
                _uiState.value = _uiState.value.copy(
                    credentials = credentials,
                    filteredCredentials = filterCredentials(credentials)
                )
            }
            .launchIn(viewModelScope)
    }

    fun onSearchQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        
        if (query.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                filteredCredentials = filterCredentials(_uiState.value.credentials)
            )
        } else {
            searchCredentialsUseCase(query)
                .onEach { credentials ->
                    _uiState.value = _uiState.value.copy(
                        filteredCredentials = filterCredentials(credentials)
                    )
                }
                .launchIn(viewModelScope)
        }
    }

    fun onCategoryChange(category: CredentialCategory?) {
        _uiState.value = _uiState.value.copy(
            selectedCategory = category,
            filteredCredentials = filterCredentials(_uiState.value.credentials)
        )
    }

    private fun filterCredentials(credentials: List<Credential>): List<Credential> {
        val category = _uiState.value.selectedCategory
        return if (category == null) {
            credentials
        } else {
            credentials.filter { it.category == category }
        }
    }

    fun deleteCredential(credential: Credential) {
        viewModelScope.launch {
            deleteCredentialUseCase(credential)
        }
    }
}

data class CredentialListUiState(
    val credentials: List<Credential> = emptyList(),
    val filteredCredentials: List<Credential> = emptyList(),
    val searchQuery: String = "",
    val selectedCategory: CredentialCategory? = null
)
