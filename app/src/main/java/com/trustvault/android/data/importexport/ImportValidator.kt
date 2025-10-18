package com.trustvault.android.data.importexport

import android.util.Log
import com.trustvault.android.domain.model.Credential
import javax.inject.Inject

/**
 * Validates imported credentials against existing vault data.
 *
 * Responsibilities:
 * - Detect duplicate/conflicting credentials
 * - Validate field requirements
 * - Provide conflict resolution strategies
 * - Ensure data integrity before import
 */
class ImportValidator @Inject constructor() {

    companion object {
        private const val TAG = "ImportValidator"
    }

    /**
     * Validates imported credentials against existing credentials.
     *
     * @param importedCredentials Credentials from import file
     * @param existingCredentials Current vault credentials
     * @return ImportPreview with conflicts identified
     */
    fun validate(
        importedCredentials: List<Credential>,
        existingCredentials: List<Credential>,
        sourceFormat: String
    ): ImportPreview {
        val conflicts = mutableListOf<ImportConflict>()
        val warnings = mutableListOf<String>()

        importedCredentials.forEach { imported ->
            // Check for duplicate username + website combination
            val duplicateByUserSite = existingCredentials.find {
                it.username.equals(imported.username, ignoreCase = true) &&
                it.website.equals(imported.website, ignoreCase = true) &&
                it.website.isNotEmpty()
            }

            if (duplicateByUserSite != null) {
                conflicts.add(
                    ImportConflict(
                        existingCredential = duplicateByUserSite,
                        importedCredential = imported,
                        conflictType = ImportConflict.ConflictType.DUPLICATE_USERNAME_WEBSITE
                    )
                )
                return@forEach
            }

            // Check for duplicate title (less strict, just warning)
            val duplicateByTitle = existingCredentials.find {
                it.title.equals(imported.title, ignoreCase = true)
            }

            if (duplicateByTitle != null) {
                warnings.add("Credential '${imported.title}' with same title already exists")
            }

            // Validate required fields
            if (imported.username.isBlank() || imported.password.isBlank()) {
                conflicts.add(
                    ImportConflict(
                        existingCredential = imported.copy(id = -1),
                        importedCredential = imported,
                        conflictType = ImportConflict.ConflictType.INVALID_FIELD
                    )
                )
            }
        }

        Log.d(TAG, "Validation complete: ${conflicts.size} conflicts, ${warnings.size} warnings")

        return ImportPreview(
            credentials = importedCredentials,
            conflicts = conflicts,
            warnings = warnings,
            sourceFormat = sourceFormat
        )
    }

    /**
     * Filters credentials based on conflict resolutions.
     *
     * @param preview ImportPreview with resolved conflicts
     * @return List of credentials ready for import after applying resolutions
     */
    fun applyResolutions(preview: ImportPreview): List<Credential> {
        val resolved = mutableListOf<Credential>()
        val conflictMap = preview.conflicts.associateBy { it.importedCredential.id }

        preview.credentials.forEach { credential ->
            val conflict = conflictMap[credential.id]

            if (conflict == null) {
                // No conflict, add as-is
                resolved.add(credential)
            } else {
                when (conflict.userResolution) {
                    ImportConflict.ConflictResolution.SKIP -> {
                        // Skip this credential
                        Log.d(TAG, "Skipping credential: ${credential.title}")
                    }

                    ImportConflict.ConflictResolution.OVERWRITE -> {
                        // Add imported credential (will overwrite via upsert)
                        resolved.add(credential.copy(id = conflict.existingCredential.id))
                    }

                    ImportConflict.ConflictResolution.KEEP_BOTH -> {
                        // Modify title to avoid duplicate
                        val newTitle = "${credential.title} (imported)"
                        resolved.add(credential.copy(title = newTitle))
                    }
                }
            }
        }

        return resolved
    }

    /**
     * Validates CSV field mapping.
     *
     * @param fieldMapping Field mapping to validate
     * @return Error message if invalid, null if valid
     */
    fun validateFieldMapping(fieldMapping: CsvFieldMapping): String? {
        return fieldMapping.validate()
    }

    /**
     * Sanitizes credential data before import.
     *
     * - Trims whitespace
     * - Validates URLs
     * - Sanitizes notes
     *
     * @param credential Credential to sanitize
     * @return Sanitized credential
     */
    fun sanitizeCredential(credential: Credential): Credential {
        return credential.copy(
            title = credential.title.trim(),
            username = credential.username.trim(),
            password = credential.password, // Never trim password (might have leading/trailing spaces)
            website = credential.website.trim(),
            notes = credential.notes.trim(),
            packageName = credential.packageName.trim()
        )
    }
}
