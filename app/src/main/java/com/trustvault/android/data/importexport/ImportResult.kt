package com.trustvault.android.data.importexport

import com.trustvault.android.domain.model.Credential

/**
 * Result of an import operation.
 *
 * Can represent successful import, validation errors, or warnings.
 * Supports partial success with warnings when some credentials are valid.
 */
sealed class ImportResult {
    data class Success(
        val importedCredentials: List<Credential>,
        val warnings: List<String> = emptyList()
    ) : ImportResult()

    data class ValidationError(
        val message: String,
        val fieldErrors: List<FieldError> = emptyList()
    ) : ImportResult()

    data class Error(
        val message: String,
        val exception: Exception? = null
    ) : ImportResult()

    /**
     * Field-level validation error.
     */
    data class FieldError(
        val rowNumber: Int,
        val fieldName: String,
        val value: String,
        val reason: String
    )
}

/**
 * Preview of import data before committing.
 * Allows user to review and resolve conflicts.
 */
data class ImportPreview(
    val credentials: List<Credential>,
    val conflicts: List<ImportConflict> = emptyList(),
    val warnings: List<String> = emptyList(),
    val sourceFormat: String // "CSV", "KDBX", etc.
) {
    val totalCount: Int get() = credentials.size
    val conflictCount: Int get() = conflicts.size
    val warningCount: Int get() = warnings.size
}

/**
 * Represents a conflict when importing credentials.
 */
data class ImportConflict(
    val existingCredential: Credential,
    val importedCredential: Credential,
    val conflictType: ConflictType,
    val userResolution: ConflictResolution = ConflictResolution.SKIP
) {
    enum class ConflictType {
        DUPLICATE_USERNAME_WEBSITE,  // Same username + website combination
        DUPLICATE_TITLE,              // Same title
        INVALID_FIELD                // Missing/invalid required field
    }

    enum class ConflictResolution {
        SKIP,      // Don't import this credential
        OVERWRITE, // Replace existing with imported
        KEEP_BOTH  // Import as separate credential with modified title
    }
}

/**
 * CSV field mapping for flexible import.
 * Maps CSV column indices to credential fields.
 */
data class CsvFieldMapping(
    val titleIndex: Int? = null,
    val usernameIndex: Int? = null,
    val passwordIndex: Int? = null,
    val websiteIndex: Int? = null,
    val categoryIndex: Int? = null,
    val notesIndex: Int? = null,
    val otpSecretIndex: Int? = null,
    val packageNameIndex: Int? = null,
    val skipFirstRow: Boolean = true
) {
    fun hasRequiredFields(): Boolean {
        return usernameIndex != null && passwordIndex != null
    }

    fun validate(): String? {
        if (!hasRequiredFields()) {
            return "Username and password fields are required for import"
        }
        return null
    }
}
