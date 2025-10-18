package com.trustvault.android.credentialmanager

import android.content.Context
import android.util.Log
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CreatePasswordResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.GetPasswordOption
import androidx.credentials.PasswordCredential
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialException
import com.trustvault.android.domain.model.Credential
import com.trustvault.android.domain.repository.CredentialRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Facade for integrating with Android Credential Manager API.
 *
 * Provides modern credential management for passwords and passkeys.
 * Works alongside AutofillService to provide best user experience.
 *
 * Features:
 * - Password retrieval via Credential Manager sheet
 * - Password saving to vault
 * - Passkey/WebAuthn registration and authentication (Android 14+)
 * - Integration with existing vault credentials
 * - User preference support (Credential Manager vs Autofill)
 *
 * Security:
 * - Credentials remain encrypted in database
 * - Only decrypted when needed for Credential Manager API
 * - User authentication required via database unlock
 * - Passkeys use device's secure enclave (StrongBox when available)
 * - Private keys never leave device
 *
 * Android Version Support:
 * - Password Manager: Android 14+ (API 34+) with Google Play Services
 * - Passkeys/WebAuthn: Android 14+ (API 34+) with hardware support
 * - Graceful fallback to AutofillService on older versions
 *
 * Passkey Integration:
 * See PASSKEY_SERVER_INTEGRATION.md for server-side implementation details.
 */
