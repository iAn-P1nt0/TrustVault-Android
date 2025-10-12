package com.trustvault.android.security.ocr

/**
 * Base exception for OCR-related errors.
 *
 * Provides structured error handling for the OCR feature with specific
 * exception types for different failure scenarios.
 */
sealed class OcrException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /**
     * Text recognition failed during ML Kit processing.
     *
     * Possible causes:
     * - ML Kit model initialization failed
     * - Image processing error
     * - Memory allocation failure
     *
     * Recommended action: Show error message, allow retry
     */
    class RecognitionFailedException(message: String, cause: Throwable? = null) :
        OcrException("Text recognition failed: $message", cause)

    /**
     * No text was detected in the captured image.
     *
     * Possible causes:
     * - Image is blank or too blurry
     * - No text content in captured area
     * - Lighting conditions too poor
     *
     * Recommended action: Prompt user to capture clearer image
     */
    class NoTextDetectedException(message: String = "No text found in image") :
        OcrException(message)

    /**
     * Image capture failed before OCR processing.
     *
     * Possible causes:
     * - Camera hardware error
     * - Insufficient storage space
     * - Camera permission revoked mid-capture
     *
     * Recommended action: Show error message, navigate back
     */
    class CaptureFailedException(message: String, cause: Throwable? = null) :
        OcrException("Image capture failed: $message", cause)

    /**
     * Field parsing failed - could not extract credential fields.
     *
     * Possible causes:
     * - Unexpected text format
     * - No recognizable patterns (email, URL, etc.)
     * - Foreign language or special characters
     *
     * Recommended action: Show partial results, allow manual entry
     */
    class ParsingFailedException(message: String, cause: Throwable? = null) :
        OcrException("Field parsing failed: $message", cause)

    /**
     * Camera initialization failed.
     *
     * Possible causes:
     * - Camera already in use by another app
     * - Unsupported device
     * - Camera hardware failure
     *
     * Recommended action: Show error message, disable OCR feature
     */
    class CameraInitializationException(message: String, cause: Throwable? = null) :
        OcrException("Camera initialization failed: $message", cause)

    /**
     * OCR feature is disabled (feature flag off or unsupported device).
     *
     * Recommended action: Hide OCR UI elements
     */
    class FeatureDisabledException(message: String = "OCR feature is not available") :
        OcrException(message)
}