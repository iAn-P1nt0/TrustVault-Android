package com.trustvault.android.presentation.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Settings screen for app configuration.
 *
 * Currently provides:
 * - Lock app option
 * - About/version information
 *
 * Future enhancements:
 * - Biometric settings
 * - Auto-lock timeout configuration
 * - Theme preferences
 * - Backup/restore settings
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onLockApp: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Lock App Button
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Button(
                    onClick = onLockApp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text("Lock App")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // About Section
            Text(
                "About TrustVault",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    InfoRow("Version", "1.0.0")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    InfoRow("Security", "OWASP 2025 Compliant")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    InfoRow("Encryption", "AES-256-GCM")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Features
            Text(
                "Features",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    FeatureItem("✓ Password Management")
                    FeatureItem("✓ TOTP/2FA Support")
                    FeatureItem("✓ Biometric Authentication")
                    FeatureItem("✓ Local Encryption")
                    FeatureItem("✓ No Cloud Sync")
                    FeatureItem("✓ Privacy-First Design")
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun FeatureItem(feature: String) {
    Text(
        feature,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}
