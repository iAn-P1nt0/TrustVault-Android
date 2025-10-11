package com.trustvault.android.domain.usecase

import com.trustvault.android.domain.model.Credential
import com.trustvault.android.domain.repository.CredentialRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SearchCredentialsUseCase @Inject constructor(
    private val repository: CredentialRepository
) {
    operator fun invoke(query: String): Flow<List<Credential>> {
        return repository.searchCredentials(query)
    }
}
