package com.trustvault.android.autofill

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.trustvault.android.domain.model.Credential
import com.trustvault.android.domain.repository.CredentialRepository
import com.trustvault.android.presentation.ui.theme.TrustVaultTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Activity that prompts the user to save or update autofilled credentials.
 *
 * Security Features:
 * - User must explicitly confirm save/update
 * - Shows what will be saved (username, app/website)
 * - Never displays password in clear text
 * - Updates existing credential if match found
 */
@AndroidEntryPoint
class AutofillSaveActivity : ComponentActivity() {

    @Inject
    lateinit var credentialRepository: CredentialRepository

    private val savedCredential: SavedCredential? by lazy {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_SAVED_CREDENTIAL, SavedCredential::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_SAVED_CREDENTIAL)
        }
    }

    private val existingCredential: Credential? by lazy {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_EXISTING_CREDENTIAL, Credential::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_EXISTING_CREDENTIAL)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val saved = savedCredential
        if (saved == null) {
            finish()
            return
        }

        setContent {
            TrustVaultTheme {
                SaveCredentialDialog(
                    savedCredential = saved,
                    existingCredential = existingCredential,
                    onSave = { title -> handleSave(saved, title) },
                    onUpdate = { handleUpdate(saved) },
                    onDismiss = { finish() }
                )
            }
        }
    }

    private fun handleSave(saved: SavedCredential, title: String) {
        lifecycleScope.launch {
            try {
                // Create new credential
                val credential = Credential(
                    id = 0, // Room will auto-generate
                    title = title,
                    username = saved.username,
                    password = saved.password,
                    website = saved.webDomain ?: "",
                    packageName = saved.packageName,
                    notes = "Saved via autofill",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                credentialRepository.insertCredential(credential)
                finish()
            } catch (_: Exception) {
                // TODO: Show error to user
                finish()
            }
        }
    }

    private fun handleUpdate(saved: SavedCredential) {
        lifecycleScope.launch {
            try {
                val existing = existingCredential ?: return@launch

                // Update existing credential with new password
                val updated = existing.copy(
                    password = saved.password,
                    updatedAt = System.currentTimeMillis()
                )

                credentialRepository.updateCredential(updated)
                finish()
            } catch (_: Exception) {
                // TODO: Show error to user
                finish()
            }
        }
    }

    companion object {
        private const val EXTRA_SAVED_CREDENTIAL = "saved_credential"
        private const val EXTRA_EXISTING_CREDENTIAL = "existing_credential"

        fun createIntent(
            context: Context,
            savedCredential: SavedCredential,
            existingCredential: Credential?
        ): Intent {
            return Intent(context, AutofillSaveActivity::class.java).apply {
                putExtra(EXTRA_SAVED_CREDENTIAL, savedCredential)
                if (existingCredential != null) {
                    putExtra(EXTRA_EXISTING_CREDENTIAL, existingCredential)
                }
            }
        }
    }
}

@Composable
fun SaveCredentialDialog(
    savedCredential: SavedCredential,
    existingCredential: Credential?,
    onSave: (title: String) -> Unit,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(generateDefaultTitle(savedCredential, existingCredential)) }
    val isUpdate = existingCredential != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isUpdate) "Update Credential?" else "Save Credential?",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isUpdate) {
                    Text(
                        text = "Update password for:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = existingCredential!!.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        text = "Save this credential to TrustVault?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                // Show username
                InfoRow(label = "Username", value = savedCredential.username)

                // Show app/website
                val appInfo = savedCredential.webDomain ?: savedCredential.packageName
                InfoRow(label = if (savedCredential.webDomain != null) "Website" else "App", value = appInfo)

                // Security note
                Text(
                    text = "Password will be encrypted and stored securely.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isUpdate) {
                        onUpdate()
                    } else {
                        if (title.isNotBlank()) {
                            onSave(title.trim())
                        }
                    }
                },
                enabled = !isUpdate || title.isNotBlank()
            ) {
                Text(if (isUpdate) "Update" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun generateDefaultTitle(saved: SavedCredential, existing: Credential?): String {
    if (existing != null) {
        return existing.title
    }

    return when {
        saved.webDomain != null -> {
            // Extract domain name as title
            saved.webDomain.removePrefix("www.")
                .substringBefore(".")
                .replaceFirstChar { it.uppercase() }
        }
        else -> {
            // Use package name as title
            saved.packageName.substringAfterLast(".")
                .replaceFirstChar { it.uppercase() }
        }
    }
}

