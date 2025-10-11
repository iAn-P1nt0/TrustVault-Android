package com.trustvault.android.data.repository

import com.trustvault.android.data.local.CredentialMapper
import com.trustvault.android.data.local.dao.CredentialDao
import com.trustvault.android.domain.model.Credential
import com.trustvault.android.domain.model.CredentialCategory
import com.trustvault.android.domain.repository.CredentialRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of CredentialRepository.
 * Handles encryption/decryption of credentials.
 */
@Singleton
class CredentialRepositoryImpl @Inject constructor(
    private val credentialDao: CredentialDao,
    private val mapper: CredentialMapper
) : CredentialRepository {

    override fun getAllCredentials(): Flow<List<Credential>> {
        return credentialDao.getAllCredentials()
            .map { entities -> mapper.toDomainList(entities) }
    }

    override fun getCredentialsByCategory(category: CredentialCategory): Flow<List<Credential>> {
        return credentialDao.getCredentialsByCategory(category.name)
            .map { entities -> mapper.toDomainList(entities) }
    }

    override fun searchCredentials(query: String): Flow<List<Credential>> {
        return credentialDao.searchCredentials(query)
            .map { entities -> mapper.toDomainList(entities) }
    }

    override suspend fun getCredentialById(id: Long): Credential? {
        return credentialDao.getCredentialById(id)?.let { mapper.toDomain(it) }
    }

    override suspend fun insertCredential(credential: Credential): Long {
        val entity = mapper.toEntity(credential)
        return credentialDao.insertCredential(entity)
    }

    override suspend fun updateCredential(credential: Credential) {
        val entity = mapper.toEntity(credential)
        credentialDao.updateCredential(entity)
    }

    override suspend fun deleteCredential(credential: Credential) {
        val entity = mapper.toEntity(credential)
        credentialDao.deleteCredential(entity)
    }

    override suspend fun deleteAllCredentials() {
        credentialDao.deleteAllCredentials()
    }
}
