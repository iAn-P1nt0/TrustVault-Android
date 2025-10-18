package com.trustvault.android.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for storing encrypted credentials.
 * All sensitive fields are encrypted before storage.
 *
 * **Version History:**
 * - v1: Initial schema (title, username, password, website, notes, category, timestamps)
 * - v2: Added packageName for Android AutofillService support
 * - v3: Added otpSecret for TOTP/2FA support
 * - v4: Added allowedDomains for URL/domain matching overrides
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
    val allowedDomains: String = "[]", // Plain text JSON array - List of domains/URLs for autofill matching
    val createdAt: Long,
    val updatedAt: Long
)
