package com.trustvault.android.autofill

import android.app.assist.AssistStructure
import android.os.CancellationSignal
import android.service.autofill.*
import android.util.Log
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import com.trustvault.android.R
import com.trustvault.android.TrustVaultApplication
import com.trustvault.android.domain.model.Credential
import com.trustvault.android.security.DatabaseKeyManager
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt EntryPoint for accessing dependencies in AutofillService.
 * Services cannot use @AndroidEntryPoint directly, so we use EntryPoint pattern.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AutofillServiceEntryPoint {
    fun credentialRepository(): com.trustvault.android.domain.repository.CredentialRepository
    fun databaseKeyManager(): DatabaseKeyManager
}

/**
 * TrustVault AutofillService implementation.
 *
 * Provides autofill suggestions for username/password fields in other apps.
 *
 * Security Features:
 * - Requires database to be unlocked (user authenticated)
 * - Matches credentials by package name and website
 * - No PII logging (only structure info for debugging)
 * - Encrypted credentials in database
 *
 * OWASP Compliance:
 * - M1: Proper Platform Usage - Uses Android AutofillService API correctly
 * - M2: Secure Data Storage - Credentials remain encrypted in database
 * - M4: Secure Authentication - Requires unlock before autofill
 *
 * Note: Uses Hilt EntryPoint pattern for dependency injection since
 * @AndroidEntryPoint is not fully supported for AutofillService.
 */
class TrustVaultAutofillService : AutofillService() {

    private lateinit var credentialRepository: com.trustvault.android.domain.repository.CredentialRepository
    private lateinit var databaseKeyManager: DatabaseKeyManager

