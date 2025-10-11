package com.trustvault.android.domain.usecase

import com.trustvault.android.domain.model.Credential
import com.trustvault.android.domain.repository.CredentialRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetAllCredentialsUseCase @Inject constructor(
    private val repository: CredentialRepository
) {
    operator fun invoke(): Flow<List<Credential>> {
        return repository.getAllCredentials()
    }
}
