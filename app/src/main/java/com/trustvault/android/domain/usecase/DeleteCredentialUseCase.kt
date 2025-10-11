package com.trustvault.android.domain.usecase

import com.trustvault.android.domain.model.Credential
import com.trustvault.android.domain.repository.CredentialRepository
import javax.inject.Inject

class DeleteCredentialUseCase @Inject constructor(
    private val repository: CredentialRepository
) {
    suspend operator fun invoke(credential: Credential) {
        repository.deleteCredential(credential)
    }
}
