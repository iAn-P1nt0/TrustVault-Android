# TrustVault Features Documentation

## 1. Master Password Setup
**Screen**: `MasterPasswordSetupScreen`

First-time setup flow where users create their master password:
- Input master password field with visibility toggle
- Confirm password field
- Real-time password strength indicator (Weak/Medium/Strong)
- Validation checks:
  - Minimum 8 characters
  - Passwords must match
- Security warning about password recovery
- Argon2id hashing on save

**Security**: Password never stored in plaintext, only Argon2id hash is persisted.

---

## 2. Unlock Screen
**Screen**: `UnlockScreen`

Authentication screen to access the vault:
- Master password input with visibility toggle
- Biometric authentication button (if enabled and available)
- Error messages for wrong password
- Zero telemetry notice displayed
- Secure password verification against stored hash

**Security**: Uses Argon2id verification, optional biometric with device credentials fallback.

---

## 3. Credential List
**Screen**: `CredentialListScreen`

Main screen showing all stored credentials:
- Search bar for filtering credentials
- Category filter chips (All, Login, Payment, Identity, Note, Other)
- Scrollable list of credentials with:
  - Category icon
  - Title
  - Username (if present)
  - Website (if present)
  - Chevron for detail view
- Empty state with friendly message
- Floating action button to add new credential
- Settings button in top bar

**Features**:
- Real-time search across title and website fields
- Category-based filtering
- Smooth animations and Material 3 design
- Card-based layout for easy reading

---

## 4. Add/Edit Credential
**Screen**: `AddEditCredentialScreen`

Form for creating or editing credentials:

### Fields:
- **Title*** (Required): Name of the credential
- **Username/Email**: Login identifier
- **Password**: 
  - Visibility toggle button
  - Password generator button (lightning icon)
- **Website**: URL or identifier
- **Category**: Dropdown selector (Login/Payment/Identity/Note/Other)
- **Notes**: Multi-line text field for additional information

### Actions:
- Save button (validates required fields)
- Back navigation
- Real-time validation
- Toast notification on success

**Security**: Username, password, and notes are encrypted before storage.

---

## 5. Password Generator
**Screen**: `PasswordGeneratorScreen`

Secure password generation tool:

### Display:
- Generated password in large, monospace font
- Copy to clipboard button
- Regenerate button

### Configuration:
- **Length Slider**: 8-32 characters
- **Character Types** (checkboxes):
  - Uppercase letters (A-Z)
  - Lowercase letters (a-z)
  - Numbers (0-9)
  - Symbols (!@#$...)

### Actions:
- Generate new password
- Copy to clipboard with toast notification
- Use password button (when opened from credential form)

**Security**: Uses `SecureRandom` for cryptographically secure generation.

---

## Security Features

### Encryption Layers

1. **Database Encryption**:
   - SQLCipher with AES-256
   - Encrypted at rest
   - Secure key derivation

2. **Field-Level Encryption**:
   - Username: Encrypted
   - Password: Encrypted
   - Notes: Encrypted
   - Title & Website: Plaintext (for search)

3. **Android Keystore**:
   - Hardware-backed keys
   - AES-256-GCM encryption
   - Secure key generation
   - IV management
   - Authentication tags

4. **Master Password**:
   - Argon2id hashing
   - Memory-hard algorithm
   - Salt per hash
   - Secure verification

5. **Biometric Authentication**:
   - Fingerprint support
   - Face authentication
   - Device credential fallback
   - Secure prompt management

---

## User Flows

### First Time User
1. Open app
2. Create master password (with strength feedback)
3. Arrive at empty credential list
4. Tap + to add first credential
5. Fill in details (optionally generate password)
6. Save credential
7. View credential in list

### Returning User
1. Open app
2. Enter master password (or use biometric)
3. View credential list
4. Search or filter by category
5. Tap credential to view/edit
6. Copy username/password as needed

### Adding Credential
1. Tap + button
2. Enter title (required)
3. Enter username/email
4. Generate or enter password
5. Optionally add website and notes
6. Select category
7. Save

### Generating Password
1. From credential form, tap generator icon
2. Adjust length slider
3. Select character types
4. Tap regenerate until satisfied
5. Tap "Use This Password"
6. Returns to form with password filled

---

## Category Icons

- **Login**: Person icon
- **Payment**: Credit card icon
- **Identity**: Badge icon
- **Note**: Note icon
- **Other**: Folder icon

---

## UI/UX Features

### Material 3 Design
- Modern color scheme
- Dynamic theming
- Smooth transitions
- Elevated cards
- Floating action buttons
- Material icons

### Dark Theme Support
- System theme detection
- Appropriate color schemes
- Status bar color matching

### Accessibility
- Large touch targets
- Clear labels
- Screen reader support
- High contrast ratios

### Responsive Design
- Adapts to different screen sizes
- Portrait orientation optimized
- Material Design guidelines

---

## Privacy Features

### Zero Telemetry
- No analytics
- No crash reporting
- No network calls
- No cloud sync
- All data local

### Data Security
- Encrypted storage
- Secure memory handling
- Protected against:
  - Unauthorized access
  - Data leakage
  - Man-in-the-middle
  - Device compromise (with encryption)

### No Recovery Mechanism
- By design for security
- No password reset
- No backup key
- User responsibility emphasized

---

## Error Handling

### User-Friendly Messages
- "Wrong password" for auth failures
- "Title is required" for validation
- "Select at least one character type" for generator
- Toast notifications for success actions

### Graceful Degradation
- Biometric unavailable: Falls back to password
- Empty states with helpful messages
- Form validation with clear feedback

---

## Performance

### Optimizations
- Lazy loading with Compose
- Flow-based reactive updates
- Efficient database queries
- Background encryption/decryption
- Coroutine-based async operations

### Memory Management
- Room database caching
- ViewModel lifecycle awareness
- Compose recomposition optimization
- Proper resource cleanup

---

## Future Enhancement Ideas

1. **Auto-fill Service**: Integration with Android Auto-fill Framework
2. **Password Health**: Weak/reused password detection
3. **Breach Checking**: Check against known breaches (offline)
4. **Secure Export**: Encrypted backup file
5. **Password History**: Track password changes
6. **Two-Factor Auth**: TOTP generator integration
7. **Secure Sharing**: Share credentials securely
8. **Rich Notes**: Markdown support
9. **Attachments**: Encrypted file storage
10. **Wear OS**: Companion app for wearables
11. **Multi-Language**: Internationalization
12. **Fingerprint Sensors**: Additional biometric types

---

## Technical Specifications

- **Minimum Android Version**: 8.0 (API 26)
- **Target Android Version**: 14 (API 34)
- **Architecture**: MVVM + Clean Architecture
- **UI Framework**: Jetpack Compose
- **Language**: Kotlin 100%
- **Build System**: Gradle with Kotlin DSL
- **Dependency Injection**: Hilt
- **Database**: Room + SQLCipher
- **Encryption**: Android Keystore + AES-256-GCM
- **Password Hashing**: Argon2id

---

## App Permissions

### Required:
- `USE_BIOMETRIC`: For fingerprint/face authentication
- `USE_FINGERPRINT`: Biometric authentication support

### Not Required:
- No internet permission
- No storage permission (scoped storage used)
- No location permission
- No camera/microphone permission

---

This completes the feature documentation for TrustVault Android MVP.
