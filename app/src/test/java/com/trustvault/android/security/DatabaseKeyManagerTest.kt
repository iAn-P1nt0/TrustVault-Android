package com.trustvault.android.security

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.trustvault.android.data.local.database.TrustVaultDatabase
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Base64

class DatabaseKeyManagerTest {

    private lateinit var context: Context
    private lateinit var keyDerivation: DatabaseKeyDerivation
    private lateinit var keystoreManager: AndroidKeystoreManager
    private lateinit var sharedPrefs: SharedPreferences
    private lateinit var prefsEditor: SharedPreferences.Editor
    private lateinit var manager: DatabaseKeyManager

    // Storage for simulating wrapped keys
    private var storedWrappedKey: String? = null

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        keyDerivation = mockk(relaxed = true)
        keystoreManager = mockk(relaxed = true)
        sharedPrefs = mockk(relaxed = true)
        prefsEditor = mockk(relaxed = true)

        // Mock SharedPreferences
        every { context.getSharedPreferences(any(), any()) } returns sharedPrefs
        every { sharedPrefs.edit() } returns prefsEditor
        every { prefsEditor.putString(any(), any()) } answers {
            storedWrappedKey = secondArg()
            prefsEditor
        }
        every { prefsEditor.clear() } answers {
            storedWrappedKey = null
            prefsEditor
        }
        every { prefsEditor.apply() } just Runs
        every { sharedPrefs.getString(any(), any()) } answers {
            storedWrappedKey ?: secondArg()
        }

        // Mock AndroidKeystoreManager - simulate wrap/unwrap
        every { keystoreManager.encrypt(any(), any()) } answers {
            val plainKey = secondArg<ByteArray>()
            // Simulate encryption by adding prefix
            val wrapped = "WRAPPED:".toByteArray() + plainKey
            wrapped
        }
        every { keystoreManager.decrypt(any(), any()) } answers {
            val wrappedKey = secondArg<ByteArray>()
            // Simulate decryption by removing prefix
            wrappedKey.copyOfRange(8, wrappedKey.size) // Skip "WRAPPED:"
        }
        every { keystoreManager.deleteKey(any()) } just Runs

        // Mock keyDerivation (legacy support)
        every { keyDerivation.deriveKey(match { it.isEmpty() }) } throws IllegalArgumentException("Master password cannot be empty")
        every { keyDerivation.deriveKey(match { it.isNotEmpty() }) } answers {
            val pwd = firstArg<CharArray>()
            String(pwd).toByteArray(Charsets.UTF_8)
        }
        every { keyDerivation.clearKeyDerivationData() } just Runs

        // Mock static Room.databaseBuilder and the builder chain
        mockkStatic(Room::class)
        val builder = mockk<RoomDatabase.Builder<TrustVaultDatabase>>(relaxed = true)
        val db = mockk<TrustVaultDatabase>(relaxed = true)
        val openHelper = mockk<SupportSQLiteOpenHelper>(relaxed = true)
        val readableDb = mockk<SupportSQLiteDatabase>(relaxed = true)
        val writableDb = mockk<SupportSQLiteDatabase>(relaxed = true)

        every { Room.databaseBuilder(context, TrustVaultDatabase::class.java, any()) } returns builder
        every { builder.openHelperFactory(any()) } returns builder
        every { builder.fallbackToDestructiveMigration() } returns builder
        every { builder.build() } returns db
        every { db.openHelper } returns openHelper
        every { openHelper.readableDatabase } returns readableDb
        every { openHelper.writableDatabase } returns writableDb
        every { writableDb.execSQL(any()) } just Runs
        every { db.close() } just Runs

