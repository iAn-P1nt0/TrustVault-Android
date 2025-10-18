package com.trustvault.android.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for storing encrypted credentials.
 * All sensitive fields are encrypted before storage.
 */
@Entity(tableName = "credentials")
data class CredentialEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String, // Plain text for search/display
    val username: String, // Encrypted
    val password: String, // Encrypted
    val website: String, // Plain text for search/display
    val notes: String, // Encrypted
    val category: String, // Plain text enum value
    val packageName: String = "", // Plain text - Android package name for autofill matching
    val otpSecret: String? = null, // Encrypted - Base32-encoded TOTP secret, null if not configured
    val createdAt: Long,
    val updatedAt: Long
)
