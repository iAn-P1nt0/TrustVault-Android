package com.trustvault.android.presentation.ui.screens.ocr.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * Permission rationale dialog for camera access.
 *
 * Explains why camera permission is needed before requesting it.
 * Required by Android best practices and OWASP M8 (Security Misconfiguration).
 *
 * Design:
 * - Clear explanation of feature
 * - Privacy guarantee (on-device processing)
 * - Grant/Deny buttons
 * - Material 3 AlertDialog
 *
 * @param onConfirm User taps "Grant Permission" - launches permission request
 * @param onDismiss User taps "Cancel" or dismisses dialog
 */
@Composable
fun PermissionRationaleDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.CameraAlt,
                contentDescription = null
            )
        },
        title = {
            Text("Camera Permission Required")
        },
        text = {
            Text(
                text = "TrustVault needs camera access to scan login credentials from browser screenshots.\n\n" +
                        "• All processing happens on your device\n" +
                        "• Images are never saved or shared\n" +
                        "• No data sent to external servers\n\n" +
                        "Your privacy is protected."
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Grant Permission")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Permission permanently denied dialog.
 *
 * Shown when user has permanently denied camera permission.
 * Instructs user to enable in system settings.
 *
 * @param onDismiss User taps "OK" or dismisses dialog
 */
@Composable
fun PermissionDeniedDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.CameraAlt,
                contentDescription = null
            )
        },
        title = {
            Text("Camera Permission Denied")
        },
        text = {
            Text(
                text = "Camera permission is required for OCR scanning.\n\n" +
                        "To enable:\n" +
                        "1. Open Android Settings\n" +
                        "2. Go to Apps → TrustVault\n" +
                        "3. Tap Permissions → Camera\n" +
                        "4. Select \"Allow\"\n\n" +
                        "You can still enter credentials manually."
            )
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}
