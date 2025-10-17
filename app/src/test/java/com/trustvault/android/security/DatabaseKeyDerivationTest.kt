package com.trustvault.android.security

import android.content.Context
import android.provider.Settings
import com.trustvault.android.util.secureWipe
import io.mockk.*
import java.security.SecureRandom
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for DatabaseKeyDerivation.
 *
 * Tests verify:
 * - Key derivation produces correct key length (32 bytes = 256 bits)
 * - Same password produces same key (deterministic with same salt)
 * - Different passwords produce different keys
 * - Empty password is rejected
 * - Long passwords work correctly
 * - Key derivation data can be cleared
 * - Password validation works correctly
 */
class DatabaseKeyDerivationTest {

    private lateinit var mockContext: Context
    private lateinit var mockKeystoreManager: AndroidKeystoreManager
    private lateinit var databaseKeyDerivation: DatabaseKeyDerivation

    private val testDeviceId = "test_device_id"
    private val testSalt = ByteArray(16) { it.toByte() } // Deterministic salt for testing

    @Before
    fun setup() {
        mockContext = mockk(relaxed = true)
        mockKeystoreManager = mockk(relaxed = true)

        // Mock Settings.Secure.getString to return test device ID
        mockkStatic(Settings.Secure::class)
        every { Settings.Secure.getString(any(), Settings.Secure.ANDROID_ID) } returns testDeviceId

        // Mock SecureRandom to produce deterministic salt
        mockkConstructor(SecureRandom::class)
        every { anyConstructed<SecureRandom>().nextBytes(any()) } answers {
            val arr = firstArg<ByteArray>()
            // fill with testSalt
            for (i in arr.indices) {
                arr[i] = testSalt[i % testSalt.size]
            }
        }

        // Mock SharedPreferences with in-memory persistence for KEY_SALT
        val mockPrefs = mockk<android.content.SharedPreferences>(relaxed = true)
        val mockEditor = mockk<android.content.SharedPreferences.Editor>(relaxed = true)
        var storedSaltBase64: String? = null
        every { mockContext.getSharedPreferences(any(), any()) } returns mockPrefs
        every { mockPrefs.getString(match { it == "db_key_salt" }, any()) } answers { storedSaltBase64 }
        every { mockPrefs.getString(any(), any()) } answers { storedSaltBase64 }
        every { mockPrefs.edit() } returns mockEditor
        every { mockEditor.putString(match { it == "db_key_salt" }, any()) } answers {
            storedSaltBase64 = secondArg()
            mockEditor
        }
        every { mockEditor.putString(any(), any()) } answers {
            storedSaltBase64 = secondArg()
            mockEditor
        }
        every { mockEditor.apply() } just Runs

        // Mock keystore manager to pass-through the salt deterministically
        every { mockKeystoreManager.encrypt(any(), any()) } answers {
            // encode: prefix + salt (copied from arg)
            val saltArg = secondArg<ByteArray>()
            byteArrayOf(1,2,3,4) + saltArg
        }
        every { mockKeystoreManager.decrypt(any(), any()) } answers {
            val enc = secondArg<ByteArray>()
            if (enc.size >= 4) enc.copyOfRange(4, enc.size) else enc
        }
        every { mockKeystoreManager.deleteKey(any()) } just Runs

        databaseKeyDerivation = DatabaseKeyDerivation(mockContext, mockKeystoreManager)
    }

    @After
    fun teardown() {
        unmockkStatic(Settings.Secure::class)
    }

    @Test
    fun `deriveKey produces correct key length`() {
        val password = "TestPassword123!".toCharArray()
        var key: ByteArray? = null

        try {
            key = databaseKeyDerivation.deriveKey(password)

            assertNotNull("Key should not be null", key)
            assertEquals("Key should be 32 bytes (256 bits)", 32, key.size)
        } finally {
            password.secureWipe()
            key?.secureWipe()
        }
    }

    @Test
    fun `deriveKey produces same key for same password`() {
        val password1 = "TestPassword123!".toCharArray()
        val password2 = "TestPassword123!".toCharArray()
        var key1: ByteArray? = null
        var key2: ByteArray? = null

        try {
            key1 = databaseKeyDerivation.deriveKey(password1)
            key2 = databaseKeyDerivation.deriveKey(password2)

            assertNotNull("First key should not be null", key1)
            assertNotNull("Second key should not be null", key2)
            assertArrayEquals("Same password should produce same key", key1, key2)
        } finally {
            password1.secureWipe()
            password2.secureWipe()
            key1?.secureWipe()
            key2?.secureWipe()
        }
    }

