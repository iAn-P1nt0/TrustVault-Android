package com.trustvault.android.presentation.ui.screens.importexport

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.trustvault.android.presentation.viewmodel.ImportExportViewModel
import androidx.compose.foundation.border

/**
 * Main import/export hub screen.
 * Provides access to CSV export, CSV import, and backup management.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportExportScreen(
    onNavigateBack: () -> Unit,
    onNavigateToCsvImport: () -> Unit,
    onNavigateToBackupManagement: () -> Unit,
    viewModel: ImportExportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // State for export confirmation dialog
    var showExportConfirmation by remember { mutableStateOf(false) }

    // Export confirmation dialog
    if (showExportConfirmation) {
        ExportConfirmationDialog(
            onConfirm = {
                showExportConfirmation = false
                viewModel.exportToCSV()
            },
            onDismiss = {
                showExportConfirmation = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import & Export") },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Export Section
            Text(
                "Export Credentials",
                style = MaterialTheme.typography.titleMedium
            )

            ExportOptionCard(
                title = "Export to CSV",
                description = "Download all credentials as CSV file",
                icon = Icons.Filled.FilePresent,
                onClick = { showExportConfirmation = true },
                isLoading = uiState is ImportExportViewModel.UiState.Loading
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Import Section
            Text(
                "Import Credentials",
                style = MaterialTheme.typography.titleMedium
            )

            ImportOptionCard(
                title = "Import from CSV",
                description = "Import credentials from CSV file with field mapping",
                icon = Icons.Filled.UploadFile,
                onClick = onNavigateToCsvImport
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Backup Section
            Text(
                "Backup & Restore",
                style = MaterialTheme.typography.titleMedium
            )

            ImportOptionCard(
                title = "Manage Backups",
                description = "Create, restore, or delete encrypted backups",
                icon = Icons.Filled.CloudSync,
                onClick = onNavigateToBackupManagement
            )

            // Status Messages
            when (val state = uiState) {
                is ImportExportViewModel.UiState.Success -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    SucessMessageCard(state.message)
                }
                is ImportExportViewModel.UiState.Error -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    ErrorMessageCard(state.message)
                }
                is ImportExportViewModel.UiState.ExportReady -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    ExportReadyCard(
                        fileName = state.fileName,
                        fileUri = state.fileUri,
                        onDismiss = { viewModel.resetUiState() },
                        onShare = {
                            state.fileUri?.let { uri ->
                                val shareIntent = viewModel.getShareIntent(uri, state.fileName)
                                context.startActivity(Intent.createChooser(shareIntent, "Share CSV Export"))
                            }
                        }
                    )
                }
                else -> {}
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Information Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Security Features",
                        style = MaterialTheme.typography.labelLarge
                    )
                    InfoItem("✓ All backups are AES-256-GCM encrypted")
                    InfoItem("✓ CSV exports are unencrypted for compatibility")
                    InfoItem("✓ Conflicts detected and user-resolvable")
                    InfoItem("✓ Memory properly wiped after operations")
                }
            }
        }
    }
}

@Composable
private fun ExportOptionCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    isLoading: Boolean = false
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Button(
                    onClick = onClick,
                    modifier = Modifier.wrapContentWidth()
                ) {
                    Text("Export")
                }
            }
        }
    }
}

@Composable
private fun ImportOptionCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = onClick,
                modifier = Modifier.wrapContentWidth()
            ) {
                Text("Open")
            }
        }
    }
}

@Composable
private fun SucessMessageCard(message: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.primary, shape = CardDefaults.shape),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(message, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ErrorMessageCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Filled.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Text(message, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ExportReadyCard(
    fileName: String,
    fileUri: android.net.Uri?,
    onDismiss: () -> Unit,
    onShare: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Column {
                    Text(
                        "Export Complete!",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        fileName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                "Your credentials have been exported to a CSV file. You can now share it or save it to your device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.9f)
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.3f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Share button
                OutlinedButton(
                    onClick = onShare,
                    modifier = Modifier.weight(1f),
                    enabled = fileUri != null
                ) {
                    Icon(
                        Icons.Filled.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share")
                }

                // Done button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Done")
                }
            }

            // Security warning
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    "⚠️ Warning: CSV exports are unencrypted. Handle with care and delete after use.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun InfoItem(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(start = 0.dp, top = 4.dp, bottom = 4.dp)
    )
}

@Composable
private fun ExportConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text("Export to CSV?")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "You are about to export all your credentials to an unencrypted CSV file.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                Icons.Filled.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                "Security Warning",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Text(
                            "• CSV files are NOT encrypted\n" +
                            "• Your passwords will be visible in plain text\n" +
                            "• Anyone with access to the file can read your credentials\n" +
                            "• Delete the file after use",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                Text(
                    "Only proceed if you understand the security risks and need to transfer credentials to another password manager.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Export Anyway")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
