package com.trustvault.android.presentation

/**
 * Navigation routes for the app.
 */
sealed class Screen(val route: String) {
    object LoadingScreen : Screen("loading")
    object MasterPasswordSetup : Screen("master_password_setup")
    object Unlock : Screen("unlock")
    object CredentialList : Screen("credential_list")
    object Settings : Screen("settings")
    object CredentialDetail : Screen("credential_detail/{credentialId}") {
        fun createRoute(credentialId: Long) = "credential_detail/$credentialId"
    }
    object AddEditCredential : Screen("add_edit_credential?credentialId={credentialId}") {
        fun createRoute(credentialId: Long? = null) =
            if (credentialId != null) "add_edit_credential?credentialId=$credentialId"
            else "add_edit_credential"
    }
    object PasswordGenerator : Screen("password_generator")
    object OcrCapture : Screen("ocr_capture")
    object ImportExport : Screen("import_export")
    object CsvImport : Screen("csv_import")
    object BackupManagement : Screen("backup_management")
    object PrivacyDashboard : Screen("privacy_dashboard")
}