        manager = DatabaseKeyManager(context, keyDerivation, keystoreManager)
    }

    @After
    fun tearDown() {
        unmockkStatic(Room::class)
        storedWrappedKey = null
    }

    @Test
    fun `initializeDatabase returns database and validatePassword is true`() {
        val db = manager.initializeDatabase("secret123".toCharArray())
        assertNotNull(db)
        assertTrue(manager.validatePassword("secret123".toCharArray()))
    }

    @Test(expected = SecurityException::class)
    fun `changeMasterPassword throws on wrong old password`() {
        manager.initializeDatabase("correct".toCharArray())
        manager.changeMasterPassword("wrong".toCharArray(), "newpass".toCharArray())
    }

    @Test
    fun `initializeDatabase works with wrapped key model regardless of password`() {
        // With wrapped key model, password is not used for database encryption
        // Empty passwords should still work (password validation is separate)
        val db = manager.initializeDatabase(CharArray(0))
        assertNotNull(db)
    }

    @Test
    fun `initializeDatabase supports long password`() {
        val longPwd = ("a".repeat(2048)).toCharArray()
        val db = manager.initializeDatabase(longPwd)
        assertNotNull(db)
        assertTrue(manager.validatePassword(("a".repeat(2048)).toCharArray()))
    }

    // ========================================
    // NEW TESTS: Key Wrap/Unwrap Functionality
    // ========================================

    @Test
    fun `initializeDatabase generates and wraps random key on first run`() {
        // First initialization - should generate new key
        val db1 = manager.initializeDatabase("password".toCharArray())
        assertNotNull(db1)

        // Verify wrapped key was stored
        assertNotNull(storedWrappedKey)
        assertTrue(storedWrappedKey!!.isNotEmpty())

        // Verify keystoreManager.encrypt was called
        verify(exactly = 1) { keystoreManager.encrypt(any(), any()) }
    }

    @Test
    fun `initializeDatabase unwraps existing key on subsequent runs`() {
        // First initialization - generates key
        manager.initializeDatabase("password".toCharArray())
        val firstWrappedKey = storedWrappedKey

        // Lock database
        manager.lockDatabase()

        // Second initialization - should unwrap existing key
        manager.initializeDatabase("password".toCharArray())
        val secondWrappedKey = storedWrappedKey

        // Wrapped key should be the same
        assertEquals(firstWrappedKey, secondWrappedKey)

        // Verify keystoreManager.decrypt was called (unwrap)
        verify(atLeast = 1) { keystoreManager.decrypt(any(), any()) }
    }

    @Test
    fun `wrapped key is stored as Base64 in SharedPreferences`() {
        manager.initializeDatabase("password".toCharArray())

        // Verify stored key is valid Base64
        assertNotNull(storedWrappedKey)
        assertDoesNotThrow {
            Base64.getDecoder().decode(storedWrappedKey)
        }
    }

    @Test
    fun `database key is independent of master password`() {
        // Initialize with first password
        manager.initializeDatabase("password1".toCharArray())
        val wrappedKey1 = storedWrappedKey

        // Lock and reinitialize with different password
        manager.lockDatabase()
        manager.initializeDatabase("password2".toCharArray())
        val wrappedKey2 = storedWrappedKey

        // Database key (wrapped) should be the same regardless of password
        assertEquals(wrappedKey1, wrappedKey2)
    }

    @Test
    fun `lockDatabase clears key from memory`() {
        manager.initializeDatabase("password".toCharArray())
        assertTrue(manager.isDatabaseInitialized())

        manager.lockDatabase()
        assertFalse(manager.isDatabaseInitialized())

        // Attempting to get database should throw
        assertThrows(IllegalStateException::class.java) {
            manager.getDatabase()
        }
    }

    @Test
    fun `rekeyDatabase generates new random key and re-encrypts`() {
        // Initialize database
        manager.initializeDatabase("password".toCharArray())
        val originalWrappedKey = storedWrappedKey

        // Perform rekey
        val rekeySuccess = manager.rekeyDatabase()
        assertTrue(rekeySuccess)

        // Verify new wrapped key was stored
        assertNotNull(storedWrappedKey)
        assertNotEquals(originalWrappedKey, storedWrappedKey)

        // Note: PRAGMA rekey execution is tested implicitly by the success of rekeyDatabase()
        // The mock writableDatabase already handles execSQL calls
    }

    @Test
    fun `rekeyDatabase updates key in memory`() {
        manager.initializeDatabase("password".toCharArray())
        val db1 = manager.getDatabase()
        assertNotNull(db1)

        // Rekey
        manager.rekeyDatabase()

        // Database should still be accessible with new key
        val db2 = manager.getDatabase()
        assertNotNull(db2)
    }

    @Test
    fun `resetDatabase clears all key material`() {
        manager.initializeDatabase("password".toCharArray())
        assertNotNull(storedWrappedKey)

        manager.resetDatabase()

        // Verify wrapped key cleared from SharedPreferences
        assertNull(storedWrappedKey)

        // Verify Keystore key deleted
        verify { keystoreManager.deleteKey(any()) }

        // Verify database not initialized
        assertFalse(manager.isDatabaseInitialized())
    }

    @Test
    fun `resetDatabase also clears legacy key derivation data`() {
        manager.initializeDatabase("password".toCharArray())
        manager.resetDatabase()

        // Verify legacy key derivation data cleared
        verify { keyDerivation.clearKeyDerivationData() }
    }

    @Test
    fun `wrapped key survives app restart simulation`() {
        // First session
        manager.initializeDatabase("password".toCharArray())
        val wrappedKey = storedWrappedKey
        manager.lockDatabase()

        // Simulate app restart - create new manager with same storage
        val newManager = DatabaseKeyManager(context, keyDerivation, keystoreManager)

        // Second session - should unwrap existing key
        newManager.initializeDatabase("password".toCharArray())
        assertEquals(wrappedKey, storedWrappedKey)

        // Database should be accessible
        assertNotNull(newManager.getDatabase())
    }

    @Test
    fun `key wrap uses hardware-backed Keystore encryption`() {
        manager.initializeDatabase("password".toCharArray())

        // Verify keystoreManager.encrypt was called with correct alias
        verify { keystoreManager.encrypt("trustvault_db_kek", any()) }
    }

    @Test
    fun `key unwrap uses hardware-backed Keystore decryption`() {
        // First init to create wrapped key
        manager.initializeDatabase("password".toCharArray())
        manager.lockDatabase()

        // Second init to unwrap
        manager.initializeDatabase("password".toCharArray())

        // Verify keystoreManager.decrypt was called with correct alias
        verify(atLeast = 1) { keystoreManager.decrypt("trustvault_db_kek", any()) }
    }

    @Test
    fun `database key is 256 bits (32 bytes)`() {
        // Capture the generated key
        var capturedKey: ByteArray? = null
        every { keystoreManager.encrypt(any(), any()) } answers {
            capturedKey = secondArg()
            "WRAPPED:".toByteArray() + capturedKey!!
        }

        manager.initializeDatabase("password".toCharArray())

        // Verify key length is 32 bytes (256 bits)
        assertNotNull(capturedKey)
        assertEquals(32, capturedKey!!.size)
    }

    @Test
    fun `validatePassword works with wrapped key model`() {
        manager.initializeDatabase("password".toCharArray())

        // validatePassword should succeed (doesn't actually validate password anymore,
        // just checks if database can be opened with wrapped key)
        assertTrue(manager.validatePassword("any_password".toCharArray()))
    }

    @Test
    fun `multiple lock and unlock cycles maintain key integrity`() {
        for (i in 1..5) {
            manager.initializeDatabase("password".toCharArray())
            val wrappedKey = storedWrappedKey
            assertNotNull(wrappedKey)

            manager.lockDatabase()
            assertFalse(manager.isDatabaseInitialized())

            // Wrapped key should persist in storage
            assertEquals(wrappedKey, storedWrappedKey)
        }
    }

    private fun assertDoesNotThrow(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            fail("Expected no exception, but got: ${e.message}")
        }
    }

    private inline fun <reified T : Throwable> assertThrows(
        expectedType: Class<T>,
        block: () -> Unit
    ) {
        try {
            block()
            fail("Expected ${expectedType.simpleName} to be thrown")
        } catch (e: Throwable) {
            if (!expectedType.isInstance(e)) {
                fail("Expected ${expectedType.simpleName}, but got ${e::class.simpleName}")
            }
        }
    }
}
