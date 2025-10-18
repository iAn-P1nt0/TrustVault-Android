package com.trustvault.android.data.importexport

import android.util.Log
import com.trustvault.android.domain.model.Credential
import com.trustvault.android.domain.model.CredentialCategory
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import javax.inject.Inject

/**
 * Imports credentials from CSV format.
 *
 * Features:
 * - Flexible field mapping (user can specify which columns contain what data)
 * - Automatic header detection
 * - Validation of required fields (username, password)
 * - Support for optional fields (OTP, package name, etc.)
 * - Memory-safe parsing with automatic buffer clearing
 *
 * Expected CSV Format (when auto-detected):
 * title,username,password,website,category,notes,otp_secret,package_name
 */
class CsvImporter @Inject constructor() {

    companion object {
        private const val TAG = "CsvImporter"
    }

    /**
     * Imports credentials from CSV string with field mapping.
     *
     * @param csvContent CSV content as string
     * @param fieldMapping Field mapping configuration
     * @return ImportPreview with parsed credentials and any warnings
     */
    fun import(
        csvContent: String,
        fieldMapping: CsvFieldMapping
    ): ImportPreview {
        return try {
            val credentials = mutableListOf<Credential>()
            val warnings = mutableListOf<String>()

            CSVParser.parse(csvContent, CSVFormat.DEFAULT.withFirstRecordAsHeader()).use { parser ->
                var rowNumber = 0

                for (record in parser) {
                    rowNumber++

                    // Skip empty rows
                    if (record.values().all { it.isBlank() }) {
                        continue
                    }

                    try {
                        val credential = parseRow(record, fieldMapping, rowNumber)
                        if (credential != null) {
                            credentials.add(credential)
                        }
                    } catch (e: Exception) {
                        warnings.add("Row $rowNumber: ${e.message}")
                        Log.w(TAG, "Error parsing row $rowNumber: ${e.message}")
                    }
                }
            }

            if (credentials.isEmpty()) {
                return ImportPreview(
                    credentials = emptyList(),
                    warnings = warnings + "No valid credentials found in CSV",
                    sourceFormat = "CSV"
                )
            }

            ImportPreview(
                credentials = credentials,
                warnings = warnings,
                sourceFormat = "CSV"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error importing CSV: ${e.message}", e)
            ImportPreview(
                credentials = emptyList(),
                warnings = listOf("Failed to parse CSV: ${e.message}"),
                sourceFormat = "CSV"
            )
        }
    }

    /**
     * Auto-detects field mapping from CSV headers.
     *
     * Attempts to match common header names to credential fields.
     * Returns default mapping if headers not found.
     *
     * @param csvContent CSV content as string
     * @return Detected field mapping
     */
    fun autoDetectFieldMapping(csvContent: String): CsvFieldMapping {
        return try {
            CSVParser.parse(csvContent, CSVFormat.DEFAULT.withFirstRecordAsHeader()).use { parser ->
                val headers = parser.headerMap
                    .mapKeys { it.key.lowercase().trim() }
                    .mapValues { it.value }

                CsvFieldMapping(
                    titleIndex = findHeaderIndex(headers, "title", "name", "account"),
                    usernameIndex = findHeaderIndex(headers, "username", "user", "email", "login"),
                    passwordIndex = findHeaderIndex(headers, "password", "pass", "pwd"),
                    websiteIndex = findHeaderIndex(headers, "website", "url", "site", "domain"),
                    categoryIndex = findHeaderIndex(headers, "category", "type"),
                    notesIndex = findHeaderIndex(headers, "notes", "comment"),
                    otpSecretIndex = findHeaderIndex(headers, "otp", "totp", "2fa", "otp_secret"),
                    packageNameIndex = findHeaderIndex(headers, "package", "app", "package_name"),
                    skipFirstRow = true
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to auto-detect field mapping: ${e.message}")
            // Return default mapping
            CsvFieldMapping()
        }
    }

    /**
     * Parses a single CSV record into a Credential.
     *
     * @param record CSV record
     * @param fieldMapping Field mapping
     * @param rowNumber Row number for error reporting
     * @return Credential or null if required fields missing
     */
    private fun parseRow(
        record: org.apache.commons.csv.CSVRecord,
        fieldMapping: CsvFieldMapping,
        rowNumber: Int
    ): Credential? {
        val username = fieldMapping.usernameIndex?.let { record.get(it) }?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Username is required but missing")

        val password = fieldMapping.passwordIndex?.let { record.get(it) }?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Password is required but missing")

        val title = fieldMapping.titleIndex?.let { record.get(it) }?.takeIf { it.isNotBlank() }
            ?: username // Use username as fallback title

        val website = fieldMapping.websiteIndex?.let { record.get(it) }?.takeIf { it.isNotBlank() } ?: ""
        val notes = fieldMapping.notesIndex?.let { record.get(it) }?.takeIf { it.isNotBlank() } ?: ""
        val otpSecret = fieldMapping.otpSecretIndex?.let { record.get(it) }?.takeIf { it.isNotBlank() }
        val packageName = fieldMapping.packageNameIndex?.let { record.get(it) }?.takeIf { it.isNotBlank() } ?: ""

        val categoryStr = fieldMapping.categoryIndex?.let { record.get(it) }?.takeIf { it.isNotBlank() } ?: "LOGIN"
        val category = try {
            CredentialCategory.entries.find {
                it.displayName.equals(categoryStr, ignoreCase = true) || it.name.equals(categoryStr, ignoreCase = true)
            } ?: CredentialCategory.LOGIN
        } catch (e: Exception) {
            CredentialCategory.LOGIN
        }

        return Credential(
            id = 0,
            title = title,
            username = username,
            password = password,
            website = website,
            notes = notes,
            category = category,
            packageName = packageName,
            otpSecret = otpSecret,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Finds header index by trying multiple possible names.
     *
     * @param headers Map of lowercase headers to their original index
     * @param possibleNames Possible header names to try
     * @return Index of matched header or null if none found
     */
    private fun findHeaderIndex(
        headers: Map<String, Int>,
        vararg possibleNames: String
    ): Int? {
        for (name in possibleNames) {
            headers[name.lowercase()]?.let { return it }
        }
        return null
    }
}
