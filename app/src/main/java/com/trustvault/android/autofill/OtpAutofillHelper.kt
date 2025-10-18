package com.trustvault.android.autofill

import android.app.assist.AssistStructure
import android.os.Build
import android.service.autofill.Dataset
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import com.trustvault.android.R
import com.trustvault.android.domain.model.Credential
import com.trustvault.android.security.TotpGenerator

/**
 * Helper for generating TOTP autofill datasets.
 *
 * **Responsibility:**
 * - Generate current TOTP code from credential
 * - Create autofill Dataset with TOTP value
 * - Generate RemoteViews for autofill UI
 * - Handle errors gracefully
 *
 * **Security:**
 * - Code generated on-demand only
 * - Never stores code in memory longer than needed
 * - No logging of actual code values
 * - Only fills valid OTP fields
 *
 * **Compatibility:**
 * - Requires Android 8.0+ (API 26)
 * - Works with system AutofillService
 */
@RequiresApi(Build.VERSION_CODES.O)
class OtpAutofillHelper {

    private val totpGenerator = TotpGenerator()

    /**
     * Creates an autofill Dataset for a TOTP code.
     *
     * **Dataset Structure:**
     * - Single field: OTP code
     * - Remote UI: Shows "2FA Code" + current code
     * - One-time fill: User taps to autofill
     *
     * @param credential Credential with otpSecret
     * @param otpFieldId AutofillId of OTP input field
     * @return Dataset ready for autofill, or null if generation fails
     */
    fun createOtpDataset(
        credential: Credential,
        otpFieldId: AutofillId
    ): Dataset? {
        // Verify credential has OTP secret
        if (credential.otpSecret.isNullOrBlank()) {
            return null
        }

        // Generate current TOTP code
        val code = try {
            val now = System.currentTimeMillis() / 1000
            val result = totpGenerator.generate(
                credential.otpSecret,
                timeSeconds = now,
                digits = 6,
                period = 30
            )
            result.code
        } catch (e: Exception) {
            // Log failure but don't expose exception
            android.util.Log.e("OtpAutofill", "TOTP generation failed: ${e.javaClass.simpleName}")
            return null
        }

        // Create RemoteViews for UI
        val remoteViews = RemoteViews(
            "com.trustvault.android",
            R.layout.autofill_2fa_item
        )
        remoteViews.setTextViewText(R.id.autofill_2fa_label, "2FA Code")
        remoteViews.setTextViewText(R.id.autofill_2fa_code, code)

        // Create dataset with single field
        val dataset = Dataset.Builder(remoteViews)
            .setValue(
                otpFieldId,
                AutofillValue.forText(code)
            )
            .build()

        return dataset
    }

    /**
     * Creates multiple TOTP datasets for different field IDs.
     *
     * Handles scenarios where OTP code might appear in multiple fields
     * (rare but possible in some complex 2FA forms).
     *
     * @param credential Credential with otpSecret
     * @param otpFieldIds List of AutofillIds for OTP fields
     * @return List of Datasets ready for autofill
     */
    fun createMultipleOtpDatasets(
        credential: Credential,
        otpFieldIds: List<AutofillId>
    ): List<Dataset> {
        return otpFieldIds.mapNotNull { fieldId ->
            createOtpDataset(credential, fieldId)
        }
    }

    /**
     * Validates if TOTP code is still valid for autofill.
     *
     * Checks:
     * - Code generated in current time window
     * - Code won't expire immediately after fill
     * - Code is not in last second (too risky)
     *
     * @param credential Credential with otpSecret
     * @return true if code is valid for autofill
     */
    fun isOtpCodeValid(credential: Credential): Boolean {
        if (credential.otpSecret.isNullOrBlank()) {
            return false
        }

        return try {
            val now = System.currentTimeMillis() / 1000
            val result = totpGenerator.generate(
                credential.otpSecret,
                timeSeconds = now,
                digits = 6,
                period = 30
            )

            // Don't autofill if code expires in next 2 seconds
            // (user won't have time to enter on server)
            result.remainingSeconds > 2
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Gets the remaining validity time for TOTP code.
     *
     * Useful for UI feedback (e.g., "expires in X seconds").
     *
     * @param credential Credential with otpSecret
     * @return Remaining seconds in current window, or 0 if invalid
     */
    fun getRemainingSeconds(credential: Credential): Int {
        if (credential.otpSecret.isNullOrBlank()) {
            return 0
        }

        return try {
            val now = System.currentTimeMillis() / 1000
            val result = totpGenerator.generate(
                credential.otpSecret,
                timeSeconds = now,
                digits = 6,
                period = 30
            )
            result.remainingSeconds
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Generates a single TOTP code without autofill wrapper.
     *
     * Useful for manual testing or other scenarios where
     * the code is needed directly.
     *
     * **Security:** Code is returned as-is. Caller is responsible
     * for not logging it or storing it longer than necessary.
     *
     * @param credential Credential with otpSecret
     * @return Generated TOTP code, or empty string if generation fails
     */
    fun generateOtpCode(credential: Credential): String {
        if (credential.otpSecret.isNullOrBlank()) {
            return ""
        }

        return try {
            val now = System.currentTimeMillis() / 1000
            totpGenerator.generate(
                credential.otpSecret,
                timeSeconds = now,
                digits = 6,
                period = 30
            ).code
        } catch (e: Exception) {
            ""
        }
    }
}
