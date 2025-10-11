package com.trustvault.android.domain.model

import java.util.Date

/**
 * Domain model for a credential.
 * Contains unencrypted data for use in the application.
 */
data class Credential(
    val id: Long = 0,
    val title: String,
    val username: String,
    val password: String,
    val website: String = "",
    val notes: String = "",
    val category: CredentialCategory = CredentialCategory.LOGIN,
    val createdAt: Date = Date(),
    val updatedAt: Date = Date()
)
