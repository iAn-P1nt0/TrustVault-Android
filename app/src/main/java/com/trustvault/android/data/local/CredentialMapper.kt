package com.trustvault.android.data.local

import com.trustvault.android.data.local.entity.CredentialEntity
import com.trustvault.android.domain.model.Credential
import com.trustvault.android.domain.model.CredentialCategory
import com.trustvault.android.security.FieldEncryptor
import javax.inject.Inject

/**
 * Maps between domain models and encrypted database entities.
 *
 * **Serialization Strategy:**
 * - Sensitive fields (username, password, notes, otpSecret): AES-256-GCM encrypted
 * - URL matching fields (website, packageName, allowedDomains): Plain text (used for search/matching)
 * - allowedDomains: Serialized as JSON array for flexibility
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
            otpSecret = credential.otpSecret?.let { fieldEncryptor.encrypt(it) }, // Encrypt TOTP secret
            allowedDomains = serializeAllowedDomains(credential.allowedDomains), // JSON array
            createdAt = credential.createdAt,
            updatedAt = credential.updatedAt
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
            otpSecret = entity.otpSecret?.let { fieldEncryptor.decrypt(it) }, // Decrypt TOTP secret
            allowedDomains = deserializeAllowedDomains(entity.allowedDomains), // Parse JSON array
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    /**
     * Converts a list of encrypted entities to domain models.
     */
    fun toDomainList(entities: List<CredentialEntity>): List<Credential> {
        return entities.map { toDomain(it) }
    }

    /**
     * Serializes allowed domains list to JSON array string.
     * Used for storing in database.
     *
     * Simple JSON format: ["domain1.com","domain2.com","*.domain3.com"]
     */
    private fun serializeAllowedDomains(domains: List<String>): String {
        if (domains.isEmpty()) return "[]"

        return try {
            val jsonParts = domains.map { domain ->
                "\"${domain.replace("\"", "\\\"")}\""
            }
            "[${jsonParts.joinToString(",")}]"
        } catch (e: Exception) {
            "[]" // Fallback to empty array on serialization error
        }
    }

    /**
     * Deserializes JSON array string to allowed domains list.
     * Used for loading from database.
     *
     * Simple JSON format: ["domain1.com","domain2.com","*.domain3.com"]
     */
    private fun deserializeAllowedDomains(jsonString: String): List<String> {
        return try {
            if (jsonString.isEmpty() || jsonString == "[]") return emptyList()

            // Remove [ and ]
            val trimmed = jsonString.trim().removePrefix("[").removeSuffix("]")
            if (trimmed.isEmpty()) return emptyList()

            // Split by comma but respect quoted strings
            val domains = mutableListOf<String>()
            var current = StringBuilder()
            var inQuotes = false
            var escaped = false

            for (char in trimmed) {
                when {
                    escaped -> {
                        current.append(char)
                        escaped = false
                    }
                    char == '\\' && inQuotes -> {
                        escaped = true
                    }
                    char == '\"' -> {
                        inQuotes = !inQuotes
                    }
                    char == ',' && !inQuotes -> {
                        val domain = current.toString().trim().trim('"')
                        if (domain.isNotEmpty()) {
                            domains.add(domain)
                        }
                        current = StringBuilder()
                    }
                    else -> current.append(char)
                }
            }

            // Add last domain
            val domain = current.toString().trim().trim('"')
            if (domain.isNotEmpty()) {
                domains.add(domain)
            }

            domains
        } catch (e: Exception) {
            emptyList() // Fallback to empty list on deserialization error
        }
    }
}
