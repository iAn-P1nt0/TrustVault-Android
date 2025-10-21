package com.trustvault.android.presentation.ui.screens.privacy

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.trustvault.android.compliance.PrivacyManager
import com.trustvault.android.presentation.viewmodel.PrivacyDashboardViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * Privacy Dashboard Screen - GDPR & DPDP Compliance UI
 *
 * Displays comprehensive privacy information to users:
 * - Data collection practices
 * - Consent status for all processing purposes
 * - Data retention policies
 * - Data export (GDPR Article 20)
 * - Data erasure (GDPR Article 17)
 * - Privacy policy information
 *
 * **GDPR Articles 13-14:** Transparency and information to data subjects
 * **DPDP Act Section 4:** Information to data principals
 *
 * @param onNavigateBack Navigation callback
 * @param viewModel Privacy dashboard state management
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyDashboardScreen(
    onNavigateBack: () -> Unit,
    viewModel: PrivacyDashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy & Data Protection") },
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                uiState.dashboardData != null -> {
                    PrivacyDashboardContent(
                        dashboardData = uiState.dashboardData!!,
                        uiState = uiState,
                        onConsentClick = { purpose -> viewModel.showConsentDialog(purpose) },
                        onRetentionPolicyClick = { viewModel.showRetentionPolicyDialog() },
                        onDataExportClick = { viewModel.showDataExportDialog() },
                        onDataErasureClick = { viewModel.showDataErasureDialog() },
                        onEnforceRetentionPolicy = { viewModel.enforceRetentionPolicy() }
                    )
                }
            }

            // Error snackbar
            uiState.error?.let { error ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("Dismiss")
                        }
                    }
                ) {
                    Text(error)
                }
            }
        }
    }

    // Consent dialog
    uiState.showConsentDialog?.let { purpose ->
        ConsentDialog(
            purpose = purpose,
            currentConsent = uiState.consentRecords[purpose]?.granted ?: false,
            onDismiss = { viewModel.hideConsentDialog() },
            onConfirm = { granted -> viewModel.updateConsent(purpose, granted) }
        )
    }

    // Retention policy dialog
    if (uiState.showRetentionPolicyDialog) {
        RetentionPolicyDialog(
            currentPolicy = uiState.retentionPolicy?.retentionDays ?: -1,
            onDismiss = { viewModel.hideRetentionPolicyDialog() },
            onConfirm = { days -> viewModel.updateRetentionPolicy(days) }
        )
    }

    // Data export dialog
    if (uiState.showDataExportDialog) {
        DataExportDialog(
            onDismiss = { viewModel.hideDataExportDialog() },
            onExport = { format, scope, encrypt, password ->
                viewModel.exportData(format, scope, encrypt, password)
            },
            exportResult = uiState.exportResult,
            isExporting = uiState.exportInProgress
        )
    }

    // Data erasure dialog
    if (uiState.showDataErasureDialog) {
        DataErasureDialog(
            onDismiss = { viewModel.hideDataErasureDialog() },
            onConfirm = { deleteMasterPassword, deleteAuditLogs, masterPassword ->
                viewModel.executeDataErasure(deleteMasterPassword, deleteAuditLogs, masterPassword)
            },
            erasureResult = uiState.erasureResult,
            isErasing = uiState.erasureInProgress
        )
    }
}

/**
 * Main privacy dashboard content.
 */
