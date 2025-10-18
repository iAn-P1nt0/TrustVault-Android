package com.trustvault.android.presentation.ui.screens.security

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.trustvault.android.presentation.viewmodel.PasswordHealthViewModel
import com.trustvault.android.security.PasswordHealthAnalyzer

/**
 * Password health analysis screen.
 * Shows password strength, complexity score, and breach status.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordHealthScreen(
    initialPassword: String = "",
    onNavigateBack: () -> Unit,
    viewModel: PasswordHealthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isBreachCheckOptedIn by viewModel.isBreachCheckOptedIn.collectAsState()

    var password by remember { mutableStateOf(initialPassword) }
    var showPassword by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }

    LaunchedEffect(initialPassword) {
        if (initialPassword.isNotEmpty()) {
            viewModel.analyzePassword(initialPassword, username, email)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Password Health") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Password Input Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Enter Password to Analyze",
                        style = MaterialTheme.typography.titleMedium
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { newPassword ->
                            password = newPassword
                            if (newPassword.isNotEmpty()) {
                                viewModel.analyzePassword(newPassword, username, email)
                            }
                        },
                        label = { Text("Password") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (showPassword) {
                            androidx.compose.ui.text.input.VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    if (showPassword) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                    contentDescription = null
                                )
                            }
                        }
                    )

                    OutlinedTextField(
                        value = username,
                        onValueChange = { newUsername ->
                            username = newUsername
                            if (password.isNotEmpty()) {
                                viewModel.analyzePassword(password, newUsername, email)
                            }
                        },
                        label = { Text("Username (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = email,
                        onValueChange = { newEmail ->
                            email = newEmail
                            if (password.isNotEmpty()) {
                                viewModel.analyzePassword(password, username, newEmail)
                            }
                        },
                        label = { Text("Email (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Results Section
            when (uiState) {
                is PasswordHealthViewModel.UiState.Idle -> {
                    // Empty state
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Enter a password to analyze its strength",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                is PasswordHealthViewModel.UiState.Analyzing -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is PasswordHealthViewModel.UiState.HealthReady -> {
                    val health = (uiState as PasswordHealthViewModel.UiState.HealthReady).health
                    val isBreached = (uiState as PasswordHealthViewModel.UiState.HealthReady).isBreached
                    val breachCount = (uiState as PasswordHealthViewModel.UiState.HealthReady).breachCount
                    val breachCached = (uiState as PasswordHealthViewModel.UiState.HealthReady).breachCached

                    // Strength Score Card
                    StrengthScoreCard(health)

                    // Entropy Information
                    EntropyCard(health)

                    // Crack Time
                    CrackTimeCard(health)

                    // Feedback
                    if (health.feedback.isNotEmpty()) {
                        FeedbackCard("Strengths", health.feedback, Icons.Filled.CheckCircle)
                    }

                    // Warnings
                    if (health.warnings.isNotEmpty()) {
                        FeedbackCard("Warnings", health.warnings, Icons.Filled.Warning)
                    }

                    // Breach Check
                    BreachCheckCard(
                        isBreached = isBreached,
                        breachCount = breachCount,
                        isCached = breachCached,
                        isOptedIn = isBreachCheckOptedIn,
                        onOptInChanged = { viewModel.setBreachCheckOptedIn(it) }
                    )
                }

                is PasswordHealthViewModel.UiState.Error -> {
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
                            Text(
                                (uiState as PasswordHealthViewModel.UiState.Error).message,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StrengthScoreCard(health: PasswordHealthAnalyzer.PasswordHealth) {
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Password Strength",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        health.strengthLevel.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = parseColor(health.strengthLevel.color)
                    )
                }

                // Score circle
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            color = parseColor(health.strengthLevel.color).copy(alpha = 0.2f),
                            shape = MaterialTheme.shapes.large
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${health.score}/100",
                        style = MaterialTheme.typography.headlineSmall,
                        color = parseColor(health.strengthLevel.color)
                    )
                }
            }

            // Progress bar
            LinearProgressIndicator(
                progress = (health.score / 100f).coerceIn(0f, 1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                trackColor = MaterialTheme.colorScheme.surface,
                color = parseColor(health.strengthLevel.color)
            )
        }
    }
}

@Composable
private fun EntropyCard(health: PasswordHealthAnalyzer.PasswordHealth) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            InfoRow("Entropy", "%.1f bits".format(health.entropyBits))
            InfoRow("Length", "${health.crackTimeYears.toLong()} character minimum")
        }
    }
}

@Composable
private fun CrackTimeCard(health: PasswordHealthAnalyzer.PasswordHealth) {
    val crackTimeText = when {
        health.crackTimeYears < 1 -> "< 1 year"
        health.crackTimeYears < 1_000_000 -> "${health.crackTimeYears.toLong()} years"
        health.crackTimeYears < 1_000_000_000 -> "Millions of years"
        else -> "Billions of years (practically uncrackable)"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Estimated Crack Time",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                crackTimeText,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "Assumes 1 billion guesses per second (fast offline attack)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FeedbackCard(
    title: String,
    items: List<String>,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(title, style = MaterialTheme.typography.titleSmall)
            }

            items.forEach { item ->
                Text(
                    "• $item",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 28.dp)
                )
            }
        }
    }
}

@Composable
private fun BreachCheckCard(
    isBreached: Boolean,
    breachCount: Int,
    isCached: Boolean,
    isOptedIn: Boolean,
    onOptInChanged: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isBreached) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Breach Check (k-anonymity)",
                    style = MaterialTheme.typography.titleSmall
                )

                Switch(
                    checked = isOptedIn,
                    onCheckedChange = onOptInChanged
                )
            }

            if (isOptedIn) {
                if (isBreached) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            "Found in $breachCount breach(es)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            "Not found in known breaches",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                if (isCached) {
                    Text(
                        "(Result from cache)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    "Uses k-anonymity: only hash prefix sent to server, password never leaves device.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "Enable to check if this password has appeared in known data breaches (privacy-preserving)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun parseColor(colorHex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(colorHex))
    } catch (e: Exception) {
        Color.Gray
    }
}
