package com.trustvault.android.presentation.ui.screens.ocr

import android.Manifest
import androidx.camera.core.ImageCapture
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.trustvault.android.presentation.ui.screens.ocr.components.CameraPreview
import com.trustvault.android.presentation.ui.screens.ocr.components.PermissionDeniedDialog
import com.trustvault.android.presentation.ui.screens.ocr.components.PermissionRationaleDialog
import com.trustvault.android.presentation.viewmodel.OcrCaptureViewModel
import com.trustvault.android.security.ocr.OcrResult

/**
 * OCR Capture Screen for scanning login credentials from browser screenshots.
 *
 * Features:
 * - Camera permission handling (runtime request with rationale)
 * - CameraX preview with lifecycle management
 * - Capture button with processing state
 * - Error handling and user feedback
 * - Privacy notice overlay
 *
 * Flow:
 * 1. Check camera permission
 * 2. Show rationale if needed
 * 3. Request permission
 * 4. Show camera preview
 * 5. User taps capture button
 * 6. Process image with OCR
 * 7. Navigate back with result
 *
 * SECURITY:
 * - Permission rationale explains on-device processing
 * - No images persisted to disk
 * - OcrResult cleared after navigation
 *
 * @param onNavigateBack Navigate back to previous screen
 * @param onCredentialsExtracted Callback with extracted credentials
 * @param viewModel OCR capture view model (injected by Hilt)
 */
@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OcrCaptureScreen(
    onNavigateBack: () -> Unit,
    onCredentialsExtracted: (OcrResult) -> Unit,
    viewModel: OcrCaptureViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    // Camera permission state
    val cameraPermissionState = rememberPermissionState(
        permission = Manifest.permission.CAMERA
    )

    // Show rationale dialog
    var showRationaleDialog by remember { mutableStateOf(false) }

    // Show permanently denied dialog
    var showDeniedDialog by remember { mutableStateOf(false) }

    // ImageCapture use case (provided by CameraPreview)
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    // Request permission on first composition if not granted
    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            if (cameraPermissionState.status.shouldShowRationale) {
                showRationaleDialog = true
            } else {
                cameraPermissionState.launchPermissionRequest()
            }
        }
    }

    // Handle permission denied permanently
    LaunchedEffect(cameraPermissionState.status) {
        if (!cameraPermissionState.status.isGranted &&
            !cameraPermissionState.status.shouldShowRationale) {
            // Permission denied permanently
            showDeniedDialog = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan Credentials") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
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
                // Permission granted - show camera preview
                cameraPermissionState.status.isGranted -> {
                    CameraPreviewContent(
                        uiState = uiState,
                        onImageCaptureReady = { imageCapture = it },
                        onCapture = {
                            imageCapture?.let { capture ->
                                val executor = androidx.core.content.ContextCompat.getMainExecutor(context)
                                viewModel.captureAndProcess(capture, executor) { ocrResult ->
                                    onCredentialsExtracted(ocrResult)
                                    onNavigateBack()
                                }
                            }
                        },
                        onDismissError = { viewModel.clearError() }
                    )
                }

                // Show rationale dialog
                showRationaleDialog -> {
                    PermissionRationaleDialog(
                        onConfirm = {
                            showRationaleDialog = false
                            cameraPermissionState.launchPermissionRequest()
                        },
                        onDismiss = {
                            showRationaleDialog = false
                            onNavigateBack()
                        }
                    )
                }

                // Show permanently denied dialog
                showDeniedDialog -> {
                    PermissionDeniedDialog(
                        onDismiss = {
                            showDeniedDialog = false
                            onNavigateBack()
                        }
                    )
                }

                // Waiting for permission decision
                else -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

/**
 * Camera preview content with capture controls.
 *
 * @param uiState Current UI state from ViewModel
 * @param onImageCaptureReady Callback when ImageCapture is ready
 * @param onCapture Callback when user taps capture button
 * @param onDismissError Callback to dismiss error snackbar
 */
@Composable
private fun CameraPreviewContent(
    uiState: com.trustvault.android.presentation.viewmodel.OcrCaptureState,
    onImageCaptureReady: (ImageCapture) -> Unit,
    onCapture: () -> Unit,
    onDismissError: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Camera preview (full screen)
        CameraPreview(
            modifier = Modifier.fillMaxSize(),
            onImageCaptureReady = onImageCaptureReady
        )

        // Privacy notice overlay (top)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopCenter),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            )
        ) {
            Text(
                text = "Position the login form within the frame.\n" +
                        "All processing happens on your device.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(12.dp)
            )
        }

        // Capture button (bottom center)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Processing indicator
            if (uiState.isProcessing) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    ),
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp)
                        )
                        Text("Reading text...")
                    }
                }
            }

            // Capture button
            FloatingActionButton(
                onClick = if (uiState.isProcessing) { {} } else onCapture,
                modifier = Modifier.size(72.dp),
                containerColor = if (uiState.isProcessing)
                    MaterialTheme.colorScheme.surfaceVariant
                else
                    MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Filled.CameraAlt,
                    contentDescription = "Capture",
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Tap to scan",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                modifier = Modifier
                    .background(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = MaterialTheme.shapes.small
                    )
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        // Error snackbar
        uiState.error?.let { errorMessage ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                action = {
                    TextButton(onClick = onDismissError) {
                        Text("Dismiss")
                    }
                }
            ) {
                Text(errorMessage)
            }
        }
    }
}
