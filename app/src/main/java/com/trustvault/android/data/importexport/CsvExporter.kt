package com.trustvault.android.data.importexport

import android.util.Log
import com.trustvault.android.domain.model.Credential
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import java.io.StringWriter
import javax.inject.Inject

/**
 * Exports credentials to CSV format.
 *
 * Features:
 * - Supports full credential export including OTP secrets
 * - CSV format follows standard headers
 * - Sensitive data not masked (user can control export)
 * - Memory-safe with StringWriter
 *
 * CSV Format:
 * title,username,password,website,category,notes,otp_secret,package_name
 */
class CsvExporter @Inject constructor() {

    companion object {
        private const val TAG = "CsvExporter"

        // CSV Headers
        private val HEADERS = arrayOf(
            "title",
            "username",
            "password",
            "website",
            "category",
            "notes",
            "otp_secret",
            "package_name"
        )
    }

    /**
     * Exports credentials to CSV string.
     *
     * @param credentials List of credentials to export
     * @return CSV string with headers and credential rows
     * @throws Exception if CSV generation fails
     */
    fun exportToString(credentials: List<Credential>): String {
        return try {
            StringWriter().use { writer ->
                CSVPrinter(writer, CSVFormat.DEFAULT.withHeader(*HEADERS)).use { printer ->
                    credentials.forEach { credential ->
                        printer.printRecord(
                            credential.title,
                            credential.username,
                            credential.password,
                            credential.website,
                            credential.category.displayName,
                            credential.notes,
                            credential.otpSecret ?: "",
                            credential.packageName
                        )
                    }
                    printer.flush()
                }
                writer.toString()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting credentials to CSV: ${e.message}", e)
            throw e
        }
    }

    /**
     * Exports credentials to CSV bytes.
     *
     * @param credentials List of credentials to export
     * @return CSV content as UTF-8 bytes
     */
    fun exportToBytes(credentials: List<Credential>): ByteArray {
        return exportToString(credentials).toByteArray(Charsets.UTF_8)
    }
}
