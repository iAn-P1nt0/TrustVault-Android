# Security Fix: Hardcoded Database Encryption Key

## Vulnerability Overview

**OWASP Classification:** A02:2021 - Cryptographic Failures
**Severity:** CRITICAL
**Status:** ✅ FIXED

### Original Vulnerability

The SQLCipher database encryption key was hardcoded in `DatabaseModule.kt`:

```kotlin
// VULNERABLE CODE (REMOVED)
val passphrase = SQLiteDatabase.getBytes("trustvault_db_key_v1".toCharArray())
```

**Impact:**
- Complete database compromise
- Anyone with APK access could extract the hardcoded key
- Rendered SQLCipher encryption useless
- All stored passwords effectively unencrypted

## Solution Implemented

### Architecture Overview

The fix implements a secure key derivation system that derives the database encryption key from the user's master password at runtime:

```
Master Password (User Input)
    ↓
PBKDF2-HMAC-SHA256 (100,000 iterations)
    ↓
256-bit Encryption Key
    ↓
SQLCipher Database
```

### Components Created

#### 1. **DatabaseKeyDerivation.kt**
Handles secure key derivation from master password using:
- **PBKDF2-HMAC-SHA256** with 100,000 iterations (OWASP recommended)
- **Device-specific salt** (ANDROID_ID)
- **Random cryptographic salt** (stored encrypted in Android Keystore)
- **256-bit output** for AES-256 encryption

**Security Features:**
- Multiple entropy sources (password + device + random salt)
- Protection against rainbow table attacks
- Computationally expensive derivation (prevents brute force)
- Secure memory clearing after use

**Location:** `app/src/main/java/com/trustvault/android/security/DatabaseKeyDerivation.kt`

#### 2. **DatabaseKeyManager.kt**
Manages database encryption key lifecycle:
- **Lazy initialization** after authentication
- **Key stored in memory** only during active session
- **Automatic key clearing** when app is locked
- **Thread-safe** synchronized operations

**Key Methods:**
- `initializeDatabase(masterPassword)` - Derives key and initializes database
- `getDatabase()` - Returns initialized database or throws exception
- `lockDatabase()` - Clears keys from memory and closes database
- `validatePassword(masterPassword)` - Validates password can unlock database

**Location:** `app/src/main/java/com/trustvault/android/security/DatabaseKeyManager.kt`

### Integration Changes

#### 3. **DatabaseModule.kt** (Modified)
**Before:**
```kotlin
@Provides
@Singleton
fun provideTrustVaultDatabase(@ApplicationContext context: Context): TrustVaultDatabase {
    val passphrase = SQLiteDatabase.getBytes("trustvault_db_key_v1".toCharArray())
    // ... hardcoded key used
}
```

**After:**
```kotlin
@Provides
@Singleton
fun provideCredentialDao(databaseKeyManager: DatabaseKeyManager): CredentialDao {
    return databaseKeyManager.getDatabase().credentialDao()
}
```

Database is now initialized lazily through `DatabaseKeyManager` instead of at app startup.

#### 4. **MasterPasswordViewModel.kt** (Modified)
Added database initialization after master password setup:

```kotlin
fun createMasterPassword(onSuccess: () -> Unit) {
    // ... validation ...
    val hash = passwordHasher.hashPassword(state.password)
    preferencesManager.setMasterPasswordHash(hash)

    // NEW: Initialize database with derived key
    databaseKeyManager.initializeDatabase(state.password)

    onSuccess()
}
```

#### 5. **UnlockViewModel.kt** (Modified)
Added database initialization after successful authentication:

```kotlin
fun unlock(onSuccess: () -> Unit) {
    // ... password verification ...
    if (isValid) {
        // NEW: Initialize database with derived key
        databaseKeyManager.initializeDatabase(password)
        onSuccess()
    }
}
```

#### 6. **MainViewModel.kt** (Modified)
Added database lock management:

```kotlin
fun lockDatabase() {
    databaseKeyManager.lockDatabase()
}

override fun onCleared() {
    super.onCleared()
    lockDatabase() // Ensure database is locked when ViewModel destroyed
}
```

## Security Benefits

### ✅ Eliminated Vulnerabilities

1. **No Hardcoded Secrets**
   - Database key derived at runtime from user password
   - No static keys in source code or APK

2. **Strong Key Derivation**
   - PBKDF2-HMAC-SHA256 with 100,000 iterations
   - Computationally expensive (prevents brute force)
   - Device binding prevents key reuse across devices

3. **Defense in Depth**
   - Multiple entropy sources
   - Encrypted salt storage in Android Keystore
   - Memory-safe key management

4. **Proper Key Lifecycle**
   - Keys only in memory during active sessions
   - Automatic clearing on lock/logout
   - No persistent storage of derived keys

### 🔒 OWASP Compliance

- **A02:2021 - Cryptographic Failures:** FIXED
  - ✅ Strong key derivation function (PBKDF2)
  - ✅ Proper iteration count (100,000)
  - ✅ Unique keys per installation
  - ✅ No hardcoded cryptographic material

