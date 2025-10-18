package com.trustvault.android.presentation.ui.screens.importexport

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
                onClick = { viewModel.exportToCSV() },
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
            when (uiState) {
                is ImportExportViewModel.UiState.Success -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    SucessMessageCard((uiState as ImportExportViewModel.UiState.Success).message)
                }
                is ImportExportViewModel.UiState.Error -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    ErrorMessageCard((uiState as ImportExportViewModel.UiState.Error).message)
                }
                is ImportExportViewModel.UiState.ExportReady -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    ExportReadyCard(
                        fileName = (uiState as ImportExportViewModel.UiState.ExportReady).fileName,
                        onDismiss = { viewModel.resetUiState() }
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
private fun ExportReadyCard(fileName: String, onDismiss: () -> Unit) {
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
                    Icons.Filled.Download,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    "Export ready: $fileName",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Button(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("OK")
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
