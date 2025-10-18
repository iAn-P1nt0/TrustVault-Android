package com.trustvault.android.presentation.ui.screens.credentials

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.trustvault.android.BuildConfig
import com.trustvault.android.domain.model.CredentialCategory
import com.trustvault.android.presentation.ui.components.TotpDisplayCard
import com.trustvault.android.presentation.viewmodel.AddEditCredentialViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditCredentialScreen(
    onNavigateBack: () -> Unit,
    onNavigateToGenerator: () -> Unit,
    onNavigateToOcrCapture: () -> Unit = {},
    viewModel: AddEditCredentialViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var passwordVisible by remember { mutableStateOf(false) }
    var showCategoryMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isEditing) "Edit Credential" else "New Credential") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
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
                .verticalScroll(rememberScrollState())
        ) {
            OutlinedTextField(
                value = uiState.title,
                onValueChange = { viewModel.onTitleChange(it) },
                label = { Text("Title*") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // OCR Scan button (conditional on feature flag)
            if (BuildConfig.ENABLE_OCR_FEATURE && !uiState.isEditing) {
                OutlinedButton(
                    onClick = onNavigateToOcrCapture,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scan from Browser")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            OutlinedTextField(
                value = uiState.username,
                onValueChange = { viewModel.onUsernameChange(it) },
                label = { Text("Username/Email") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = uiState.password,
                onValueChange = { viewModel.onPasswordChange(it) },
                label = { Text("Password") },
                visualTransformation = if (passwordVisible) 
                    VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    Row {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) 
                                    Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password"
                            )
                        }
                        IconButton(onClick = onNavigateToGenerator) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = "Generate password")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = uiState.website,
                onValueChange = { viewModel.onWebsiteChange(it) },
                label = { Text("Website") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Category selector
            ExposedDropdownMenuBox(
                expanded = showCategoryMenu,
                onExpandedChange = { showCategoryMenu = it }
            ) {
                OutlinedTextField(
                    value = uiState.category.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Category") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = showCategoryMenu)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = showCategoryMenu,
                    onDismissRequest = { showCategoryMenu = false }
                ) {
                    CredentialCategory.entries.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category.displayName) },
                            onClick = {
                                viewModel.onCategoryChange(category)
                                showCategoryMenu = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = uiState.notes,
                onValueChange = { viewModel.onNotesChange(it) },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6
            )

            Spacer(modifier = Modifier.height(24.dp))

            // TOTP/2FA Section
            Text(
                text = "Two-Factor Authentication (Optional)",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = "Add a TOTP secret to enable 2FA code generation for this credential.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Manual TOTP secret entry
            OutlinedTextField(
                value = uiState.otpSecret,
                onValueChange = { viewModel.onOtpSecretChange(it) },
                label = { Text("TOTP Secret (Base32)") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g., GEZDGNBVGY3TQOJQ") },
                singleLine = true,
                trailingIcon = {
                    if (uiState.otpSecret.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onOtpSecretChange("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear")
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // QR Code Scan button for TOTP
            OutlinedButton(
                onClick = { /* TODO: Launch QR scanner */ },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Filled.QrCode2,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Scan TOTP QR Code")
            }

            if (uiState.totpIssuer.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Scanned Configuration:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Service: ${uiState.totpIssuer}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (uiState.totpAccount.isNotEmpty()) {
                            Text(
                                text = "Account: ${uiState.totpAccount}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // Display TOTP code if secret is configured
            if (uiState.otpSecret.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                TotpDisplayCard(
                    otpSecret = uiState.otpSecret,
                    credentialTitle = uiState.title
                )
            }

            if (uiState.error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = uiState.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    viewModel.saveCredential {
                        Toast.makeText(context, "Credential saved", Toast.LENGTH_SHORT).show()
                        onNavigateBack()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoading
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Save")
                }
            }
        }
    }
}