## Testing Recommendations

### Unit Tests
```kotlin
@Test
fun testKeyDerivation_samePassword_sameDevice_producesConsistentKey() {
    val key1 = keyDerivation.deriveKey("TestPassword123!")
    val key2 = keyDerivation.deriveKey("TestPassword123!")
    assertArrayEquals(key1, key2)
}

@Test
fun testKeyDerivation_differentPasswords_produceDifferentKeys() {
    val key1 = keyDerivation.deriveKey("Password1")
    val key2 = keyDerivation.deriveKey("Password2")
    assertFalse(key1.contentEquals(key2))
}

@Test
fun testDatabaseLock_clearsKeysFromMemory() {
    databaseKeyManager.initializeDatabase("TestPassword")
    assertTrue(databaseKeyManager.isDatabaseInitialized())

    databaseKeyManager.lockDatabase()
    assertFalse(databaseKeyManager.isDatabaseInitialized())
}
```

### Integration Tests
1. Create master password → Verify database initialized
2. Lock app → Verify database locked and keys cleared
3. Unlock with password → Verify database re-initialized
4. Wrong password → Verify database remains locked
5. Reinstall app → Verify new device generates different key

### Manual Security Testing
1. **APK Decompilation Test**
   - Decompile APK and search for any hardcoded keys
   - Verify no "trustvault_db_key" strings in code

2. **Memory Dump Test**
   - Lock database
   - Dump app memory
   - Verify encryption key is not present

3. **Device Transfer Test**
   - Export database file
   - Try to open on different device
   - Verify cannot open without master password

## Migration Notes

### For Existing Users

**⚠️ IMPORTANT:** This change is NOT backward compatible.

Existing databases encrypted with the old hardcoded key cannot be automatically migrated because:
1. The old hardcoded key is being removed
2. We don't know the user's master password to derive the new key
3. We can't decrypt with old key and re-encrypt with new key

**Options:**

#### Option 1: Fresh Start (Recommended for MVP)
```kotlin
// In DatabaseKeyManager or migration script
context.deleteDatabase("trustvault_database")
keyDerivation.clearKeyDerivationData()
```

#### Option 2: Migration Path (For Production)
Implement a one-time migration:
1. On first launch after update, detect old database
2. Decrypt with old hardcoded key
3. Prompt user for master password
4. Re-encrypt entire database with new derived key
5. Delete old database

```kotlin
fun migrateToSecureEncryption(masterPassword: String) {
    // 1. Open old database with hardcoded key
    val oldDb = openOldDatabase()
    val credentials = oldDb.credentialDao().getAllSync()

    // 2. Initialize new database with derived key
    val newDb = databaseKeyManager.initializeDatabase(masterPassword)

    // 3. Copy all data
    credentials.forEach { newDb.credentialDao().insert(it) }

    // 4. Delete old database
    context.deleteDatabase("trustvault_database_old")
}
```

## Performance Considerations

### Key Derivation Time
- PBKDF2 with 100,000 iterations takes ~100-300ms on modern devices
- This is intentional (prevents brute force attacks)
- Show loading indicator during authentication

### Memory Usage
- Encryption key: 32 bytes (256 bits)
- Salt: 16 bytes
- Minimal memory overhead

### Battery Impact
- Key derivation only on login/unlock (not continuous)
- Negligible battery impact

## Future Enhancements

### Recommended Improvements

1. **Increase PBKDF2 Iterations**
   ```kotlin
   // Consider increasing to 310,000+ iterations
   private const val PBKDF2_ITERATIONS = 310_000
   ```

2. **Argon2 for Key Derivation**
   ```kotlin
   // Use Argon2id instead of PBKDF2 for better security
   // Already have argon2kt dependency
   ```

3. **Biometric-Protected Key Cache**
   ```kotlin
   // Cache derived key encrypted with biometric key
   // Faster unlock with biometric without re-deriving
   ```

4. **Key Rotation**
   ```kotlin
   // Allow users to change master password
   // Automatically re-encrypt database
   ```

5. **Hardware Security Module (HSM)**
   ```kotlin
   // On supported devices, use StrongBox keystore
   .setIsStrongBoxBacked(true)
   ```

## Security Audit Checklist

- [x] No hardcoded encryption keys
- [x] Strong key derivation function (PBKDF2/Argon2)
- [x] Sufficient iteration count (100,000+)
- [x] Unique salt per installation
- [x] Device binding for additional entropy
- [x] Encrypted salt storage
- [x] Memory-safe key management
- [x] Automatic key clearing on lock
- [x] Thread-safe operations
- [x] Proper error handling

## References

- **OWASP Mobile Security:** https://owasp.org/www-project-mobile-security-testing-guide/
- **PBKDF2 Recommendations:** https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html
- **Android Keystore:** https://developer.android.com/training/articles/keystore
- **SQLCipher Documentation:** https://www.zetetic.net/sqlcipher/

## Author
Security fix implemented by Claude Code Security Auditor Agent
Date: 2025-10-11
