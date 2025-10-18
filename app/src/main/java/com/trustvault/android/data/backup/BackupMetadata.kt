package com.trustvault.android.data.backup

import com.google.gson.annotations.SerializedName
import java.text.SimpleDateFormat
import java.util.*

/**
 * Metadata for an encrypted backup file.
 *
 * Stored alongside encrypted credential data to enable:
 * - Version compatibility checking
 * - Backup timing and scheduling
 * - Device binding and rotation
 * - Recovery and audit trails
 */
data class BackupMetadata(
    @SerializedName("version")
    val version: Int = CURRENT_VERSION,

    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @SerializedName("device_id")
    val deviceId: String,

    @SerializedName("credential_count")
    val credentialCount: Int,

    @SerializedName("encryption_algorithm")
    val encryptionAlgorithm: String = "AES-256-GCM",

    @SerializedName("key_derivation_algorithm")
    val keyDerivationAlgorithm: String = "PBKDF2-HMAC-SHA256",

    @SerializedName("key_derivation_iterations")
    val keyDerivationIterations: Int = 600000,

    @SerializedName("app_version")
    val appVersion: String,

    @SerializedName("android_api_level")
    val androidApiLevel: Int,

    @SerializedName("backup_type")
    val backupType: BackupType = BackupType.MANUAL,

    @SerializedName("backup_size_bytes")
    val backupSizeBytes: Long = 0,

    @SerializedName("checksum")
    val checksum: String? = null,

    @SerializedName("notes")
    val notes: String = ""
) {
    companion object {
        const val CURRENT_VERSION = 1
    }

    enum class BackupType {
        @SerializedName("MANUAL")
        MANUAL,

        @SerializedName("SCHEDULED")
        SCHEDULED,

        @SerializedName("CLOUD_SYNC")
        CLOUD_SYNC
    }

    /**
     * Formats timestamp as human-readable date.
     */
    fun formattedDate(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(timestamp))
    }

    /**
     * Formats backup size as human-readable string.
     */
    fun formattedSize(): String {
        return when {
            backupSizeBytes < 1024 -> "${backupSizeBytes} B"
            backupSizeBytes < 1024 * 1024 -> "${backupSizeBytes / 1024} KB"
            else -> "${backupSizeBytes / (1024 * 1024)} MB"
        }
    }

    /**
     * Validates metadata for backup compatibility.
     */
    fun validate(): Boolean {
        return version <= CURRENT_VERSION &&
            credentialCount >= 0 &&
            keyDerivationIterations >= 600000 &&
            deviceId.isNotBlank()
    }
}

/**
 * Backup file structure representation.
 * Encapsulates metadata, encrypted data, and integrity information.
 */
data class BackupFile(
    val metadata: BackupMetadata,
    val encryptedCredentials: ByteArray,
    val iv: ByteArray,
    val salt: ByteArray,
    val authTag: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BackupFile

        if (metadata != other.metadata) return false
        if (!encryptedCredentials.contentEquals(other.encryptedCredentials)) return false
        if (!iv.contentEquals(other.iv)) return false
        if (!salt.contentEquals(other.salt)) return false
        if (authTag != null) {
            if (other.authTag == null) return false
            if (!authTag.contentEquals(other.authTag)) return false
        } else if (other.authTag != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = metadata.hashCode()
        result = 31 * result + encryptedCredentials.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        result = 31 * result + salt.contentHashCode()
        result = 31 * result + (authTag?.contentHashCode() ?: 0)
        return result
    }

    /**
     * Securely wipes sensitive buffers from memory.
     */
    fun wipe() {
        encryptedCredentials.fill(0)
        iv.fill(0)
        salt.fill(0)
        authTag?.fill(0)
    }
}

/**
 * Backup restore result information.
 */
data class BackupRestoreResult(
    val success: Boolean,
    val credentialsRestored: Int = 0,
    val message: String = "",
    val exception: Exception? = null
)
