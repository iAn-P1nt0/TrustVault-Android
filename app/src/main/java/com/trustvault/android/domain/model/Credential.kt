package com.trustvault.android.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Domain model for a credential.
 * Contains unencrypted data for use in the application.
 */
@Parcelize
data class Credential(
    val id: Long = 0,
    val title: String,
    val username: String,
    val password: String,
    val website: String = "",
    val notes: String = "",
    val category: CredentialCategory = CredentialCategory.LOGIN,
    val packageName: String = "", // Android package name for autofill matching
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) : Parcelable
