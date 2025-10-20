package com.trustvault.android.presentation.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * BiometricSetupDialog - Prompts user to enable biometric unlock.
 *
 * **Usage:** Show after successful vault creation or password unlock.
 *
 * @param onEnableClick Callback when user clicks "Enable" button
 * @param onNotNowClick Callback when user clicks "Not Now" button
 * @param onNeverAskClick Callback when user clicks "Never Ask Again" button
 * @param onDismiss Callback when dialog is dismissed
 */
@Composable
fun BiometricSetupDialog(
    onEnableClick: () -> Unit,
    onNotNowClick: () -> Unit,
    onNeverAskClick: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Fingerprint,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Enable Biometric Unlock?",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Unlock your vault quickly and securely with your fingerprint or face.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Your master password will be encrypted and stored securely in the Android Keystore.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onEnableClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Enable Biometric Unlock")
            }
        },
        dismissButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onNotNowClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Not Now")
                }

                TextButton(
                    onClick = onNeverAskClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Never Ask Again",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    )
}

/**
 * BiometricInvalidatedDialog - Shows when biometric key is invalidated.
 *
 * @param onReEnable Callback to re-enable biometric unlock
 * @param onDismiss Callback when dialog is dismissed
 */
@Composable
fun BiometricInvalidatedDialog(
    onReEnable: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Fingerprint,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                text = "Biometric Changed",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Text(
                text = "Your biometrics have changed. For security, biometric unlock has been disabled. " +
                        "You can re-enable it in Settings.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(onClick = onReEnable) {
                Text("Re-enable in Settings")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    )
}