@Composable
private fun PrivacyDashboardContent(
    dashboardData: PrivacyManager.PrivacyDashboardData,
    uiState: PrivacyDashboardViewModel.PrivacyDashboardUiState,
    onConsentClick: (PrivacyManager.DataProcessingPurpose) -> Unit,
    onRetentionPolicyClick: () -> Unit,
    onDataExportClick: () -> Unit,
    onDataErasureClick: () -> Unit,
    onEnforceRetentionPolicy: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "Your Privacy Rights",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "TrustVault is committed to protecting your privacy in compliance with GDPR and DPDP Act 2023.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Privacy Policy Section
        PrivacyPolicySection(dashboardData)

        Spacer(modifier = Modifier.height(16.dp))

        // Data Collection Section
        DataCollectionSection(dashboardData)

        Spacer(modifier = Modifier.height(16.dp))

        // Consent Management Section
        ConsentManagementSection(
            dashboardData = dashboardData,
            consentRecords = uiState.consentRecords,
            onConsentClick = onConsentClick
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Data Retention Section
        DataRetentionSection(
            retentionPolicy = uiState.retentionPolicy,
            expiredCount = uiState.expiredCredentialsCount,
            onRetentionPolicyClick = onRetentionPolicyClick,
            onEnforcePolicy = onEnforceRetentionPolicy
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Your Data Rights Section
        YourDataRightsSection(
            onDataExportClick = onDataExportClick,
            onDataErasureClick = onDataErasureClick
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Compliance Information
        ComplianceInfoSection(dashboardData)

        Spacer(modifier = Modifier.height(32.dp))
    }
}

/**
 * Privacy policy section.
 */
@Composable
private fun PrivacyPolicySection(dashboardData: PrivacyManager.PrivacyDashboardData) {
    SectionCard(title = "Privacy Policy") {
        InfoRow(
            label = "Version",
            value = dashboardData.privacyPolicyVersion
        )

        if (dashboardData.privacyPolicyAcceptedDate > 0) {
            InfoRow(
                label = "Accepted On",
                value = formatDate(dashboardData.privacyPolicyAcceptedDate)
            )
        }

        InfoRow(
            label = "Region",
            value = when {
                dashboardData.isGdprRegion -> "EU/EEA (GDPR)"
                dashboardData.isDpdpRegion -> "India (DPDP Act)"
                else -> "Global"
            }
        )
    }
}

/**
 * Data collection information section.
 */
@Composable
private fun DataCollectionSection(dashboardData: PrivacyManager.PrivacyDashboardData) {
    SectionCard(title = "Data Collection") {
        Text(
            text = "Data We Collect:",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        dashboardData.dataCollected.forEach { item ->
            Row(
                modifier = Modifier.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(item, style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Data We DO NOT Collect:",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        dashboardData.dataNotCollected.forEach { item ->
            Row(
                modifier = Modifier.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(item, style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = dashboardData.dataStorage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

/**
 * Consent management section.
 */
@Composable
private fun ConsentManagementSection(
    dashboardData: PrivacyManager.PrivacyDashboardData,
    consentRecords: Map<PrivacyManager.DataProcessingPurpose, com.trustvault.android.compliance.ConsentManager.ConsentRecord>,
    onConsentClick: (PrivacyManager.DataProcessingPurpose) -> Unit
) {
    SectionCard(title = "Consent Management") {
        Text(
            text = "Manage your data processing consents:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        PrivacyManager.DataProcessingPurpose.values().forEach { purpose ->
            val record = consentRecords[purpose]
            val granted = record?.granted ?: false

            ConsentItem(
                purpose = purpose,
                granted = granted,
                withdrawable = record?.withdrawable ?: !purpose.isRequired,
                onClick = { onConsentClick(purpose) }
            )
        }
    }
}

/**
 * Individual consent item.
 */
@Composable
private fun ConsentItem(
    purpose: PrivacyManager.DataProcessingPurpose,
    granted: Boolean,
    withdrawable: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (granted) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            }
        ),
        onClick = if (withdrawable) onClick else ({})
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = purpose.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = purpose.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (purpose.isRequired) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Required for app functionality",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = if (granted) Icons.Filled.Check else Icons.Filled.Close,
                contentDescription = if (granted) "Granted" else "Denied",
                tint = if (granted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
        }
    }
}

/**
 * Data retention section.
 */
@Composable
private fun DataRetentionSection(
    retentionPolicy: com.trustvault.android.compliance.DataRetentionManager.RetentionPolicy?,
    expiredCount: Int,
    onRetentionPolicyClick: () -> Unit,
    onEnforcePolicy: () -> Unit
) {
    SectionCard(title = "Data Retention Policy") {
        InfoRow(
            label = "Retention Period",
            value = when (retentionPolicy?.retentionDays) {
                -1 -> "Indefinite"
                null -> "Not set"
                else -> "${retentionPolicy.retentionDays} days"
            }
        )

        InfoRow(
            label = "Auto-Delete Expired",
            value = if (retentionPolicy?.autoDelete == true) "Enabled" else "Disabled"
        )

        if (expiredCount > 0) {
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "$expiredCount credentials have expired",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onEnforcePolicy,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Delete Expired Credentials")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onRetentionPolicyClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Change Retention Policy")
        }
    }
}

/**
 * Your data rights section.
 */
@Composable
private fun YourDataRightsSection(
    onDataExportClick: () -> Unit,
    onDataErasureClick: () -> Unit
) {
    SectionCard(title = "Your Data Rights") {
        Text(
            text = "Exercise your GDPR & DPDP Act rights:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Right to Data Portability (GDPR Article 20)
        DataRightButton(
            title = "Export Your Data",
            description = "Download all your data in a machine-readable format (GDPR Article 20)",
            icon = Icons.Filled.Download,
            onClick = onDataExportClick
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Right to Erasure (GDPR Article 17)
        DataRightButton(
            title = "Delete All Data",
            description = "Permanently erase all your data (GDPR Article 17 - Right to be Forgotten)",
            icon = Icons.Filled.DeleteForever,
            onClick = onDataErasureClick,
            isDestructive = true
        )
    }
}

/**
 * Data right button component.
 */
@Composable
private fun DataRightButton(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isDestructive) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = if (isDestructive) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Compliance information section.
 */
@Composable
private fun ComplianceInfoSection(dashboardData: PrivacyManager.PrivacyDashboardData) {
    SectionCard(title = "Compliance Information") {
        Text(
            text = "TrustVault complies with:",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        ComplianceItem("GDPR (EU Regulation 2016/679)")
        ComplianceItem("DPDP Act 2023 (India)")
        ComplianceItem("OWASP Mobile Top 10 2025")
        ComplianceItem("ISO 27001:2022")

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Zero Telemetry",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = "No analytics, tracking, or data transmission. All data stays on your device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
    }
}

/**
 * Compliance item row.
 */
@Composable
private fun ComplianceItem(text: String) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.VerifiedUser,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * Reusable section card.
 */
@Composable
private fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            content()
        }
    }
}

/**
 * Info row component.
 */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Formats timestamp to readable date.
 */
private fun formatDate(timestamp: Long): String {
    return SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(timestamp))
}