@Singleton
class CredentialManagerFacade @Inject constructor(
    @ApplicationContext private val context: Context,
    private val credentialRepository: CredentialRepository,
    private val passkeyManager: PasskeyManager
) {

    private val credentialManager: CredentialManager by lazy {
        CredentialManager.create(context)
    }

    companion object {
        private const val TAG = "CredentialManager"
    }

    /**
     * Checks if Credential Manager is available on this device.
     * Requires Android 14+ or Google Play Services with Credential Manager support.
     */
    suspend fun isAvailable(): Boolean {
        return try {
            // Try to create a simple request to check availability
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(GetPasswordOption())
                .build()
            true
        } catch (e: Exception) {
            Log.d(TAG, "Credential Manager not available: ${e.message}")
            false
        }
    }

    /**
     * Retrieves password credentials for the calling app.
     *
     * Shows Credential Manager bottom sheet with available credentials
     * from TrustVault that match the calling app's package or website.
     *
     * @param callingPackage Package name of the app requesting credentials
     * @param webDomain Optional web domain for browser-based requests
     * @return PasswordCredential if user selects one, null if cancelled
     */
    suspend fun getPasswordCredential(
        callingPackage: String,
        webDomain: String? = null
    ): PasswordCredential? {
        return try {
            Log.d(TAG, "Getting password credentials for package: $callingPackage, domain: $webDomain")

            // Build request for password credentials
            val getPasswordOption = GetPasswordOption()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(getPasswordOption)
                .build()

            // Get credentials from Credential Manager
            val result = credentialManager.getCredential(
                context = context,
                request = request
            )

            handleGetCredentialResponse(result)
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Error getting credentials: ${e.message}", e)
            null
        }
    }

    /**
     * Saves a password credential to TrustVault via Credential Manager.
     *
     * This is called when an app uses Credential Manager to save credentials.
     * The credential is encrypted and stored in TrustVault database.
     *
     * @param username The username/email
     * @param password The password
     * @param packageName Package name of the app
     * @param webDomain Optional web domain
     * @return true if saved successfully
     */
    suspend fun savePasswordCredential(
        username: String,
        password: String,
        packageName: String,
        webDomain: String? = null
    ): Boolean {
        return try {
            Log.d(TAG, "Saving password credential for package: $packageName")

            // Create the password credential
            val request = CreatePasswordRequest(
                id = username,
                password = password
            )

            // Save via Credential Manager (registers with Android)
            val result = credentialManager.createCredential(
                context = context,
                request = request
            ) as CreatePasswordResponse

            // Also save to TrustVault database
            saveToVault(
                username = username,
                password = password,
                packageName = packageName,
                webDomain = webDomain
            )

            Log.d(TAG, "Password credential saved successfully")
            true
        } catch (e: CreateCredentialException) {
            Log.e(TAG, "Error saving credential: ${e.message}", e)
            false
        }
    }

    /**
     * Retrieves all credentials from vault that match the given criteria.
     * Used to populate Credential Manager with available credentials.
     *
     * @param packageName App package name
     * @param webDomain Optional web domain
     * @return List of matching credentials
     */
    suspend fun getMatchingCredentials(
        packageName: String,
        webDomain: String? = null
    ): List<Credential> {
        val allCredentials = credentialRepository.getAllCredentials().first()

        return allCredentials.filter { credential ->
            // Match by package name
            if (credential.packageName.isNotEmpty() && credential.packageName == packageName) {
                return@filter true
            }

            // Match by web domain
            if (webDomain != null && credential.website.isNotEmpty()) {
                val credentialDomain = extractDomain(credential.website)
                val requestDomain = extractDomain(webDomain)

                if (credentialDomain == requestDomain ||
                    credentialDomain.contains(requestDomain) ||
                    requestDomain.contains(credentialDomain)) {
                    return@filter true
                }
            }

            false
        }
    }

    /**
     * Checks if passkey/WebAuthn support is available on this device.
     *
     * Requires Android 14+ with hardware support.
     * See PasskeyManager for detailed requirements.
     *
     * @return true if passkeys are available
     */
    suspend fun isPasskeyAvailable(): Boolean {
        return passkeyManager.isPasskeyAvailable()
    }

    /**
     * Initiates passkey registration (creates a new passkey).
     *
     * This delegates to PasskeyManager which handles:
     * - Challenge validation
     * - Request building
     * - Device biometric/PIN verification
     * - Response parsing
     *
     * **Server Integration Required**:
     * 1. Server generates challenge: 32-64 random bytes, base64url encoded
     * 2. Server provides RP ID (relying party identifier)
     * 3. Client sends attestation response to server for verification
     * 4. Server verifies using FIDO2 library (e.g., java-webauthn-server)
     * 5. Server stores public key for future authentications
     *
     * See PASSKEY_SERVER_INTEGRATION.md for implementation details.
     *
     * @param rpId Relying Party identifier (e.g., "example.com")
     * @param rpName Relying Party display name (e.g., "Example Service")
     * @param userId Server-provided user ID (base64url encoded)
     * @param userName User's identifier (typically email)
     * @param displayName User's display name
     * @param challenge Server-generated challenge (base64url encoded, 32-64 bytes)
     * @return PublicKeyCredentialModel if successful, null if cancelled or error
     */
    suspend fun registerPasskey(
        rpId: String,
        rpName: String,
        userId: String,
        userName: String,
        displayName: String,
        challenge: String
    ): PublicKeyCredentialModel? {
        return passkeyManager.createPasskeyCredential(
            rpId = rpId,
            rpName = rpName,
            userId = userId,
            userName = userName,
            displayName = displayName,
            challenge = challenge
        )
    }

    /**
     * Authenticates with a passkey (proves possession without exposing key).
     *
     * This delegates to PasskeyManager which handles:
     * - Challenge validation
     * - Request building
     * - Device biometric/PIN verification
     * - Signature generation
     * - Response parsing
     *
     * **Server Integration Required**:
     * 1. Server generates challenge: 32-64 random bytes, base64url encoded
     * 2. Server provides RP ID and optionally allowed credential IDs
     * 3. Client sends assertion response to server for verification
     * 4. Server verifies signature using stored public key
     * 5. Server validates counter to detect cloning attacks
     *
     * See PASSKEY_SERVER_INTEGRATION.md for implementation details.
     *
     * @param rpId Relying Party identifier (e.g., "example.com")
     * @param challenge Server-generated challenge (base64url encoded, 32-64 bytes)
     * @param allowCredentials Optional list of credential IDs to allow
     * @return PublicKeyCredentialModel if successful, null if cancelled or error
     */
    suspend fun authenticateWithPasskey(
        rpId: String,
        challenge: String,
        allowCredentials: List<String>? = null
    ): PublicKeyCredentialModel? {
        return passkeyManager.getPasskeyAssertion(
            rpId = rpId,
            challenge = challenge,
            allowCredentials = allowCredentials
        )
    }

    /**
     * Stores a passkey credential to TrustVault for recovery/management.
     *
     * Stores metadata about the passkey (not the private key, which stays in secure enclave).
     * Used for:
     * - Display in credential list
     * - Account recovery information
     * - Device management (view/delete passkeys)
     *
     * @param credential PublicKeyCredentialModel from registration
     * @param rpId Relying Party identifier
     * @return true if stored successfully
     */
    suspend fun storePasskeyCredential(
        credential: PublicKeyCredentialModel,
        rpId: String
    ): Boolean {
        return passkeyManager.storePasskeyCredential(credential, rpId)
    }

    /**
     * Retrieves passkey credentials for a specific service (RP).
     *
     * Used for device/account management UI.
     *
     * @param rpId Relying Party identifier
     * @return List of passkey credentials for this service
     */
    suspend fun getPasskeyCredentialsForService(rpId: String): List<Credential> {
        return passkeyManager.getPasskeyCredentialsForRp(rpId)
    }

    /**
     * Converts a TrustVault credential to PasswordCredential for Credential Manager.
     */
    fun toPasswordCredential(credential: Credential): PasswordCredential {
        return PasswordCredential(
            id = credential.username,
            password = credential.password
        )
    }

    /**
     * Handles the response from Credential Manager get request.
     */
    private fun handleGetCredentialResponse(response: GetCredentialResponse): PasswordCredential? {
        return when (val credential = response.credential) {
            is PasswordCredential -> {
                Log.d(TAG, "Received password credential: ${credential.id}")
                credential
            }
            else -> {
                Log.w(TAG, "Received unsupported credential type: ${credential.type}")
                null
            }
        }
    }

    /**
     * Saves credential to TrustVault database.
     */
    private suspend fun saveToVault(
        username: String,
        password: String,
        packageName: String,
        webDomain: String?
    ) {
        // Check if credential already exists
        val existing = credentialRepository.getAllCredentials().first()
            .find {
                it.packageName == packageName &&
                it.username.equals(username, ignoreCase = true)
            }

        if (existing != null) {
            // Update existing credential
            val updated = existing.copy(
                password = password,
                updatedAt = System.currentTimeMillis()
            )
            credentialRepository.updateCredential(updated)
        } else {
            // Create new credential
            val title = generateTitle(packageName, webDomain)
            val credential = Credential(
                id = 0,
                title = title,
                username = username,
                password = password,
                website = webDomain ?: "",
                packageName = packageName,
                notes = "Saved via Credential Manager",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            credentialRepository.insertCredential(credential)
        }
    }

    /**
     * Generates a user-friendly title for a credential.
     */
    private fun generateTitle(packageName: String, webDomain: String?): String {
        return when {
            webDomain != null -> {
                webDomain.removePrefix("www.")
                    .substringBefore(".")
                    .replaceFirstChar { it.uppercase() }
            }
            else -> {
                packageName.substringAfterLast(".")
                    .replaceFirstChar { it.uppercase() }
            }
        }
    }

    /**
     * Extracts domain from URL for matching.
     */
    private fun extractDomain(url: String): String {
        return try {
            val normalized = url.trim().lowercase()
                .removePrefix("http://")
                .removePrefix("https://")
                .substringBefore("/")
                .substringBefore(":")
            normalized.removePrefix("www.")
        } catch (_: Exception) {
            url.lowercase()
        }
    }
}

