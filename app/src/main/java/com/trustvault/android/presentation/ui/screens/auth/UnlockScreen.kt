package com.trustvault.android.presentation.ui.screens.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.trustvault.android.presentation.ui.components.BiometricInvalidatedDialog
import com.trustvault.android.presentation.ui.components.BiometricSetupDialog
import com.trustvault.android.presentation.viewmodel.BiometricViewModel
import com.trustvault.android.presentation.viewmodel.UnlockViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnlockScreen(
    onUnlocked: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    viewModel: UnlockViewModel = hiltViewModel(),
    biometricViewModel: BiometricViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val biometricUiState by biometricViewModel.uiState.collectAsState()
    var passwordVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Show biometric prompt on launch if available
    LaunchedEffect(uiState.shouldShowBiometricPromptOnLaunch) {
        if (uiState.shouldShowBiometricPromptOnLaunch) {
            // Get FragmentActivity from context
            val activity = context as? FragmentActivity
            if (activity != null) {
                viewModel.showBiometricPrompt(activity, onUnlocked)
            }
        }
    }

    // Show biometric setup dialog after successful password unlock
    if (uiState.shouldOfferBiometricSetup) {
        BiometricSetupDialog(
            onEnableClick = {
                val activity = context as? FragmentActivity
                val masterPassword = uiState.lastMasterPassword
                if (activity != null && masterPassword != null) {
                    biometricViewModel.setupBiometricUnlock(
                        activity = activity,
                        masterPassword = masterPassword,
                        onSuccess = {
                            viewModel.clearLastMasterPassword()
                        }
                    )
                }
            },
            onNotNowClick = {
                viewModel.clearLastMasterPassword()
            },
            onNeverAskClick = {
                biometricViewModel.setNeverAskAgain()
                viewModel.clearLastMasterPassword()
            },
            onDismiss = {
                viewModel.clearLastMasterPassword()
            }
        )
    }

    // Show biometric invalidated dialog
    if (uiState.showBiometricInvalidatedDialog) {
        BiometricInvalidatedDialog(
            onReEnable = {
                viewModel.dismissBiometricInvalidatedDialog()
                onNavigateToSettings()
            },
            onDismiss = {
                viewModel.dismissBiometricInvalidatedDialog()
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TrustVault") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Unlock Your Vault",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = uiState.password,
                onValueChange = { viewModel.onPasswordChange(it) },
                label = { Text("Master Password") },
                visualTransformation = if (passwordVisible) 
                    VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) 
                                Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

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
                onClick = { viewModel.unlock(onUnlocked) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoading
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Unlock")
                }
            }

            if (uiState.isBiometricAvailable) {
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = {
                        val activity = context as? FragmentActivity
                        if (activity != null) {
                            viewModel.showBiometricPrompt(activity, onUnlocked)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Filled.Fingerprint,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Unlock with Biometric")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "🔒 Zero telemetry - Your data never leaves your device",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