    private val serviceScope = CoroutineScope(Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        // Manually inject dependencies using Hilt EntryPoint
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            AutofillServiceEntryPoint::class.java
        )
        credentialRepository = entryPoint.credentialRepository()
        databaseKeyManager = entryPoint.databaseKeyManager()
    }

    companion object {
        private const val TAG = "TrustVaultAutofill"
        private const val MAX_DATASETS = 10 // Limit suggestions to avoid UI clutter
    }

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        // PII-safe logging: Log structure info but not actual field values
        Log.d(TAG, "onFillRequest: Autofill request received")

        serviceScope.launch {
            try {
                handleFillRequest(request, callback)
            } catch (e: Exception) {
                Log.e(TAG, "Error handling fill request: ${e.message}", e)
                callback.onFailure("Error processing autofill request")
            }
        }
    }

    private suspend fun handleFillRequest(request: FillRequest, callback: FillCallback) {
        // SECURITY: Check if database is unlocked (user authenticated)
        if (!databaseKeyManager.isDatabaseInitialized()) {
            Log.d(TAG, "Database not unlocked - cannot provide autofill")
            callback.onFailure("User not authenticated")
            return
        }

        // Extract autofill structure
        val structure = request.fillContexts.lastOrNull()?.structure
        if (structure == null) {
            Log.w(TAG, "No autofill structure found")
            callback.onFailure("No structure")
            return
        }

        // Parse autofill fields from structure
        val autofillFields = parseStructure(structure)
        if (autofillFields.isEmpty()) {
            Log.d(TAG, "No autofillable fields found")
            callback.onFailure("No autofillable fields")
            return
        }

        Log.d(TAG, "Found ${autofillFields.size} autofillable fields")

        // Find matching credentials
        val packageName = structure.activityComponent.packageName
        val webDomain = autofillFields.firstOrNull { it.webDomain != null }?.webDomain

        Log.d(TAG, "Searching credentials for package: $packageName, domain: $webDomain")

        val matchingCredentials = withContext(Dispatchers.IO) {
            findMatchingCredentials(packageName, webDomain)
        }

        if (matchingCredentials.isEmpty()) {
            Log.d(TAG, "No matching credentials found")
            callback.onFailure("No credentials")
            return
        }

        Log.d(TAG, "Found ${matchingCredentials.size} matching credentials")

        // Build autofill response with datasets
        val fillResponse = buildFillResponse(autofillFields, matchingCredentials)
        callback.onSuccess(fillResponse)

        Log.d(TAG, "Autofill response sent successfully")
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        // PII-safe logging
        Log.d(TAG, "onSaveRequest: Save request received")

        serviceScope.launch {
            try {
                handleSaveRequest(request, callback)
            } catch (e: Exception) {
                Log.e(TAG, "Error handling save request: ${e.message}", e)
                callback.onFailure("Error processing save request")
            }
        }
    }

    private suspend fun handleSaveRequest(request: SaveRequest, callback: SaveCallback) {
        // SECURITY: Check if database is unlocked
        if (!databaseKeyManager.isDatabaseInitialized()) {
            Log.d(TAG, "Database not unlocked - cannot save credentials")
            callback.onFailure("User not authenticated")
            return
        }

        // Parse credentials from save request
        val savedCredential = parseSaveRequest(request)
        if (savedCredential == null) {
            Log.w(TAG, "Could not parse credentials from save request")
            callback.onFailure("Invalid save data")
            return
        }

        Log.d(TAG, "Parsed save request: package=${savedCredential.packageName}, hasUsername=${savedCredential.username.isNotEmpty()}, hasPassword=${savedCredential.password.isNotEmpty()}")

        // Check if credential already exists
        val existingCredential = withContext(Dispatchers.IO) {
            findExistingCredential(savedCredential)
        }

        // Launch confirmation UI
        val intent = AutofillSaveActivity.createIntent(
            context = this@TrustVaultAutofillService,
            savedCredential = savedCredential,
            existingCredential = existingCredential
        )
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)

        // Signal success - actual save happens in confirmation activity
        callback.onSuccess()
    }

    /**
     * Parses SaveRequest to extract username and password.
     */
    private fun parseSaveRequest(request: SaveRequest): SavedCredential? {
        val structure = request.fillContexts.lastOrNull()?.structure ?: return null

        var username = ""
        var password = ""
        val packageName = structure.activityComponent.packageName
        var webDomain: String? = null

        // Extract data from each dataset in the request
        request.datasetIds?.forEach { datasetId ->
            val clientState = request.clientState ?: return@forEach
            // Client state contains the autofill data
        }

        // Parse structure to find filled fields
        val fields = mutableListOf<AutofillField>()
        for (i in 0 until structure.windowNodeCount) {
            val windowNode = structure.getWindowNodeAt(i)
            extractSaveData(windowNode.rootViewNode, fields) { field, value ->
                when (field.fieldType) {
                    AutofillFieldType.USERNAME -> {
                        username = value
                        if (webDomain == null) webDomain = field.webDomain
                    }
                    AutofillFieldType.PASSWORD -> password = value
                }
            }
        }

        if (username.isEmpty() && password.isEmpty()) {
            return null
        }

        return SavedCredential(
            username = username,
            password = password,
            packageName = packageName,
            webDomain = webDomain
        )
    }

    /**
     * Recursively extracts autofill values from ViewNode.
     */
    private fun extractSaveData(
        node: AssistStructure.ViewNode,
        fields: MutableList<AutofillField>,
        onFieldFound: (AutofillField, String) -> Unit
    ) {
        val autofillId = node.autofillId
        val autofillHints = node.autofillHints
        val autofillValue = node.autofillValue

        if (autofillId != null && autofillHints != null && autofillValue != null) {
            val fieldType = determineFieldType(autofillHints)
            if (fieldType != null && autofillValue.isText) {
                val field = AutofillField(
                    autofillId = autofillId,
                    fieldType = fieldType,
                    webDomain = node.webDomain,
                    hints = autofillHints.toList()
                )
                val value = autofillValue.textValue.toString()
                onFieldFound(field, value)
            }
        }

        // Recursively process children
        for (i in 0 until node.childCount) {
            extractSaveData(node.getChildAt(i), fields, onFieldFound)
        }
    }

    /**
     * Finds existing credential that matches the saved credential.
     */
    private suspend fun findExistingCredential(saved: SavedCredential): Credential? {
        val allCredentials = credentialRepository.getAllCredentials().first()

        return allCredentials.firstOrNull { credential ->
            // Match by package name and username
            if (credential.packageName == saved.packageName &&
                credential.username.equals(saved.username, ignoreCase = true)) {
                return@firstOrNull true
            }

            // Match by web domain and username
            if (saved.webDomain != null && credential.website.isNotEmpty()) {
                val credentialDomain = extractDomain(credential.website)
                val savedDomain = extractDomain(saved.webDomain)
                if (credentialDomain == savedDomain &&
                    credential.username.equals(saved.username, ignoreCase = true)) {
                    return@firstOrNull true
                }
            }

            false
        }
    }

    /**
     * Parses the AssistStructure to extract autofillable fields.
     * Identifies username and password fields based on autofill hints.
     *
     * SECURITY: Does not log field values, only structure metadata.
     */
    private fun parseStructure(structure: AssistStructure): List<AutofillField> {
        val fields = mutableListOf<AutofillField>()

        for (i in 0 until structure.windowNodeCount) {
            val windowNode = structure.getWindowNodeAt(i)
            parseNode(windowNode.rootViewNode, fields)
        }

        return fields
    }

    /**
     * Recursively parses a ViewNode to find autofillable fields.
     */
    private fun parseNode(node: AssistStructure.ViewNode, fields: MutableList<AutofillField>) {
        // Check if this node is autofillable
        val autofillId = node.autofillId
        val autofillHints = node.autofillHints

        if (autofillId != null && autofillHints != null && autofillHints.isNotEmpty()) {
            val fieldType = determineFieldType(autofillHints)
            if (fieldType != null) {
                fields.add(
                    AutofillField(
                        autofillId = autofillId,
                        fieldType = fieldType,
                        webDomain = node.webDomain,
                        hints = autofillHints.toList()
                    )
                )
                // PII-safe logging: Log field type but not ID or value
                Log.d(TAG, "Found autofillable field: type=$fieldType, hints=${autofillHints.contentToString()}")
            }
        }

        // Recursively process child nodes
        for (i in 0 until node.childCount) {
            parseNode(node.getChildAt(i), fields)
        }
    }

    /**
     * Determines if autofill hints indicate a username or password field.
     */
    private fun determineFieldType(hints: Array<String>): AutofillFieldType? {
        return when {
            hints.any { it == android.view.View.AUTOFILL_HINT_USERNAME ||
                       it == android.view.View.AUTOFILL_HINT_EMAIL_ADDRESS } -> {
                AutofillFieldType.USERNAME
            }
            hints.any { it == android.view.View.AUTOFILL_HINT_PASSWORD } -> {
                AutofillFieldType.PASSWORD
            }
            else -> null
        }
    }

    /**
     * Finds credentials matching the given package name or web domain.
     *
     * Matching strategy:
     * 1. Exact package name match
     * 2. Website domain match (for web apps)
     * 3. Fuzzy website match (subdomain)
     */
    private suspend fun findMatchingCredentials(
        packageName: String,
        webDomain: String?
    ): List<Credential> {
        // Get all credentials (from encrypted database)
        val allCredentials = credentialRepository.getAllCredentials().first()

        // Filter by package name or website
        val matchingCredentials = allCredentials.filter { credential ->
            // Exact package name match
            if (credential.packageName.isNotEmpty() && credential.packageName == packageName) {
                return@filter true
            }

            // Website domain match
            if (webDomain != null && credential.website.isNotEmpty()) {
                // Extract domain from credential website
                val credentialDomain = extractDomain(credential.website)
                val requestDomain = extractDomain(webDomain)

                if (credentialDomain == requestDomain) {
                    return@filter true
                }

                // Fuzzy match: Check if domains share common root
                // e.g., "login.example.com" matches "example.com"
                if (credentialDomain.contains(requestDomain) || requestDomain.contains(credentialDomain)) {
                    return@filter true
                }
            }

            false
        }

        // Limit number of suggestions
        return matchingCredentials.take(MAX_DATASETS)
    }

    /**
     * Extracts the domain from a URL or domain string.
     * e.g., "https://example.com/path" -> "example.com"
     */
    private fun extractDomain(url: String): String {
        return try {
            val normalized = url.trim().lowercase()
                .removePrefix("http://")
                .removePrefix("https://")
                .substringBefore("/")
                .substringBefore(":")

            // Remove www. prefix for better matching
            normalized.removePrefix("www.")
        } catch (_: Exception) {
            url.lowercase()
        }
    }

    /**
     * Builds a FillResponse with datasets for each matching credential.
     */
    private fun buildFillResponse(
        fields: List<AutofillField>,
        credentials: List<Credential>
    ): FillResponse {
        val responseBuilder = FillResponse.Builder()

        // Find username and password field IDs
        val usernameField = fields.firstOrNull { it.fieldType == AutofillFieldType.USERNAME }
        val passwordField = fields.firstOrNull { it.fieldType == AutofillFieldType.PASSWORD }

        if (usernameField == null && passwordField == null) {
            Log.w(TAG, "No username or password fields found")
        }

        // Create a dataset for each credential
        credentials.forEach { credential ->
            val dataset = buildDataset(credential, usernameField, passwordField)
            responseBuilder.addDataset(dataset)
        }

        return responseBuilder.build()
    }

    /**
     * Builds a Dataset for a single credential.
     * A Dataset represents one autofill suggestion.
     */
    private fun buildDataset(
        credential: Credential,
        usernameField: AutofillField?,
        passwordField: AutofillField?
    ): Dataset {
        val datasetBuilder = Dataset.Builder()

        // Create presentation (UI shown to user)
        val presentation = RemoteViews(packageName, R.layout.autofill_suggestion_item)

        // Display credential title and username (not password!)
        presentation.setTextViewText(R.id.autofill_title, credential.title)
        presentation.setTextViewText(R.id.autofill_username, credential.username)

        // Set autofill values for username field
        if (usernameField != null) {
            datasetBuilder.setValue(
                usernameField.autofillId,
                AutofillValue.forText(credential.username),
                presentation
            )
        }

        // Set autofill values for password field
        if (passwordField != null) {
            val passwordPresentation = RemoteViews(packageName, R.layout.autofill_suggestion_item)
            passwordPresentation.setTextViewText(R.id.autofill_title, credential.title)
            passwordPresentation.setTextViewText(R.id.autofill_username, "••••••••") // Mask password

            datasetBuilder.setValue(
                passwordField.autofillId,
                AutofillValue.forText(credential.password),
                passwordPresentation
            )
        }

        return datasetBuilder.build()
    }
}

/**
 * Represents an autofillable field in the app structure.
 */
data class AutofillField(
    val autofillId: AutofillId,
    val fieldType: AutofillFieldType,
    val webDomain: String?,
    val hints: List<String>
)

/**
 * Type of autofillable field.
 */
enum class AutofillFieldType {
    USERNAME,
    PASSWORD
}

/**
 * Represents credential data parsed from a save request.
 */
@kotlinx.parcelize.Parcelize
data class SavedCredential(
    val username: String,
    val password: String,
    val packageName: String,
    val webDomain: String?
) : android.os.Parcelable

