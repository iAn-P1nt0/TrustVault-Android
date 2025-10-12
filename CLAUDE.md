# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

TrustVault is a privacy-first Android password manager MVP built with modern Android development practices. The app stores credentials locally with hardware-backed encryption, zero telemetry, and no cloud sync. Security is the primary concern throughout the architecture.

## Build Commands

### Building the project
```bash
./gradlew build
```

### Running tests
```bash
# Unit tests
./gradlew test

# Instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest

# Run specific test class
./gradlew test --tests "com.trustvault.android.security.DatabaseKeyDerivationTest"
```

### Running the app
```bash
# Debug build
./gradlew installDebug

# Release build
./gradlew installRelease
```

### Linting and code quality
```bash
# Run lint checks
./gradlew lint

# Generate lint report (outputs to app/build/reports/lint-results.html)
./gradlew lintDebug
```

### Cleaning build artifacts
```bash
./gradlew clean
```

## Architecture Overview

The app follows **Clean Architecture** with strict separation of concerns across three layers:

### 1. Presentation Layer (MVVM)
- **UI**: Jetpack Compose with Material 3
- **ViewModels**: State management and business logic coordination
- **Navigation**: Single-activity architecture with Navigation Compose
- **Location**: `presentation/` package

Key screens:
- Master Password Setup → First-time setup flow
- Unlock Screen → Authentication (password + biometric)
- Credential List → Search, filter, CRUD operations
- Add/Edit Credential → Form with validation
- Password Generator → Cryptographic password generation

### 2. Domain Layer
- **Models**: Pure Kotlin domain models (`Credential`, `CredentialCategory`)
- **Repository Interfaces**: Contracts for data operations
- **Use Cases**: Single-responsibility business logic (`GetAllCredentialsUseCase`, `SaveCredentialUseCase`, etc.)
- **Location**: `domain/` package

### 3. Data Layer
- **Room Database**: Local persistence with SQLCipher encryption
- **Repository Implementations**: Data operations
- **DAOs**: Database access
- **Mappers**: Entity ↔ Domain model conversion
- **Location**: `data/` package

### Cross-Cutting Concerns

#### Security Layer (`security/` package)
Critical security components - handle with extreme care:

- **DatabaseKeyManager**: Manages database encryption key lifecycle
  - Keys derived from master password at runtime (never hardcoded)
  - Keys only exist in memory during active sessions
  - Automatic key clearing on lock/logout
  - Thread-safe operations

- **DatabaseKeyDerivation**: PBKDF2-based key derivation
  - 100,000 iterations (OWASP recommended)
  - Device-specific salt binding
  - Protects against rainbow table and brute force attacks

- **FieldEncryptor**: AES-256-GCM field-level encryption
  - Uses Android Keystore for hardware-backed keys
  - Encrypts username, password, notes fields

- **PasswordHasher**: Argon2id password hashing
  - Master password verification (never stored plaintext)

- **BiometricAuthManager**: Biometric authentication wrapper
  - Fingerprint and face authentication

- **AndroidKeystoreManager**: Hardware-backed key storage
  - Uses StrongBox when available

#### Dependency Injection (`di/` package)
- **AppModule**: Application-level dependencies
- **DatabaseModule**: Database initialization (lazy, post-authentication)

## Critical Security Concepts

### Database Encryption Flow
1. User enters master password during setup
2. `DatabaseKeyDerivation` derives 256-bit key using PBKDF2
3. `DatabaseKeyManager` initializes SQLCipher database with derived key
4. Key stored in memory only during active session
5. On lock: key cleared, database closed

**IMPORTANT**: Database cannot be initialized at app startup. It requires user authentication first. ViewModels must call `databaseKeyManager.initializeDatabase(password)` after successful authentication.

### Authentication Flow
```
First Launch:
MasterPasswordSetupScreen → Create password → Initialize database → CredentialListScreen

Subsequent Launches:
UnlockScreen → Verify password + Initialize database → CredentialListScreen
```

