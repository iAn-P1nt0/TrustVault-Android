package com.trustvault.android.domain.model

/**
 * Domain model for credential categories.
 */
enum class CredentialCategory(val displayName: String) {
    LOGIN("Login"),
    PAYMENT("Payment"),
    IDENTITY("Identity"),
    NOTE("Secure Note"),
    OTHER("Other");

    companion object {
        fun fromString(value: String): CredentialCategory {
            return entries.find { it.name == value } ?: OTHER
        }
    }
}
