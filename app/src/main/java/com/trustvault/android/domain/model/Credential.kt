package com.trustvault.android.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Domain model for a credential.
 * Contains unencrypted data for use in the application.
 *
 * **URL/Domain Matching:**
 * - packageName: Android package name for app autofill matching (e.g., "com.example.app")
 * - website: Primary website URL for web autofill (e.g., "https://example.com")
 * - allowedDomains: List of additional domain/URL patterns for autofill matching
 *   * Supports full URLs (https://example.com), domains (example.com), wildcards (*.example.com)
 *   * Used for browser cross-site autofill (same credential across subdomains)
 *   * User can manually add/remove entries via domain override UI
 *   * Defaults to primary domain extracted from website field
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
    val otpSecret: String? = null, // Base32-encoded TOTP secret, null if not configured
    val allowedDomains: List<String> = emptyList(), // Additional domains for URL matching overrides
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) : Parcelable
