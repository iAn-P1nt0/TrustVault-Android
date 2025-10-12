package com.trustvault.android.presentation.viewmodel

import android.content.Context
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trustvault.android.security.ocr.OcrException
import com.trustvault.android.security.ocr.OcrProcessor
import com.trustvault.android.security.ocr.OcrResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for OCR credential capture screen.
 *
 * Manages:
 * - Camera lifecycle and initialization
 * - Image capture coordination
 * - OCR processing via OcrProcessor
 * - UI state (loading, error, success)
 * - Extracted credential data
 *
 * SECURITY NOTE:
 * - OcrResult is cleared on ViewModel clear (onCleared)
 * - Sensitive data has limited lifetime
 * - No logging of extracted credentials
 *
 * @param ocrProcessor ML Kit processor for text recognition
 */
@HiltViewModel
class OcrCaptureViewModel @Inject constructor(
    private val ocrProcessor: OcrProcessor
) : ViewModel() {

    private val _uiState = MutableStateFlow(OcrCaptureState())
    val uiState: StateFlow<OcrCaptureState> = _uiState.asStateFlow()

    /**
     * Capture image and process with OCR.
     *
     * Flow:
     * 1. Set UI state to processing
     * 2. Capture image via ImageCapture
     * 3. Process ImageProxy with OcrProcessor
     * 4. Update state with result or error
     *
     * SECURITY CONTROL: ImageProxy closed by OcrProcessor in finally block
     *
     * @param imageCapture CameraX ImageCapture use case
     * @param executor Executor for camera callbacks
     * @param onSuccess Callback with extracted credentials
     */
    fun captureAndProcess(
        imageCapture: ImageCapture,
        executor: java.util.concurrent.Executor,
        onSuccess: (OcrResult) -> Unit
    ) {
        _uiState.update { it.copy(isProcessing = true, error = null) }

        imageCapture.takePicture(
            executor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    processImage(image, onSuccess)
                }

                override fun onError(exception: ImageCaptureException) {
                    handleCaptureError(exception)
                }
            }
        )
    }

    /**
     * Process captured ImageProxy with OCR.
     *
     * @param imageProxy Captured image from camera
     * @param onSuccess Callback with extracted credentials
     */
    private fun processImage(
        imageProxy: ImageProxy,
        onSuccess: (OcrResult) -> Unit
    ) {
        viewModelScope.launch {
            val result = ocrProcessor.processImage(imageProxy)

            result.fold(
                onSuccess = { ocrResult ->
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            extractedData = ocrResult,
                            error = null
                        )
                    }
                    onSuccess(ocrResult)
                },
                onFailure = { exception ->
                    val errorMessage = when (exception) {
                        is OcrException.NoTextDetectedException ->
                            "No text found in image. Please capture a clearer image of the login form."

                        is OcrException.ParsingFailedException ->
                            "Could not detect credential fields. Please try again or enter manually."

                        is OcrException.RecognitionFailedException ->
                            "Text recognition failed. Please try again."

                        is OcrException.CaptureFailedException ->
                            "Image capture failed. Please try again."

                        else ->
                            "OCR processing failed: ${exception.message}"
                    }

                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            error = errorMessage
                        )
                    }
                }
            )
        }
    }

    /**
     * Handle camera capture error.
     *
     * @param exception ImageCaptureException from CameraX
     */
    private fun handleCaptureError(exception: ImageCaptureException) {
        val errorMessage = when (exception.imageCaptureError) {
            ImageCapture.ERROR_CAMERA_CLOSED ->
                "Camera closed unexpectedly. Please try again."

            ImageCapture.ERROR_CAPTURE_FAILED ->
                "Image capture failed. Please try again."

            ImageCapture.ERROR_FILE_IO ->
                "File I/O error. Please check device storage."

            ImageCapture.ERROR_INVALID_CAMERA ->
                "Invalid camera configuration."

            else ->
                "Camera error: ${exception.message}"
        }

        _uiState.update {
            it.copy(
                isProcessing = false,
                error = errorMessage
            )
        }
    }

    /**
     * Process a Bitmap (e.g., user-selected screenshot) with OCR.
     *
     * @param bitmap Bitmap image to process
     * @param onSuccess Callback with extracted credentials
     */
    fun processBitmap(
        bitmap: android.graphics.Bitmap,
        onSuccess: (OcrResult) -> Unit
    ) {
        _uiState.update { it.copy(isProcessing = true, error = null) }
        viewModelScope.launch {
            val result = ocrProcessor.processBitmap(bitmap)

            result.fold(
                onSuccess = { ocrResult ->
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            extractedData = ocrResult,
                            error = null
                        )
                    }
                    onSuccess(ocrResult)
                },
                onFailure = { exception ->
                    val errorMessage = when (exception) {
                        is OcrException.NoTextDetectedException ->
                            "No text found in image. Please select a clearer screenshot of the login form."

                        is OcrException.ParsingFailedException ->
                            "Could not detect credential fields in the screenshot. Try again or enter manually."

                        is OcrException.RecognitionFailedException ->
                            "Text recognition failed on the screenshot. Please try again."

                        else ->
                            "OCR processing failed: ${exception.message}"
                    }

                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            error = errorMessage
                        )
                    }
                }
            )
        }
    }

    /**
     * Clear error message.
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Reset state (for retry).
     */
    fun resetState() {
        // Clear previous OcrResult before resetting
        _uiState.value.extractedData?.clear()

        _uiState.update {
            OcrCaptureState()
        }
    }

    /**
     * ViewModel cleanup.
     *
     * SECURITY CONTROL: Clear extracted credentials from memory.
     */
    override fun onCleared() {
        super.onCleared()

        // Clear sensitive data
        _uiState.value.extractedData?.clear()

        // Close OCR processor
        ocrProcessor.close()
    }
}

/**
 * UI state for OCR capture screen.
 *
 * @property isProcessing True when OCR processing in progress
 * @property extractedData Extracted credentials (nullable)
 * @property error Error message (nullable)
 */
data class OcrCaptureState(
    val isProcessing: Boolean = false,
    val extractedData: OcrResult? = null,
    val error: String? = null
)
