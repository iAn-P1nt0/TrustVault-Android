package com.trustvault.android.presentation.ui.screens.ocr.components

import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * CameraX preview composable for OCR capture.
 *
 * Integrates CameraX camera preview with Jetpack Compose using AndroidView.
 * Follows CameraX best practices:
 * - Lifecycle-aware camera binding
 * - Bind use cases in onCreate (not onResume)
 * - Proper resource cleanup
 *
 * Implementation validated against:
 * - Android CameraX Architecture Guide
 * - Google CameraX samples
 *
 * @param modifier Compose modifier for layout
 * @param cameraSelector Camera selector (default: back camera)
 * @param onImageCaptureReady Callback with ImageCapture use case for capturing
 */
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA,
    onImageCaptureReady: (ImageCapture) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Remember PreviewView to avoid recreation
    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    // Remember ImageCapture use case (stateful)
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    // LaunchedEffect for camera initialization (runs once per composition)
    LaunchedEffect(cameraSelector) {
        val cameraProvider = suspendCoroutine { continuation ->
            ProcessCameraProvider.getInstance(context).also { future ->
                future.addListener({
                    continuation.resume(future.get())
                }, ContextCompat.getMainExecutor(context))
            }
        }

        // Unbind all use cases before rebinding (cleanup)
        cameraProvider.unbindAll()

        // Configure Preview use case
        val preview = Preview.Builder()
            .build()
            .also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

        // Configure ImageCapture use case
        val imageCaptureUseCase = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        try {
            // Bind use cases to lifecycle
            // Best practice: Bind in onCreate (via LaunchedEffect), not onResume
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCaptureUseCase
            )

            // Store ImageCapture reference and notify parent
            imageCapture = imageCaptureUseCase
            onImageCaptureReady(imageCaptureUseCase)

        } catch (e: Exception) {
            // Log error but don't crash app
            android.util.Log.e("CameraPreview", "Camera binding failed", e)
        }
    }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            // Camera provider unbindAll called automatically by lifecycle
            // No manual cleanup needed with lifecycle-aware binding
        }
    }

    // Render PreviewView using AndroidView
    AndroidView(
        factory = { previewView },
        modifier = modifier.fillMaxSize()
    )
}