    @Test
    fun `deriveKey produces different keys for different passwords`() {
        val password1 = "TestPassword123!".toCharArray()
        val password2 = "DifferentPassword456!".toCharArray()
        var key1: ByteArray? = null
        var key2: ByteArray? = null

        try {
            key1 = databaseKeyDerivation.deriveKey(password1)
            key2 = databaseKeyDerivation.deriveKey(password2)

            assertNotNull("First key should not be null", key1)
            assertNotNull("Second key should not be null", key2)
            assertFalse("Different passwords should produce different keys",
                key1!!.contentEquals(key2))
        } finally {
            password1.secureWipe()
            password2.secureWipe()
            key1?.secureWipe()
            key2?.secureWipe()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `deriveKey throws exception for empty password`() {
        val password = "".toCharArray()

        try {
            databaseKeyDerivation.deriveKey(password)
        } finally {
            password.secureWipe()
        }
    }

    @Test
    fun `deriveKey works with long password`() {
        val password = "a".repeat(1000).toCharArray()
        var key: ByteArray? = null

        try {
            key = databaseKeyDerivation.deriveKey(password)

            assertNotNull("Key should not be null for long password", key)
            assertEquals("Key should be 32 bytes even for long password", 32, key.size)
        } finally {
            password.secureWipe()
            key?.secureWipe()
        }
    }

    @Test
    fun `deriveKey works with unicode password`() {
        val password = "Test密码🔒".toCharArray()
        var key: ByteArray? = null

        try {
            key = databaseKeyDerivation.deriveKey(password)

            assertNotNull("Key should not be null for unicode password", key)
            assertEquals("Key should be 32 bytes for unicode password", 32, key.size)
        } finally {
            password.secureWipe()
            key?.secureWipe()
        }
    }

    @Test
    fun `validateMasterPassword returns true for valid password`() {
        val password = "TestPassword123!".toCharArray()

        try {
            val isValid = databaseKeyDerivation.validateMasterPassword(password)

            assertTrue("Valid password should return true", isValid)
        } finally {
            password.secureWipe()
        }
    }

    @Test
    fun `validateMasterPassword returns false for empty password`() {
        val password = "".toCharArray()

        try {
            val isValid = databaseKeyDerivation.validateMasterPassword(password)

            assertFalse("Empty password should return false", isValid)
        } finally {
            password.secureWipe()
        }
    }

    @Test
    fun `clearKeyDerivationData calls keystore manager`() {
        databaseKeyDerivation.clearKeyDerivationData()

        verify { mockKeystoreManager.deleteKey(any()) }
    }

    @Test
    fun `deriveKey key is deterministic with same salt`() {
        val password = "TestPassword123!".toCharArray()
        var key1: ByteArray? = null
        var key2: ByteArray? = null
        var key3: ByteArray? = null

        try {
            // Derive key three times
            key1 = databaseKeyDerivation.deriveKey(password)
            key2 = databaseKeyDerivation.deriveKey(password)
            key3 = databaseKeyDerivation.deriveKey(password)

            // All keys should be identical
            assertArrayEquals("First and second keys should match", key1, key2)
            assertArrayEquals("Second and third keys should match", key2, key3)
        } finally {
            password.secureWipe()
            key1?.secureWipe()
            key2?.secureWipe()
            key3?.secureWipe()
        }
    }

    @Test
    fun `deriveKey produces non-zero key`() {
        val password = "TestPassword123!".toCharArray()
        var key: ByteArray? = null

        try {
            key = databaseKeyDerivation.deriveKey(password)

            assertNotNull("Key should not be null", key)

            // Check that key is not all zeros
            val hasNonZero = key!!.any { it != 0.toByte() }
            assertTrue("Key should contain non-zero bytes", hasNonZero)
        } finally {
            password.secureWipe()
            key?.secureWipe()
        }
    }

    @Test
    fun `deriveKey with special characters works correctly`() {
        val password = "Test!@#\$%^&*()_+-=[]{}|;':\",./<>?".toCharArray()
        var key: ByteArray? = null

        try {
            key = databaseKeyDerivation.deriveKey(password)

            assertNotNull("Key should not be null for password with special chars", key)
            assertEquals("Key should be 32 bytes", 32, key.size)
        } finally {
            password.secureWipe()
            key?.secureWipe()
        }
    }
}
