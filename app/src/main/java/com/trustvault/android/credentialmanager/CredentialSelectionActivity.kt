package com.trustvault.android.credentialmanager

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.credentials.provider.PendingIntentHandler
import androidx.credentials.GetCredentialResponse
import androidx.credentials.PasswordCredential
import androidx.credentials.provider.ProviderGetCredentialRequest
import com.trustvault.android.domain.repository.CredentialRepository
import com.trustvault.android.security.DatabaseKeyManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.activity.ComponentActivity

/**
 * Activity that handles credential selection from Credential Manager.
 *
 * This activity is launched when the user selects a credential from the
 * Credential Manager bottom sheet. It retrieves the selected credential
 * and returns it to the calling app.
 *
 * Flow:
 * 1. User taps credential in Credential Manager sheet
 * 2. This activity is launched via PendingIntent
 * 3. Activity retrieves credential from database
 * 4. Returns credential to Credential Manager
 * 5. Credential Manager passes it to calling app
 */
@AndroidEntryPoint
class CredentialSelectionActivity : ComponentActivity() {

    @Inject
    lateinit var credentialRepository: CredentialRepository

    @Inject
    lateinit var databaseKeyManager: DatabaseKeyManager

    private val activityScope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleCredentialSelection()
    }

    private fun handleCredentialSelection() {
        activityScope.launch {
            try {
                // Check if database is initialized (user has authenticated)
                if (!databaseKeyManager.isDatabaseInitialized()) {
                    // Database not ready - user hasn't authenticated yet
                    setResult(RESULT_CANCELED)
                    finish()
                    return@launch
                }

                val credentialId = intent.getLongExtra("credential_id", -1L)

                if (credentialId == -1L) {
                    setResult(RESULT_CANCELED)
                    finish()
                    return@launch
                }

                // Get credential from database
                val credential = credentialRepository.getAllCredentials().first()
                    .find { it.id == credentialId }

                if (credential == null) {
                    setResult(RESULT_CANCELED)
                    finish()
                    return@launch
                }

                // Create response with the selected credential. These APIs require
                // Android 34+; guard with runtime check to keep minSdk 26 compatible.
                if (Build.VERSION.SDK_INT >= 34) {
                    val result = PendingIntentHandler.retrieveProviderGetCredentialRequest(intent)
                    if (result != null) {
                        val response = GetCredentialResponse(
                            PasswordCredential(
                                id = credential.username,
                                password = credential.password
                            )
                        )

                        val resultIntent = Intent()
                        PendingIntentHandler.setGetCredentialResponse(resultIntent, response)
                        setResult(RESULT_OK, resultIntent)
                    } else {
                        setResult(RESULT_CANCELED)
                    }
                } else {
                    // Not supported on older Android versions
                    setResult(RESULT_CANCELED)
                }

                finish()
            } catch (e: Exception) {
                setResult(RESULT_CANCELED)
                finish()
            }
        }
    }
}
