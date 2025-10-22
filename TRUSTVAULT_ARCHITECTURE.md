# TrustVault Android Application: Architecture and Implementation Details

This document provides a comprehensive overview of the TrustVault Android application's source code, detailing its architecture and the implementation of its various modules. It is intended for novice Android programmers to understand the intricacies of the codebase.

## Overall Architecture

The TrustVault application follows a modern, multi-layered architecture inspired by Clean Architecture principles. This approach separates concerns, improves testability, and makes the codebase easier to maintain and scale. The core layers are:

*   **Presentation Layer:** Handles all UI-related logic, including Activities, Fragments (or Jetpack Compose screens), and ViewModels. It is responsible for displaying data to the user and handling user interactions.
*   **Domain Layer:** Contains the application's business logic. This layer is independent of the Android framework and consists of use cases, domain models, and repository interfaces.
*   **Data Layer:** Responsible for providing data to the application, whether from a local database, network, or other sources. It includes repository implementations, data sources, and data access objects (DAOs).

### Dependency Injection

The application utilizes Hilt for dependency injection, which simplifies the process of providing dependencies to different parts of the application. The `di` module contains Hilt modules that define how to provide instances of various classes, such as the database, repositories, and use cases.

### Security Philosophy

TrustVault is built with a strong emphasis on security. The application follows a zero-knowledge security model, meaning that sensitive data is encrypted and decrypted locally on the user's device, and the application's developers have no access to the user's master password or unencrypted data.

## Module-wise Implementation Details

### Data Layer (`data` module)

The `data` layer is responsible for all data-related operations. It abstracts the data sources from the rest of the application.

*   **`local`:** This sub-package contains the Room database implementation, including:
    *   **`dao`:** Data Access Objects for database operations (e.g., `CredentialDao`).
    *   **`database`:** The main database class (`TrustVaultDatabase`).
    *   **`entity`:** The data entities that represent the database tables (e.g., `CredentialEntity`).
*   **`repository`:** This package contains the implementation of the repository interfaces defined in the `domain` layer (e.g., `CredentialRepositoryImpl`). It is the single source of truth for the application's data.
*   **`backup`:** Handles the creation, encryption, and validation of backups.
*   **`importexport`:** Manages the import and export of credentials from/to various formats like CSV and KeePass.

### Domain Layer (`domain` module)

The `domain` layer contains the core business logic of the application. It is independent of the Android framework.

*   **`model`:** Defines the domain models (e.g., `Credential`) that represent the core data structures of the application.
*   **`repository`:** Contains the repository interfaces (e.g., `CredentialRepository`) that define the contract for data operations. The `data` layer provides the implementation for these interfaces.
*   **`usecase`:** Contains the use cases that encapsulate specific business logic (e.g., `SaveCredentialUseCase`, `GetAllCredentialsUseCase`). These use cases are executed by the `presentation` layer.
*   **`preferences`:** Defines interfaces for managing user preferences.

### Presentation Layer (`presentation` module)

The `presentation` layer is responsible for the UI of the application. It uses Jetpack Compose for building the UI.

*   **`ui`:** Contains the Composable functions, screens, and UI components.
    *   **`screens`:** Each screen of the application is represented by a Composable function (e.g., `CredentialListScreen`, `SettingsScreen`).
    *   **`components`:** Reusable UI components are defined here.
    *   **`theme`:** The application's theme, colors, and typography are defined in this package.
*   **`viewmodel`:** Contains the ViewModels for each screen. ViewModels are responsible for preparing and managing the data for the UI and handling user interactions.
*   **`MainActivity.kt`:** The main entry point of the application.
*   **`Navigation.kt`:** Defines the navigation graph for the application using Jetpack Navigation.

### Security Module (`security` module)

This is a critical module that contains all the security-related logic.

*   **`CryptoManager.kt`:** A core class that handles encryption and decryption of data.
*   **`DatabaseKeyManager.kt`:** Manages the encryption key for the database.
*   **`BiometricAuthManager.kt`:** Handles biometric authentication (fingerprint, face).
*   **`PasswordHasher.kt`:** A utility for securely hashing passwords.
*   **`RootDetection.kt`:** A utility to detect if the device is rooted.
*   **`zeroknowledge`:** This sub-package contains the implementation of the zero-knowledge proof system.
*   **`ocr`:** Contains the logic for the OCR (Optical Character Recognition) feature, including processing and parsing of credentials.

### Other Modules

*   **`accessibility`:** Contains the implementation of the accessibility service, which is used for autofill on older Android versions.
*   **`autofill`:** Implements the Android Autofill framework for seamless credential filling.
*   **`bridge`:** Implements a secure bridge protocol for syncing data between devices.
*   **`compliance`:** Contains tools for auditing, logging, and compliance with privacy regulations.
*   **`credentialmanager`:** Integrates with the Android Credential Manager for a unified sign-in experience.
*   **`di`:** The dependency injection module.
*   **`logging`:** Contains a secure logging utility.
*   **`util`:** A collection of utility classes and functions.
