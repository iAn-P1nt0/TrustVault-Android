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
    val createdAt: Long,
    val updatedAt: Long
)
