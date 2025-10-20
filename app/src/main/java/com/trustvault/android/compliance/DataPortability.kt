package com.trustvault.android.compliance

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.trustvault.android.data.importexport.CsvExporter
import com.trustvault.android.domain.model.Credential
import com.trustvault.android.domain.repository.CredentialRepository
import com.trustvault.android.security.CryptoManager
import com.trustvault.android.util.secureWipe
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataPortability - GDPR Article 20 Right to Data Portability
 *
 * Implements GDPR Article 20 "Right to data portability":
 * - Data subject has right to receive personal data in structured, commonly used, machine-readable format
 * - Data subject has right to transmit data to another controller without hindrance
 *
 * **GDPR Article 20 Requirements:**
 * - Structured format (JSON, CSV, XML)
 * - Commonly used (industry standard formats)
 * - Machine-readable (not PDFs or images)
 * - No hindrance to transmission (portable, interoperable)
 *
 * **DPDP Act 2023:**
 * - Supports data principal's right to access and portability
 *
 * **Supported Export Formats:**
 * 1. JSON - Machine-readable, preserves data structure, industry standard
 * 2. CSV - Spreadsheet-compatible, human-readable, universal support
 * 3. XML - Industry standard, supports complex structures
 * 4. KeePass CSV - Password manager interchange format (compatible with KeePass, 1Password, Bitwarden)
 *
 * **Export Contents:**
 * - All credentials (title, username, password, website, notes, OTP secrets)
 * - Metadata (creation date, modification date, category)
 * - Consent records (processing purposes, granted/denied, timestamps)
 * - Privacy preferences (retention policy, region settings)
 * - Timestamps and version information
 *
 * @property context Application context
 * @property credentialRepository Credential data access
 * @property csvExporter CSV export functionality
 * @property consentManager Consent record access
 * @property privacyManager Privacy settings access
 * @property cryptoManager Encryption for sensitive exports
 */
