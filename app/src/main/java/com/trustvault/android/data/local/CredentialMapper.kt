package com.trustvault.android.data.local

import com.trustvault.android.data.local.entity.CredentialEntity
import com.trustvault.android.domain.model.Credential
import com.trustvault.android.domain.model.CredentialCategory
import com.trustvault.android.security.FieldEncryptor
import java.util.Date
import javax.inject.Inject

/**
 * Maps between domain models and encrypted database entities.
 */
class CredentialMapper @Inject constructor(
    private val fieldEncryptor: FieldEncryptor
) {

    /**
     * Converts a domain Credential to an encrypted CredentialEntity.
     */
    fun toEntity(credential: Credential): CredentialEntity {
        return CredentialEntity(
            id = credential.id,
            title = credential.title, // Plain text for search
            username = fieldEncryptor.encrypt(credential.username),
            password = fieldEncryptor.encrypt(credential.password),
            website = credential.website, // Plain text for search
            notes = fieldEncryptor.encrypt(credential.notes),
            category = credential.category.name,
            packageName = credential.packageName, // Plain text for autofill matching
            createdAt = credential.createdAt.time,
            updatedAt = credential.updatedAt.time
        )
    }

    /**
     * Converts an encrypted CredentialEntity to a domain Credential.
     */
    fun toDomain(entity: CredentialEntity): Credential {
        return Credential(
            id = entity.id,
            title = entity.title,
            username = fieldEncryptor.decrypt(entity.username),
            password = fieldEncryptor.decrypt(entity.password),
            website = entity.website,
            notes = fieldEncryptor.decrypt(entity.notes),
            category = CredentialCategory.fromString(entity.category),
            packageName = entity.packageName, // Plain text for autofill matching
            createdAt = Date(entity.createdAt),
            updatedAt = Date(entity.updatedAt)
        )
    }

    /**
     * Converts a list of encrypted entities to domain models.
     */
    fun toDomainList(entities: List<CredentialEntity>): List<Credential> {
        return entities.map { toDomain(it) }
    }
}
