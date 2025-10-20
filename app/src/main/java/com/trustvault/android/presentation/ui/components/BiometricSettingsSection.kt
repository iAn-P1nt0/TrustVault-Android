package com.trustvault.android.presentation.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.trustvault.android.presentation.viewmodel.BiometricViewModel
import com.trustvault.android.security.biometric.BiometricAvailability

/**
 * BiometricSettingsSection - UI component for biometric authentication settings.
 *
 * Allows users to:
 * - Enable biometric unlock
 * - Disable biometric unlock
 * - View biometric availability status
 */
@Composable
fun BiometricSettingsSection(
    viewModel: BiometricViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showPasswordPrompt by remember { mutableStateOf(false) }
    var showDisableConfirmation by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Security",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Biometric unlock switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Fingerprint,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = if (uiState.isBiometricEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Biometric Unlock",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = when {
                                    uiState.isBiometricEnabled -> "Enabled"
                                    uiState.biometricAvailability is BiometricAvailability.Available -> "Disabled"
                                    uiState.biometricAvailability is BiometricAvailability.NoneEnrolled -> "No biometrics enrolled"
                                    uiState.biometricAvailability is BiometricAvailability.NoHardware -> "Not available on this device"
                                    else -> "Not available"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = uiState.isBiometricEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                // Show password prompt to enable biometric
                                showPasswordPrompt = true
                            } else {
                                // Show confirmation dialog to disable
                                showDisableConfirmation = true
                            }
                        },
                        enabled = !uiState.isLoading &&
                                 uiState.biometricAvailability is BiometricAvailability.Available
                    )
                }

                // Show status message if biometric is not available
                if (uiState.biometricAvailability !is BiometricAvailability.Available) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (uiState.biometricAvailability) {
                                is BiometricAvailability.NoneEnrolled ->
                                    "Add fingerprint or face in device Settings to use biometric unlock."
                                is BiometricAvailability.NoHardware ->
                                    "This device doesn't support biometric authentication."
                                is BiometricAvailability.HardwareUnavailable ->
                                    "Biometric hardware is temporarily unavailable. Try again later."
                                is BiometricAvailability.SecurityUpdateRequired ->
                                    "A security update is required for biometric authentication."
                                else ->
                                    "Biometric authentication is not available."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Show error message
                if (uiState.errorMessage != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = uiState.errorMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                // Show success message
                if (uiState.successMessage != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = uiState.successMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Description
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Unlock your vault with fingerprint or face recognition. " +
                   "Your master password is encrypted and stored securely in Android Keystore.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }

    // Password prompt dialog for enabling biometric
    if (showPasswordPrompt) {
        PasswordPromptDialog(
            onConfirm = { password ->
                val activity = context as? FragmentActivity
                if (activity != null) {
                    viewModel.setupBiometricUnlock(
                        activity = activity,
                        masterPassword = password.toCharArray(),
                        onSuccess = {
                            showPasswordPrompt = false
                        }
                    )
                }
            },
            onDismiss = {
                showPasswordPrompt = false
            }
        )
    }

    // Disable confirmation dialog
    if (showDisableConfirmation) {
        AlertDialog(
            onDismissRequest = { showDisableConfirmation = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Fingerprint,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Disable Biometric Unlock?") },
            text = {
                Text("You'll need to enter your master password to unlock your vault.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.disableBiometricUnlock()
                        showDisableConfirmation = false
                    }
                ) {
                    Text("Disable")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisableConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Clear messages after display
    LaunchedEffect(uiState.errorMessage, uiState.successMessage) {
        if (uiState.errorMessage != null || uiState.successMessage != null) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearMessages()
        }
    }
}

/**
 * Password prompt dialog for enabling biometric unlock.
 */
@Composable
private fun PasswordPromptDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter Master Password") },
        text = {
            Column {
                Text(
                    text = "Enter your master password to enable biometric unlock.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Master Password") },
                    visualTransformation = if (passwordVisible) {
                        androidx.compose.ui.text.input.VisualTransformation.None
                    } else {
                        androidx.compose.ui.text.input.PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) {
                                    Icons.Filled.Visibility
                                } else {
                                    Icons.Filled.VisibilityOff
                                },
                                contentDescription = if (passwordVisible) "Hide password" else "Show password"
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (password.isNotEmpty()) {
                        onConfirm(password)
                        password = "" // Clear password from memory
                    }
                },
                enabled = password.isNotEmpty()
            ) {
                Text("Enable")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                password = "" // Clear password from memory
                onDismiss()
            }) {
                Text("Cancel")
            }
        }
    )
}

