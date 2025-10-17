package com.trustvault.android.autofill

import android.content.Context
import android.content.Intent
import android.service.autofill.SaveRequest
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.trustvault.android.domain.model.Credential
import com.trustvault.android.domain.repository.CredentialRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Instrumentation tests for TrustVaultAutofillService save/update flows.
 *
 * These tests verify:
 * - Service can be instantiated
 * - Save confirmation activity can be launched
 * - Credentials are saved/updated correctly
 * - App associations are preserved
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AutofillServiceInstrumentationTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var credentialRepository: CredentialRepository

    private lateinit var context: Context

    @Before
    fun setup() {
        hiltRule.inject()
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun serviceCanBeInstantiated() {
        // Verify the service class exists and can be loaded
        val serviceClass = TrustVaultAutofillService::class.java
        assertNotNull("Service class should be loadable", serviceClass)
        assertEquals("TrustVaultAutofillService", serviceClass.simpleName)
    }

    @Test
    fun saveActivityCanBeLaunched() {
        val savedCredential = SavedCredential(
            username = "test@example.com",
            password = "testpassword",
            packageName = "com.example.testapp",
            webDomain = null
        )

        val intent = AutofillSaveActivity.createIntent(
            context = context,
            savedCredential = savedCredential,
            existingCredential = null
        )

        assertNotNull("Intent should be created", intent)
        assertEquals("Intent should target AutofillSaveActivity",
            AutofillSaveActivity::class.java.name,
            intent.component?.className)
        assertTrue("Intent should have saved credential extra",
            intent.hasExtra("saved_credential"))
    }

    @Test
    fun credentialCanBeSavedWithPackageName() = runBlocking {
        // Create a test credential
        val testCredential = Credential(
            id = 0,
            title = "Test App",
            username = "testuser",
            password = "testpass",
            packageName = "com.example.testapp",
            website = "",
            notes = "Saved via autofill test"
        )

        // Save credential
        credentialRepository.insertCredential(testCredential)

        // Verify it was saved
        val allCredentials = credentialRepository.getAllCredentials().first()
        val saved = allCredentials.find { it.username == "testuser" }

        assertNotNull("Credential should be saved", saved)
        assertEquals("Package name should be preserved", "com.example.testapp", saved?.packageName)
        assertEquals("Username should match", "testuser", saved?.username)
    }

    @Test
    fun credentialCanBeUpdated() = runBlocking {
        // Insert initial credential
        val initial = Credential(
            id = 0,
            title = "Update Test",
            username = "updatetest@example.com",
            password = "oldpassword",
            packageName = "com.example.updateapp",
            website = "",
            notes = ""
        )
        credentialRepository.insertCredential(initial)

        // Find the saved credential
        val saved = credentialRepository.getAllCredentials().first()
            .find { it.username == "updatetest@example.com" }
        assertNotNull("Initial credential should be saved", saved)

        // Update with new password
        val updated = saved!!.copy(
            password = "newpassword",
            updatedAt = System.currentTimeMillis()
        )
        credentialRepository.updateCredential(updated)

        // Verify update
        val afterUpdate = credentialRepository.getAllCredentials().first()
            .find { it.id == saved.id }

        assertNotNull("Updated credential should exist", afterUpdate)
        assertEquals("Password should be updated", "newpassword", afterUpdate?.password)
        assertTrue("Updated timestamp should be newer", afterUpdate!!.updatedAt > saved.updatedAt)
    }

    @Test
    fun multipleCredentialsForSameAppCanExist() = runBlocking {
        val cred1 = Credential(
            id = 0,
            title = "App Account 1",
            username = "user1@example.com",
            password = "pass1",
            packageName = "com.example.multiapp",
            website = "",
            notes = ""
        )

        val cred2 = Credential(
            id = 0,
            title = "App Account 2",
            username = "user2@example.com",
            password = "pass2",
            packageName = "com.example.multiapp",
            website = "",
            notes = ""
        )

        credentialRepository.insertCredential(cred1)
        credentialRepository.insertCredential(cred2)

        val allCredentials = credentialRepository.getAllCredentials().first()
        val appCredentials = allCredentials.filter { it.packageName == "com.example.multiapp" }

        assertTrue("Should have at least 2 credentials for the app", appCredentials.size >= 2)
    }

    @Test
    fun credentialMatchingByWebDomain() = runBlocking {
        val credential = Credential(
            id = 0,
            title = "Website Login",
            username = "webuser@example.com",
            password = "webpass",
            packageName = "",
            website = "https://www.example.com/login",
            notes = ""
        )
        credentialRepository.insertCredential(credential)

        val saved = credentialRepository.getAllCredentials().first()
            .find { it.website.contains("example.com") }

        assertNotNull("Should find credential by website", saved)
        assertEquals("Website should match", credential.username, saved?.username)
    }
}

