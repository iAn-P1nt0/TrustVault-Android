package com.trustvault.android.domain.repository

import com.trustvault.android.domain.model.Credential
import com.trustvault.android.domain.model.CredentialCategory
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for credential operations.
 */
interface CredentialRepository {
    
    fun getAllCredentials(): Flow<List<Credential>>
    
    fun getCredentialsByCategory(category: CredentialCategory): Flow<List<Credential>>
    
    fun searchCredentials(query: String): Flow<List<Credential>>
    
    suspend fun getCredentialById(id: Long): Credential?
    
    suspend fun insertCredential(credential: Credential): Long
    
    suspend fun updateCredential(credential: Credential)
    
    suspend fun deleteCredential(credential: Credential)
    
    suspend fun deleteAllCredentials()
}
