package com.trustvault.android.presentation.ui.screens.importexport

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
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
import com.trustvault.android.presentation.viewmodel.CsvImportViewModel

/**
 * CSV import screen for manual CSV content input.
 * Users can paste CSV content which is then parsed and imported.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsvImportScreen(
    onNavigateBack: () -> Unit,
    viewModel: CsvImportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var csvContent by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import CSV") },
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
        when (uiState) {
            is CsvImportViewModel.UiState.Idle, is CsvImportViewModel.UiState.AwaitingFile -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "Paste CSV Content",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp, max = 400.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        TextField(
                            value = csvContent,
                            onValueChange = { csvContent = it },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            placeholder = {
                                Text(
                                    "title,username,password,website,category,notes\nPayPal,user@email.com,password123,https://paypal.com,LOGIN,Main account",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            textStyle = MaterialTheme.typography.bodySmall
                        )
                    }

                    Text(
                        "Supported columns: title, username, password, website, category, notes, otp_secret, package_name",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = { viewModel.loadCsvFile(csvContent) },
                        modifier = Modifier
                            .align(Alignment.End)
                            .heightIn(min = 40.dp),
                        enabled = csvContent.isNotBlank()
                    ) {
                        Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Parse & Preview")
                    }
                }
            }

            is CsvImportViewModel.UiState.ParsedCsv -> {
                val preview = (uiState as CsvImportViewModel.UiState.ParsedCsv).preview
                CsvPreviewContent(
                    preview = preview,
                    onImport = { viewModel.executeImport() },
                    onBack = {
                        viewModel.resetUiState()
                        csvContent = ""
                    },
                    padding = padding
                )
            }

            is CsvImportViewModel.UiState.Importing -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator()
                        Text("Importing credentials...")
                    }
                }
            }

            is CsvImportViewModel.UiState.ImportSuccess -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Import Successful!",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "${(uiState as CsvImportViewModel.UiState.ImportSuccess).count} credentials imported",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button(
                            onClick = {
                                viewModel.resetUiState()
                                csvContent = ""
                                onNavigateBack()
                            }
                        ) {
                            Text("Done")
                        }
                    }
                }
            }

            is CsvImportViewModel.UiState.ImportError -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(
                            Icons.Filled.Error,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            "Import Failed",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            (uiState as CsvImportViewModel.UiState.ImportError).message,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Button(
                            onClick = {
                                viewModel.resetUiState()
                                csvContent = ""
                            }
                        ) {
                            Text("Try Again")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CsvPreviewContent(
    preview: com.trustvault.android.data.importexport.ImportPreview,
    onImport: () -> Unit,
    onBack: () -> Unit,
    padding: PaddingValues
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Summary
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Import Preview",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "${preview.totalCount} credentials found",
                    style = MaterialTheme.typography.bodySmall
                )
                if (preview.conflictCount > 0) {
                    Text(
                        "⚠ ${preview.conflictCount} conflicts detected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (preview.warningCount > 0) {
                    Text(
                        "ℹ ${preview.warningCount} warnings",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // Warnings
        if (preview.warnings.isNotEmpty()) {
            Text(
                "Warnings",
                style = MaterialTheme.typography.labelLarge
            )
            preview.warnings.forEach { warning ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Text(
                        "• $warning",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        // Credentials Summary
        Text(
            "Credentials to Import",
            style = MaterialTheme.typography.labelLarge
        )
        preview.credentials.take(5).forEach { credential ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        credential.title,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        credential.username,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (credential.website.isNotBlank()) {
                        Text(
                            credential.website,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (preview.totalCount > 5) {
            Text(
                "... and ${preview.totalCount - 5} more",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f)
            ) {
                Text("Back")
            }
            Button(
                onClick = onImport,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Import")
            }
        }
    }
}