@Singleton
class DataPortability @Inject constructor(
    @ApplicationContext private val context: Context,
    private val credentialRepository: CredentialRepository,
    private val csvExporter: CsvExporter,
    private val consentManager: ConsentManager,
    private val privacyManager: PrivacyManager,
    private val cryptoManager: CryptoManager
) {

    companion object {
        private const val TAG = "DataPortability"
        private const val EXPORT_VERSION = "1.0.0"
    }

    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        .create()

    /**
     * Export format options.
     */
    enum class ExportFormat(val extension: String, val mimeType: String) {
        /** JSON format - structured, machine-readable */
        JSON("json", "application/json"),

        /** CSV format - spreadsheet-compatible */
        CSV("csv", "text/csv"),

        /** XML format - industry standard */
        XML("xml", "application/xml"),

        /** KeePass-compatible CSV format */
        KEEPASS_CSV("csv", "text/csv")
    }

    /**
     * Export scope options.
     */
    enum class ExportScope {
        /** Only credentials */
        CREDENTIALS_ONLY,

        /** Credentials + consent records */
        CREDENTIALS_AND_CONSENT,

        /** All user data (credentials, consent, privacy preferences, metadata) */
        ALL_DATA
    }

    /**
     * Export result with file information.
     */
    data class ExportResult(
        val success: Boolean,
        val file: File?,
        val format: ExportFormat,
        val recordCount: Int,
        val fileSizeBytes: Long,
        val encrypted: Boolean,
        val error: String?
    )

    /**
     * Complete data export package (for JSON/XML).
     */
    data class DataExportPackage(
        val version: String,
        val exportDate: Long,
        val scope: ExportScope,
        val credentials: List<CredentialExport>,
        val consentRecords: List<ConsentExport>? = null,
        val privacyPreferences: PrivacyPreferencesExport? = null,
        val metadata: ExportMetadata
    )

    /**
     * Credential export model (sanitized for export).
     */
    data class CredentialExport(
        val title: String,
        val username: String,
        val password: String,
        val website: String,
        val category: String,
        val notes: String,
        val otpSecret: String?,
        val packageName: String,
        // TODO: Add isFavorite when field is added to Credential model
        // val isFavorite: Boolean,
        val createdAt: Long,
        val updatedAt: Long
    )

    /**
     * Consent record export model.
     */
    data class ConsentExport(
        val purpose: String,
        val purposeDisplayName: String,
        val granted: Boolean,
        val timestamp: Long,
        val version: String
    )

    /**
     * Privacy preferences export model.
     */
    data class PrivacyPreferencesExport(
        val privacyPolicyVersion: String,
        val privacyPolicyAcceptedDate: Long,
        val gdprRegion: Boolean,
        val dpdpRegion: Boolean,
        val dataRetentionDays: Int
    )

    /**
     * Export metadata.
     */
    data class ExportMetadata(
        val totalRecords: Int,
        val exportTimestamp: Long,
        val exportVersion: String,
        val appVersion: String = "1.0.0"
    )

    // ========================================================================
    // PUBLIC EXPORT API
    // ========================================================================

    /**
     * Exports user data in specified format.
     *
     * **GDPR Article 20(1):** Data subject has right to receive personal data
     * in structured, commonly used, machine-readable format.
     *
     * @param format Export format (JSON, CSV, XML, KeePass)
     * @param scope What data to include
     * @param encrypt Whether to encrypt the export (recommended)
     * @param password Encryption password (required if encrypt=true)
     * @return ExportResult with file location and metadata
     */
    suspend fun exportUserData(
        format: ExportFormat,
        scope: ExportScope = ExportScope.ALL_DATA,
        encrypt: Boolean = false,
        password: CharArray? = null
    ): ExportResult {
        return try {
            Log.d(TAG, "Starting data export: format=$format, scope=$scope, encrypt=$encrypt")

            // Validate encryption requirements
            if (encrypt && password == null) {
                return ExportResult(
                    success = false,
                    file = null,
                    format = format,
                    recordCount = 0,
                    fileSizeBytes = 0,
                    encrypted = false,
                    error = "Password required for encrypted export"
                )
            }

            // Record data processing activity
            privacyManager.recordDataProcessing(
                purpose = PrivacyManager.DataProcessingPurpose.DATA_EXPORT,
                action = "export_initiated",
                dataType = format.name
            )

            // Generate export based on format
            val result = when (format) {
                ExportFormat.JSON -> exportToJson(scope, encrypt, password)
                ExportFormat.CSV -> exportToCsv(scope, encrypt, password)
                ExportFormat.XML -> exportToXml(scope, encrypt, password)
                ExportFormat.KEEPASS_CSV -> exportToKeePassCsv(encrypt, password)
            }

            // Record completion
            if (result.success) {
                privacyManager.recordDataProcessing(
                    purpose = PrivacyManager.DataProcessingPurpose.DATA_EXPORT,
                    action = "export_completed",
                    dataType = format.name,
                    recordCount = result.recordCount
                )

                // Update last export timestamp
                context.getSharedPreferences("trustvault_privacy", Context.MODE_PRIVATE)
                    .edit()
                    .putLong("last_data_export", System.currentTimeMillis())
                    .apply()

                Log.d(TAG, "Export completed: ${result.recordCount} records, ${result.fileSizeBytes} bytes")
            }

            result

        } catch (e: Exception) {
            Log.e(TAG, "Error during data export: ${e.message}", e)
            ExportResult(
                success = false,
                file = null,
                format = format,
                recordCount = 0,
                fileSizeBytes = 0,
                encrypted = false,
                error = e.message
            )
        } finally {
            // Secure wipe password from memory
            password?.secureWipe()
        }
    }

    // ========================================================================
    // JSON EXPORT
    // ========================================================================

    /**
     * Exports data to JSON format.
     *
     * @param scope Export scope
     * @param encrypt Whether to encrypt
     * @param password Encryption password
     * @return ExportResult
     */
    private suspend fun exportToJson(
        scope: ExportScope,
        encrypt: Boolean,
        password: CharArray?
    ): ExportResult {
        try {
            // Build export package
            val exportPackage = buildExportPackage(scope)

            // Serialize to JSON
            val json = gson.toJson(exportPackage)
            val jsonBytes = json.toByteArray(Charsets.UTF_8)

            // Create export file
            val fileName = generateFileName("trustvault_export", "json")
            val file = File(context.cacheDir, fileName)

            // Encrypt if requested
            if (encrypt && password != null) {
                val encrypted = cryptoManager.encrypt(jsonBytes, keyAlias = null)
                val encryptedJson = gson.toJson(mapOf(
                    "encrypted" to true,
                    "algorithm" to encrypted.algorithm.name,
                    "data" to android.util.Base64.encodeToString(
                        encrypted.ciphertext,
                        android.util.Base64.NO_WRAP
                    ),
                    "iv" to android.util.Base64.encodeToString(encrypted.iv, android.util.Base64.NO_WRAP)
                ))
                file.writeText(encryptedJson)
            } else {
                file.writeText(json)
            }

            return ExportResult(
                success = true,
                file = file,
                format = ExportFormat.JSON,
                recordCount = exportPackage.credentials.size,
                fileSizeBytes = file.length(),
                encrypted = encrypt,
                error = null
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error exporting to JSON: ${e.message}", e)
            return ExportResult(
                success = false,
                file = null,
                format = ExportFormat.JSON,
                recordCount = 0,
                fileSizeBytes = 0,
                encrypted = false,
                error = e.message
            )
        }
    }

    // ========================================================================
    // CSV EXPORT
    // ========================================================================

    /**
     * Exports data to CSV format.
     *
     * @param scope Export scope
     * @param encrypt Whether to encrypt
     * @param password Encryption password
     * @return ExportResult
     */
    private suspend fun exportToCsv(
        scope: ExportScope,
        encrypt: Boolean,
        password: CharArray?
    ): ExportResult {
        try {
            // Get credentials
            val credentials = credentialRepository.getAllCredentials().first()

            // Use existing CSV exporter
            val csvString = csvExporter.exportToString(credentials)
            val csvBytes = csvString.toByteArray(Charsets.UTF_8)

            // Create export file
            val fileName = generateFileName("trustvault_export", "csv")
            val file = File(context.cacheDir, fileName)

            // Encrypt if requested
            if (encrypt && password != null) {
                val encrypted = cryptoManager.encrypt(csvBytes, keyAlias = null)
                file.writeBytes(encrypted.ciphertext)
            } else {
                file.writeText(csvString)
            }

            return ExportResult(
                success = true,
                file = file,
                format = ExportFormat.CSV,
                recordCount = credentials.size,
                fileSizeBytes = file.length(),
                encrypted = encrypt,
                error = null
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error exporting to CSV: ${e.message}", e)
            return ExportResult(
                success = false,
                file = null,
                format = ExportFormat.CSV,
                recordCount = 0,
                fileSizeBytes = 0,
                encrypted = false,
                error = e.message
            )
        }
    }

    // ========================================================================
    // XML EXPORT
    // ========================================================================

    /**
     * Exports data to XML format.
     *
     * @param scope Export scope
     * @param encrypt Whether to encrypt
     * @param password Encryption password
     * @return ExportResult
     */
    private suspend fun exportToXml(
        scope: ExportScope,
        encrypt: Boolean,
        password: CharArray?
    ): ExportResult {
        try {
            val exportPackage = buildExportPackage(scope)

            // Build XML manually (simple implementation)
            val xml = buildXmlString(exportPackage)
            val xmlBytes = xml.toByteArray(Charsets.UTF_8)

            // Create export file
            val fileName = generateFileName("trustvault_export", "xml")
            val file = File(context.cacheDir, fileName)

            // Encrypt if requested
            if (encrypt && password != null) {
                val encrypted = cryptoManager.encrypt(xmlBytes, keyAlias = null)
                file.writeBytes(encrypted.ciphertext)
            } else {
                file.writeText(xml)
            }

            return ExportResult(
                success = true,
                file = file,
                format = ExportFormat.XML,
                recordCount = exportPackage.credentials.size,
                fileSizeBytes = file.length(),
                encrypted = encrypt,
                error = null
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error exporting to XML: ${e.message}", e)
            return ExportResult(
                success = false,
                file = null,
                format = ExportFormat.XML,
                recordCount = 0,
                fileSizeBytes = 0,
                encrypted = false,
                error = e.message
            )
        }
    }

    // ========================================================================
    // KEEPASS CSV EXPORT
    // ========================================================================

    /**
     * Exports to KeePass-compatible CSV format.
     *
     * KeePass CSV Format:
     * Account,Login Name,Password,Web Site,Comments
     */
    private suspend fun exportToKeePassCsv(
        encrypt: Boolean,
        password: CharArray?
    ): ExportResult {
        try {
            val credentials = credentialRepository.getAllCredentials().first()

            // Build KeePass CSV
            val csv = buildString {
                appendLine("Account,Login Name,Password,Web Site,Comments")
                credentials.forEach { credential ->
                    val account = escapeCsv(credential.title)
                    val login = escapeCsv(credential.username)
                    val pwd = escapeCsv(credential.password)
                    val website = escapeCsv(credential.website)
                    val comments = escapeCsv(credential.notes)
                    appendLine("$account,$login,$pwd,$website,$comments")
                }
            }

            val csvBytes = csv.toByteArray(Charsets.UTF_8)

            // Create export file
            val fileName = generateFileName("trustvault_keepass_export", "csv")
            val file = File(context.cacheDir, fileName)

            // Encrypt if requested
            if (encrypt && password != null) {
                val encrypted = cryptoManager.encrypt(csvBytes, keyAlias = null)
                file.writeBytes(encrypted.ciphertext)
            } else {
                file.writeText(csv)
            }

            return ExportResult(
                success = true,
                file = file,
                format = ExportFormat.KEEPASS_CSV,
                recordCount = credentials.size,
                fileSizeBytes = file.length(),
                encrypted = encrypt,
                error = null
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error exporting to KeePass CSV: ${e.message}", e)
            return ExportResult(
                success = false,
                file = null,
                format = ExportFormat.KEEPASS_CSV,
                recordCount = 0,
                fileSizeBytes = 0,
                encrypted = false,
                error = e.message
            )
        }
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    /**
     * Builds export package with requested scope.
     */
    private suspend fun buildExportPackage(scope: ExportScope): DataExportPackage {
        // Get credentials
        val credentials = credentialRepository.getAllCredentials().first()
        val credentialExports = credentials.map { it.toExport() }

        // Get consent records if requested
        val consentExports = if (scope != ExportScope.CREDENTIALS_ONLY) {
            consentManager.getAllConsentRecords().map { (purpose, record) ->
                ConsentExport(
                    purpose = purpose.name,
                    purposeDisplayName = purpose.displayName,
                    granted = record.granted,
                    timestamp = record.timestamp,
                    version = record.version
                )
            }
        } else null

        // Get privacy preferences if requested
        val privacyPrefs = if (scope == ExportScope.ALL_DATA) {
            val dashboardData = privacyManager.getPrivacyDashboardData()
            PrivacyPreferencesExport(
                privacyPolicyVersion = dashboardData.privacyPolicyVersion,
                privacyPolicyAcceptedDate = dashboardData.privacyPolicyAcceptedDate,
                gdprRegion = dashboardData.isGdprRegion,
                dpdpRegion = dashboardData.isDpdpRegion,
                dataRetentionDays = dashboardData.dataRetentionDays
            )
        } else null

        return DataExportPackage(
            version = EXPORT_VERSION,
            exportDate = System.currentTimeMillis(),
            scope = scope,
            credentials = credentialExports,
            consentRecords = consentExports,
            privacyPreferences = privacyPrefs,
            metadata = ExportMetadata(
                totalRecords = credentials.size,
                exportTimestamp = System.currentTimeMillis(),
                exportVersion = EXPORT_VERSION
            )
        )
    }

    /**
     * Converts Credential to export model.
     */
    private fun Credential.toExport(): CredentialExport {
        return CredentialExport(
            title = this.title,
            username = this.username,
            password = this.password,
            website = this.website,
            category = this.category.displayName,
            notes = this.notes,
            otpSecret = this.otpSecret,
            packageName = this.packageName,
            // TODO: Add isFavorite when field is added to Credential model
            // isFavorite = this.isFavorite,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt
        )
    }

    /**
     * Builds XML string from export package.
     */
    private fun buildXmlString(exportPackage: DataExportPackage): String {
        return buildString {
            appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
            appendLine("<trustvault_export version=\"${exportPackage.version}\">")
            appendLine("  <metadata>")
            appendLine("    <export_date>${Date(exportPackage.exportDate)}</export_date>")
            appendLine("    <total_records>${exportPackage.metadata.totalRecords}</total_records>")
            appendLine("  </metadata>")

            appendLine("  <credentials>")
            exportPackage.credentials.forEach { cred ->
                appendLine("    <credential>")
                appendLine("      <title>${escapeXml(cred.title)}</title>")
                appendLine("      <username>${escapeXml(cred.username)}</username>")
                appendLine("      <password>${escapeXml(cred.password)}</password>")
                appendLine("      <website>${escapeXml(cred.website)}</website>")
                appendLine("      <category>${escapeXml(cred.category)}</category>")
                appendLine("      <notes>${escapeXml(cred.notes)}</notes>")
                if (cred.otpSecret != null) {
                    appendLine("      <otp_secret>${escapeXml(cred.otpSecret)}</otp_secret>")
                }
                appendLine("      <created_at>${cred.createdAt}</created_at>")
                appendLine("      <updated_at>${cred.updatedAt}</updated_at>")
                appendLine("    </credential>")
            }
            appendLine("  </credentials>")

            if (exportPackage.consentRecords != null) {
                appendLine("  <consent_records>")
                exportPackage.consentRecords.forEach { consent ->
                    appendLine("    <consent>")
                    appendLine("      <purpose>${escapeXml(consent.purposeDisplayName)}</purpose>")
                    appendLine("      <granted>${consent.granted}</granted>")
                    appendLine("      <timestamp>${consent.timestamp}</timestamp>")
                    appendLine("    </consent>")
                }
                appendLine("  </consent_records>")
            }

            appendLine("</trustvault_export>")
        }
    }

    /**
     * Escapes XML special characters.
     */
    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    /**
     * Escapes CSV special characters.
     */
    private fun escapeCsv(text: String): String {
        return if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
            "\"${text.replace("\"", "\"\"")}\""
        } else {
            text
        }
    }

    /**
     * Generates timestamped filename.
     */
    private fun generateFileName(prefix: String, extension: String): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "${prefix}_${timestamp}.$extension"
    }
}
