package com.trustvault.android.credentialmanager

import android.content.Context
import com.trustvault.android.domain.model.Credential
import com.trustvault.android.domain.repository.CredentialRepository
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for CredentialManagerFacade.
 *
 * Tests:
 * - Credential matching by package and domain
 * - Domain extraction and normalization
 * - Credential saving to vault
 * - Title generation
 */
class CredentialManagerFacadeTest {

    private lateinit var context: Context
    private lateinit var credentialRepository: CredentialRepository
    private lateinit var facade: CredentialManagerFacade

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        credentialRepository = mockk(relaxed = true)
        val passkeyManager = mockk<PasskeyManager>(relaxed = true)
        val databaseKeyManager = mockk<com.trustvault.android.security.DatabaseKeyManager>(relaxed = true)
        facade = CredentialManagerFacade(context, credentialRepository, passkeyManager, databaseKeyManager)
    }

    @Test
    fun `getMatchingCredentials finds credential by package name`() = runBlocking {
        val credential = Credential(
            id = 1L,
            title = "Gmail",
            username = "user@gmail.com",
            password = "password123",
            packageName = "com.google.android.gm",
            website = ""
        )

        every { credentialRepository.getAllCredentials() } returns flowOf(listOf(credential))

        val matches = facade.getMatchingCredentials(
            packageName = "com.google.android.gm",
            webDomain = null
        )

        assertEquals(1, matches.size)
        assertEquals(credential.id, matches[0].id)
    }

    @Test
    fun `getMatchingCredentials finds credential by web domain`() = runBlocking {
        val credential = Credential(
            id = 1L,
            title = "GitHub",
            username = "user@example.com",
            password = "password123",
            packageName = "",
            website = "https://github.com/login"
        )

        every { credentialRepository.getAllCredentials() } returns flowOf(listOf(credential))

        val matches = facade.getMatchingCredentials(
            packageName = "com.android.chrome",
            webDomain = "github.com"
        )

        assertEquals(1, matches.size)
        assertEquals(credential.id, matches[0].id)
    }

    @Test
    fun `getMatchingCredentials handles subdomain matching`() = runBlocking {
        val credential = Credential(
            id = 1L,
            title = "Example",
            username = "user",
            password = "pass",
            packageName = "",
            website = "https://login.example.com"
        )

        every { credentialRepository.getAllCredentials() } returns flowOf(listOf(credential))

        val matches = facade.getMatchingCredentials(
            packageName = "",
            webDomain = "example.com"
        )

        assertTrue(matches.isNotEmpty())
    }

    @Test
    fun `getMatchingCredentials returns empty list when no matches`() = runBlocking {
        val credential = Credential(
            id = 1L,
            title = "App1",
            username = "user",
            password = "pass",
            packageName = "com.app1",
            website = ""
        )

        every { credentialRepository.getAllCredentials() } returns flowOf(listOf(credential))

        val matches = facade.getMatchingCredentials(
            packageName = "com.app2",
            webDomain = null
        )

        assertTrue(matches.isEmpty())
    }

    @Test
    fun `toPasswordCredential converts correctly`() {
        val credential = Credential(
            id = 1L,
            title = "Test",
            username = "test@example.com",
            password = "testpass",
            packageName = "com.test",
            website = ""
        )

        val passwordCred = facade.toPasswordCredential(credential)

        assertEquals(credential.username, passwordCred.id)
        assertEquals(credential.password, passwordCred.password)
    }

    @Test
    fun `domain extraction works for various URL formats`() {
        // Test through matching which internally uses extractDomain
        val testCases = listOf(
            "https://example.com" to "example.com",
            "http://example.com/path" to "example.com",
            "www.example.com" to "example.com",
            "https://subdomain.example.com:8080/path" to "subdomain.example.com"
        )

        testCases.forEach { (input, expected) ->
            val credential = Credential(
                id = 1L,
                title = "Test",
                username = "user",
                password = "pass",
                packageName = "",
                website = input
            )

            every { credentialRepository.getAllCredentials() } returns flowOf(listOf(credential))

            val matches = runBlocking {
                facade.getMatchingCredentials("", expected)
            }

            assertTrue("Failed for input: $input", matches.isNotEmpty())
        }
    }

    @Test
    fun `multiple credentials for same app are all returned`() = runBlocking {
        val cred1 = Credential(
            id = 1L,
            title = "Account 1",
            username = "user1@example.com",
            password = "pass1",
            packageName = "com.example.app",
            website = ""
        )

        val cred2 = Credential(
            id = 2L,
            title = "Account 2",
            username = "user2@example.com",
            password = "pass2",
            packageName = "com.example.app",
            website = ""
        )

        every { credentialRepository.getAllCredentials() } returns flowOf(listOf(cred1, cred2))

        val matches = facade.getMatchingCredentials(
            packageName = "com.example.app",
            webDomain = null
        )

        assertEquals(2, matches.size)
    }
}

