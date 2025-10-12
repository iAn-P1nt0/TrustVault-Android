package com.trustvault.android.security.ocr

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ML Kit Text Recognition wrapper with security controls.
 *
 * Integrates Google ML Kit Text Recognition v2 (bundled model) for on-device OCR
 * processing of browser login screenshots.
 *
 * SECURITY CONTROLS:
 * - On-device processing only (bundled model, no network calls)
 * - In-memory image processing (zero persistence to disk)
 * - Immediate ImageProxy disposal after processing
 * - Lifecycle-aware detector cleanup
 * - No logging of sensitive extracted text
 *
 * THREAD SAFETY:
 * - All public methods are suspend functions
 * - ML Kit processing runs on Dispatchers.Default (background thread)
 * - Lifecycle callbacks run on main thread
 *
 * Implementation validated against:
 * - Google ML Kit Official Docs: https://developers.google.com/ml-kit/vision/text-recognition/v2/android
 * - Google Codelab: https://codelabs.developers.google.com/codelabs/mlkit-android-translate
 * - spanmartina/Text-Recognition-and-Translation-MLKit (GitHub reference)
 *
 * @param context Application context (injected by Hilt)
 * @param parser Field parser for extracting credentials from OCR text
 */
@Singleton
class OcrProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val parser: CredentialFieldParser
) : DefaultLifecycleObserver {

    companion object {
        private const val TAG = "OcrProcessor"
    }

    /**
     * ML Kit Text Recognizer (bundled model).
     *
     * Uses Latin script recognizer by default.
     * Model is bundled with app (4MB size increase), no dynamic download required.
     *
     * Initialized lazily on first use to avoid startup overhead.
     */
    private val detector: TextRecognizer by lazy {
        Log.d(TAG, "Initializing ML Kit Text Recognizer (bundled model)")
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Track if detector has been initialized (for cleanup)
     */
    private var isInitialized = false

    /**
     * Process captured image and extract credential fields.
     *
     * SECURITY CRITICAL: This method handles sensitive credential data.
     * - ImageProxy processed in-memory only (no disk I/O)
     * - ImageProxy.close() called in finally block (prevents memory leak)
     * - Extracted text passed immediately to parser, then discarded
     * - Returns OcrResult with CharArray fields (clearable)
     *
     * Processing flow:
     * 1. Convert ImageProxy → InputImage (in-memory)
     * 2. ML Kit text recognition (on-device)
     * 3. Parse text → credential fields
     * 4. Close ImageProxy (release buffer)
     * 5. Return OcrResult
     *
     * @param imageProxy CameraX image from capture
     * @return Result.success(OcrResult) or Result.failure(OcrException)
     */
    @OptIn(ExperimentalGetImage::class)
    suspend fun processImage(imageProxy: ImageProxy): Result<OcrResult> {
        return withContext(Dispatchers.Default) {
            try {
                Log.d(TAG, "Processing image for OCR (${imageProxy.width}x${imageProxy.height})")

                // SECURITY CONTROL: Convert to InputImage without disk I/O
                val inputImage = InputImage.fromMediaImage(
                    imageProxy.image ?: throw OcrException.CaptureFailedException(
                        "ImageProxy.image is null"
                    ),
                    imageProxy.imageInfo.rotationDegrees
                )

                // Mark detector as initialized for cleanup
                isInitialized = true

                // ML Kit text recognition (on-device, async)
                val text = try {
                    detector.process(inputImage).await()
                } catch (e: Exception) {
                    throw OcrException.RecognitionFailedException(
                        "ML Kit processing failed",
                        e
                    )
                }

                // Log text length only, not content
                Log.d(TAG, "Text recognition complete (${text.text.length} chars)")

                // Check if any text was detected
                if (text.text.isBlank()) {
                    throw OcrException.NoTextDetectedException()
                }

                // Parse text into credential fields
                val ocrResult = parser.parseText(text.text)

                // Verify at least one field was extracted
                if (!ocrResult.hasData()) {
                    Log.w(TAG, "No credential fields found in extracted text")
                    throw OcrException.ParsingFailedException(
                        "No credential fields detected in text"
                    )
                }

                Log.d(TAG, "OCR processing successful")
                Result.success(ocrResult)

            } catch (e: OcrException) {
                // Re-throw OCR exceptions as-is
                Log.e(TAG, "OCR exception: ${e.javaClass.simpleName} - ${e.message}")
                Result.failure(e)

            } catch (e: Exception) {
                // Wrap unexpected exceptions
                Log.e(TAG, "Unexpected error during OCR processing", e)
                Result.failure(
                    OcrException.RecognitionFailedException(
                        "Unexpected error: ${e.message}",
                        e
                    )
                )

            } finally {
                // SECURITY CONTROL: Always release ImageProxy buffer
                // This prevents memory leaks and ensures timely buffer disposal
                imageProxy.close()
                Log.d(TAG, "ImageProxy closed")
            }
        }
    }

    /**
     * Process bitmap image directly (alternative to ImageProxy).
     *
     * Used for testing or when image comes from gallery instead of camera.
     *
     * @param bitmap Android Bitmap object
     * @param rotationDegrees Image rotation (0, 90, 180, 270)
     * @return Result.success(OcrResult) or Result.failure(OcrException)
     */
    suspend fun processBitmap(
        bitmap: android.graphics.Bitmap,
        rotationDegrees: Int = 0
    ): Result<OcrResult> {
        return withContext(Dispatchers.Default) {
            try {
                Log.d(TAG, "Processing bitmap for OCR (${bitmap.width}x${bitmap.height})")

                val inputImage = InputImage.fromBitmap(bitmap, rotationDegrees)
                isInitialized = true

                val text = detector.process(inputImage).await()

                Log.d(TAG, "Text recognition complete (${text.text.length} chars)")

                if (text.text.isBlank()) {
                    throw OcrException.NoTextDetectedException()
                }

                val ocrResult = parser.parseText(text.text)

                if (!ocrResult.hasData()) {
                    throw OcrException.ParsingFailedException(
                        "No credential fields detected in text"
                    )
                }

                Result.success(ocrResult)

            } catch (e: OcrException) {
                Log.e(TAG, "OCR exception: ${e.javaClass.simpleName}")
                Result.failure(e)

            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during OCR processing", e)
                Result.failure(
                    OcrException.RecognitionFailedException(
                        "Unexpected error: ${e.message}",
                        e
                    )
                )
            }
        }
    }

    /**
     * Lifecycle callback: Called when owner is destroyed.
     *
     * SECURITY CONTROL: Close ML Kit detector to release resources.
     * This ensures native memory allocated by ML Kit is freed.
     *
     * Pattern validated from Google Codelab:
     * "Add detector as lifecycle observer and close in onCleared()"
     */
    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        if (isInitialized) {
            Log.d(TAG, "Closing ML Kit Text Recognizer")
            try {
                detector.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing detector", e)
            }
            isInitialized = false
        }
    }

    /**
     * Manual cleanup method (alternative to lifecycle-based cleanup).
     *
     * Call this if OcrProcessor is not bound to a lifecycle.
     * Idempotent - safe to call multiple times.
     */
    fun close() {
        if (isInitialized) {
            Log.d(TAG, "Manual close of ML Kit Text Recognizer")
            try {
                detector.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing detector", e)
            }
            isInitialized = false
        }
    }
}