### Key Security Rules
1. **Never hardcode encryption keys or passwords**
2. **Never log sensitive data** (passwords, keys, credentials)
3. **Always clear sensitive data from memory** after use (use `ByteArray.fill(0)`)
4. **Database initialization is lazy** - must happen after authentication
5. **All credential fields are encrypted** - username, password, notes use `FieldEncryptor`

## Common Development Tasks

### Adding a new credential field
1. Update `CredentialEntity` (data layer) with encrypted field
2. Update `Credential` domain model
3. Update `CredentialMapper` for entity ↔ domain conversion
4. Update UI screens (`AddEditCredentialScreen`, `CredentialListScreen`)
5. Update DAO queries if needed

### Adding a new screen
1. Create screen composable in `presentation/ui/screens/`
2. Create ViewModel in `presentation/viewmodel/`
3. Add route to `Screen` sealed class in `Navigation.kt`
4. Register navigation route in `MainActivity.kt`

### Modifying security components
**⚠️ EXTREME CAUTION REQUIRED**

Security components in `security/` package are critical:
- Any changes require thorough security review
- Must preserve OWASP compliance (see `SECURITY_FIX_HARDCODED_KEY.md`)
- Test thoroughly with unit + integration tests
- Never weaken encryption or key derivation parameters

## Testing Strategy

### Unit Tests
- Security components (key derivation, encryption)
- ViewModels (state management, business logic)
- Use cases (domain logic)
- Mappers (data transformation)

### Integration Tests
- Database operations with encryption
- Authentication flows
- End-to-end credential CRUD

### Manual Security Testing
- APK decompilation (verify no hardcoded keys)
- Memory dump analysis (verify key clearing)
- Device transfer (verify device-bound encryption)

## Technology Stack

**Build**: Gradle 8.2, Kotlin 1.9.20, AGP 8.2.2, JDK 17
**UI**: Compose, Material 3, Navigation Compose
**Architecture**: Hilt, Coroutines, Flow, Lifecycle, Room 2.6.1
**Security**: SQLCipher 4.5.4, AndroidX Security Crypto, Biometric API, Argon2kt 1.5.0
**Database**: Room + SQLCipher
**Min SDK**: 26 (Android 8.0)
**Target SDK**: 34 (Android 14)

## Important Files

- `SECURITY_FIX_HARDCODED_KEY.md` - Documents critical security fix for hardcoded database key vulnerability (OWASP A02:2021)
- `README.md` - User-facing documentation
- `PROJECT_SUMMARY.md` - Comprehensive project statistics and architecture
- `IMPLEMENTATION.md` - Detailed implementation guide
- `FEATURES.md` - Feature documentation

## Code Style and Conventions

- **Package structure**: Feature-based within layer (e.g., `presentation/ui/screens/auth/`)
- **Naming**: Clear, descriptive names (no abbreviations unless standard)
- **ViewModels**: Expose UI state via `StateFlow`, events via sealed classes
- **Compose**: Extract reusable components, use `remember` for computed values
- **Security**: Use `@Volatile` for thread-safe singleton state, `@Synchronized` for critical sections
- **Error handling**: Try-catch with meaningful error messages, user-facing errors via ViewModel state

## Known Issues and Limitations

- No backup/export functionality (future enhancement)
- No password change feature (requires database re-encryption)
- No auto-fill integration (future enhancement)
- Database migration is destructive (`.fallbackToDestructiveMigration()`)

## Security Notes from Recent Fixes

**CRITICAL FIX (2025-10-11)**: Removed hardcoded database encryption key vulnerability
- Previously: SQLCipher key was hardcoded as `"trustvault_db_key_v1"`
- Now: Key derived from master password using PBKDF2 with 100K iterations
- Impact: Fixed OWASP A02:2021 (Cryptographic Failures)
- Migration: Existing databases cannot be automatically migrated (requires fresh start)

When working with database initialization, ensure `DatabaseKeyManager.initializeDatabase(password)` is called in:
- `MasterPasswordViewModel.createMasterPassword()` - after initial setup
- `UnlockViewModel.unlock()` - after authentication
- Never at application startup or in `DatabaseModule` directly
