package com.trustvault.android.domain.usecase

import com.trustvault.android.domain.model.Credential
import com.trustvault.android.domain.repository.CredentialRepository
import java.util.Date
import javax.inject.Inject

class SaveCredentialUseCase @Inject constructor(
    private val repository: CredentialRepository
) {
    suspend operator fun invoke(credential: Credential): Long {
        val now = Date()
        val updatedCredential = if (credential.id == 0L) {
            credential.copy(createdAt = now, updatedAt = now)
        } else {
            credential.copy(updatedAt = now)
        }
        
        return if (updatedCredential.id == 0L) {
            repository.insertCredential(updatedCredential)
        } else {
            repository.updateCredential(updatedCredential)
            updatedCredential.id
        }
    }
}
