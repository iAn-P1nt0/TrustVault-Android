# TrustVault - Privacy-First Android Password Manager

TrustVault is a secure, privacy-focused password manager for Android built with modern Android development best practices.

## 🔐 Security Features

- **Hardware-Backed Encryption**: Uses Android Keystore for hardware-backed key storage
- **Field-Level Encryption**: Sensitive credential data is encrypted at the field level
- **SQLCipher Database**: Database encryption using SQLCipher
- **Argon2 Password Hashing**: Master password hashed using Argon2id algorithm
- **Biometric Authentication**: Fingerprint and face authentication support
- **Zero Telemetry**: Your data never leaves your device - no analytics, no tracking, no cloud sync

## 🎯 Features

- **Master Password Setup**: Secure master password with strength indicator
- **Credential Management**: Full CRUD operations for credentials
- **Password Generator**: Cryptographically secure password generator with customizable options
- **Categories**: Organize credentials by type (Login, Payment, Identity, Note, Other)
- **Search**: Quick search through your credentials
- **Modern UI**: Material 3 design with Jetpack Compose

## 🏗️ Architecture

The app follows clean architecture principles with clear separation of concerns:

- **Presentation Layer**: Jetpack Compose UI with MVVM pattern
- **Domain Layer**: Use cases and domain models
- **Data Layer**: Room database with encrypted storage

### Technology Stack

- **UI**: Jetpack Compose + Material 3
- **Architecture**: MVVM with Hilt dependency injection
- **Database**: Room + SQLCipher
- **Security**: 
  - AndroidX Security Crypto
  - Android Keystore
  - Biometric API
  - Argon2kt for password hashing
- **Concurrency**: Kotlin Coroutines + Flow
- **Navigation**: Jetpack Navigation Compose

## 📁 Project Structure

```
com.trustvault.android/
├── data/
│   ├── local/
│   │   ├── entity/          # Room entities
│   │   ├── dao/             # Data access objects
│   │   └── database/        # Database configuration
│   └── repository/          # Repository implementations
├── domain/
│   ├── model/               # Domain models
│   ├── repository/          # Repository interfaces
│   └── usecase/             # Business logic use cases
├── presentation/
│   ├── ui/
│   │   ├── screens/         # Compose screens
│   │   ├── components/      # Reusable UI components
│   │   └── theme/           # Material theme
│   └── viewmodel/           # ViewModels
├── security/                # Security managers
│   ├── AndroidKeystoreManager.kt
│   ├── BiometricAuthManager.kt
│   ├── PasswordHasher.kt
│   └── FieldEncryptor.kt
├── util/                    # Utility classes
└── di/                      # Hilt modules
```

## 🚀 Getting Started

### Prerequisites

- Android Studio Hedgehog or later
- Android SDK 26 (Android 8.0) or higher
- JDK 17

### Building

1. Clone the repository:
```bash
git clone https://github.com/iAn-Pinto/TrustVault-Android.git
cd TrustVault-Android
```

2. Open the project in Android Studio

3. Build and run the app on your device or emulator

### Running Tests

```bash
./gradlew test
./gradlew connectedAndroidTest
```

## 🔒 Security Best Practices

1. **Master Password**: 
   - Never stored in plain text
   - Hashed using Argon2id with secure parameters
   - Used to derive database encryption keys

2. **Credential Storage**:
   - Database encrypted with SQLCipher
   - Sensitive fields encrypted at field level using Android Keystore
   - Hardware-backed keys when device supports it

3. **Authentication**:
   - Master password verification
   - Optional biometric authentication
   - No backup/recovery mechanism to prevent security vulnerabilities

## ⚠️ Important Security Notes

- **Master Password**: If you forget your master password, there is no way to recover your data. Make sure to remember it or store it securely.
- **Device Security**: TrustVault's security depends on your device's security. Use a secure lock screen and keep your device updated.
- **Backups**: This MVP version does not include backup/export functionality. Future versions may include encrypted export.

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## 📧 Contact

Created by iAn P1nt0

## 🙏 Acknowledgments

- Built with modern Android development best practices
- Inspired by open-source password managers
- Security-first design principles

---

**Note**: This is an MVP (Minimum Viable Product) version. Future enhancements may include:
- Encrypted backup/export
- Password breach checking
- Secure notes with rich text
- Attachment support
- Auto-fill integration
- Wear OS support