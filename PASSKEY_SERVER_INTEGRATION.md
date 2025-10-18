# Passkey Server Integration Guide for TrustVault

**Purpose**: Step-by-step guide for implementing server-side WebAuthn verification

**Status**: Ready for Implementation | **Last Updated**: 2025-10-18

---

## Table of Contents

1. [Quick Start](#quick-start)
2. [API Endpoints](#api-endpoints)
3. [JSON Request/Response Formats](#json-requestresponse-formats)
4. [Challenge Management](#challenge-management)
5. [Registration Flow](#registration-flow)
6. [Authentication Flow](#authentication-flow)
7. [Storage Requirements](#storage-requirements)
8. [Error Handling](#error-handling)
9. [Security Checklist](#security-checklist)
10. [Example Implementations](#example-implementations)

---

## Quick Start

### Minimum Requirements

Your backend needs to:

1. **Generate secure challenges** (32-64 random bytes, base64url encoded)
2. **Store challenges temporarily** (with 10-minute expiration)
3. **Verify attestation responses** using FIDO2 library
4. **Extract and store public keys**
5. **Verify assertion signatures** using stored public keys
6. **Track and validate signature counters**

### Library Selection

| Language | Library | Package |
|----------|---------|---------|
| Java | Yubico | `com.yubico:webauthn-server-core:2.4.0` |
| TypeScript/Node.js | SimpleWebAuthn | `@simplewebauthn/server:9.0.0` |
| Python | py_webauthn | `py_webauthn==1.11.1` |
| Go | webauthn | `github.com/duo-labs/webauthn` |
| .NET | WebAuthn.Net | `WebAuthn.Net` |

---

## API Endpoints

### Registration Endpoints

#### 1. Begin Registration
```
POST /api/v1/passkey/register/begin
```

**Request**:
```json
{
  "email": "user@example.com",
  "displayName": "John Doe",
  "username": "john.doe"
}
```

**Response** (200 OK):
```json
{
  "challenge": "Y2hhbGxlbmdlYjMyYWZkYjYyNzg0YzI0NWRkZjU2NDc4YzI4OTQzZg",
  "rp": {
    "id": "example.com",
    "name": "Example Service"
  },
  "user": {
    "id": "dXNlcjEyMw",
    "name": "user@example.com",
    "displayName": "John Doe"
  },
  "pubKeyCredParams": [
    { "type": "public-key", "alg": -7 },
    { "type": "public-key", "alg": -257 }
  ],
  "authenticatorSelection": {
    "residentKey": "discouraged",
    "userVerification": "required"
  },
  "attestation": "direct"
}
```

#### 2. Finish Registration (Verify & Store)
```
POST /api/v1/passkey/register/finish
```

**Request**:
```json
{
  "email": "user@example.com",
  "id": "credential_id_base64url",
  "attestationObject": "o2Nmbdu...",
  "clientDataJson": "eyJ0eXBlIjoid2ViYXV0aG4uY3JlYXRlIiw..."
}
```

**Response** (200 OK):
```json
{
  "success": true,
  "credentialId": "credential_id_base64url",
  "message": "Passkey registered successfully",
  "recoveryCodes": [
    "XXXX-XXXX-XXXX-XXXX",
    "YYYY-YYYY-YYYY-YYYY"
  ]
}
```

**Error Response** (400 Bad Request):
```json
{
  "success": false,
  "error": "attestation_verification_failed",
  "message": "Attestation object signature invalid",
  "code": "ATTESTATION_INVALID"
}
```

### Authentication Endpoints

#### 1. Begin Authentication
```
POST /api/v1/passkey/authenticate/begin
```

**Request**:
```json
{
  "username": "user@example.com"
}
```

**Response** (200 OK):
```json
{
  "challenge": "Y2hhbGxlbmdlYWZkYjYyNzg0YzI0NWRkZjU2NDc4YzI4OTQzZmI",
  "rpId": "example.com",
  "allowCredentials": [
    {
      "id": "credential_id_1_base64url",
      "type": "public-key",
      "transports": ["internal"]
    },
    {
      "id": "credential_id_2_base64url",
      "type": "public-key",
      "transports": ["internal"]
    }
  ],
  "userVerification": "required",
  "timeout": 60000
}
```

#### 2. Finish Authentication (Verify & Issue Token)
```
POST /api/v1/passkey/authenticate/finish
```

**Request**:
```json
{
  "username": "user@example.com",
  "id": "credential_id_base64url",
  "authenticatorData": "SZYN5OtPonzQZc...",
  "clientDataJson": "eyJ0eXBlIjoid2ViYXV0aG4uZ2V0Ii...",
  "signature": "MEYCIQDa2DV3...",
  "userHandle": "dXNlcjEyMw"
}
```

**Response** (200 OK):
```json
{
  "success": true,
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expiresIn": 3600,
  "refreshToken": "refresh_token_here"
}
```

**Error Response** (401 Unauthorized):
```json
{
  "success": false,
  "error": "assertion_verification_failed",
  "message": "Signature verification failed",
  "code": "ASSERTION_INVALID"
}
```

---

## JSON Request/Response Formats

### Client Data JSON Format

The client sends this JSON base64url encoded:

```json
{
  "type": "webauthn.create",
  "challenge": "Y2hhbGxlbmdlYjMyYWZkYjYyNzg0YzI0NWRkZjU2NDc4YzI4OTQzZg",
  "origin": "https://example.com",
  "crossOrigin": false
}
```

**For Authentication**:
```json
{
  "type": "webauthn.get",
  "challenge": "Y2hhbGxlbmdlYWZkYjYyNzg0YzI0NWRkZjU2NDc4YzI4OTQzZmI",
  "origin": "https://example.com",
  "crossOrigin": false
}
```

### Attestation Object Format

The attestation object is CBOR-encoded and contains:

```
{
  "fmt": "direct",
  "attStmt": { /* attestation statement */ },
  "authData": {
    "rpIdHash": <sha256 hash of RP ID>,
    "flags": 0x45,  // Bit 0: User Present, Bit 2: User Verified, Bit 6: Attested Credential Data Included
    "signCount": 0,
    "attestedCredentialData": {
      "aaguid": <AAGUID>,
      "credentialId": <credential ID>,
      "credentialPublicKey": {
        "1": <key type>,
        "3": <algorithm>,
        "-1": <curve ID (for ECDSA)>,
        "-2": <x coordinate>,
        "-3": <y coordinate>
      }
    }
  }
}
```

**Server extracts**:
- `credentialId`: Use for future authentication
- `credentialPublicKey`: Store for signature verification
- `signCount`: Initialize counter tracking
- `flags`: Verify user verification bit is set

---

## Challenge Management

### Challenge Generation

**Requirements**:
- 32-64 cryptographically random bytes
- Base64url encoded (no "=" padding)
- Expires after 10 minutes
- Single-use only

```java
// Java example
import java.security.SecureRandom;
import java.util.Base64;

public String generateChallenge() {
    byte[] challengeBytes = new byte[32];
    new SecureRandom().nextBytes(challengeBytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(challengeBytes);
}
```

```typescript
// TypeScript example
import crypto from 'crypto';

export function generateChallenge(): string {
  const challengeBytes = crypto.randomBytes(32);
  return challengeBytes.toString('base64url');
}
```

### Challenge Storage

Store challenges with metadata:

```sql
CREATE TABLE passkey_challenges (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL,
  challenge TEXT NOT NULL UNIQUE,
  challenge_type ENUM('registration', 'authentication'),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expires_at TIMESTAMP NOT NULL,
  used BOOLEAN DEFAULT FALSE,

  FOREIGN KEY (user_id) REFERENCES users(id)
);

-- Index for cleanup
CREATE INDEX idx_passkey_challenges_expires ON passkey_challenges(expires_at);
```

### Challenge Verification

```java
public boolean verifyChallengeAndConsume(String username, String challenge, String type) {
    // 1. Verify challenge exists
    Challenge stored = database.findChallenge(username, challenge, type);
    if (stored == null) {
        throw new SecurityException("Challenge not found");
    }

    // 2. Verify not expired
    if (stored.expiresAt.isBefore(Instant.now())) {
        database.deleteChallenge(stored.id);
        throw new SecurityException("Challenge expired");
    }

    // 3. Verify not already used
    if (stored.used) {
        throw new SecurityException("Challenge already used (replay attack?)");
    }

    // 4. Mark as used
    database.consumeChallenge(stored.id);

    return true;
}
```

### Cleanup Job

Run periodically to remove expired challenges:

```java
@Scheduled(fixedDelay = 300000) // Every 5 minutes
public void cleanupExpiredChallenges() {
    database.deleteExpiredChallenges();
}
```

---

## Registration Flow

### Server-Side Registration Process

```
1. Client calls POST /api/passkey/register/begin
   ├─ Request: { email, displayName, username }
   └─ Response: Attestation options with challenge

2. Server generates challenge:
   ├─ 32-64 random bytes
   ├─ Base64url encode
   └─ Store in database (10-min expiry)

3. Client receives options:
   ├─ Parses JSON
   ├─ Device generates key pair
   ├─ User completes biometric/PIN verification
   └─ Device returns attestation object

4. Client calls POST /api/passkey/register/finish
   ├─ Request: attestationObject, clientDataJson
   └─ Response: Success or error

5. Server verifies attestation:
   ├─ Verify challenge exists and not expired
   ├─ Consume challenge (mark as used)
   ├─ Parse clientDataJson
   ├─ Verify origin matches
   ├─ Verify RP ID matches
   ├─ Verify challenge matches
   ├─ Parse attestationObject (CBOR)
   ├─ Verify attestation signature (using FIDO2 library)
   ├─ Extract public key
   └─ Verify flags (user present + user verified)

6. Server stores credential:
   ├─ Generate credentialId from attestation
   ├─ Store public key
   ├─ Initialize signCount to 0
   ├─ Store RP information
   └─ Generate recovery codes

7. Return success with recovery codes
```

### Code Implementation

```java
@PostMapping("/api/v1/passkey/register/begin")
public ResponseEntity<?> beginRegistration(
    @RequestBody RegistrationBeginRequest req,
    HttpSession session
) {
    // Validate input
    if (req.getEmail() == null || req.getEmail().isEmpty()) {
        return ResponseEntity.badRequest().body(
            Map.of("error", "email_required")
        );
    }

    // Check user exists or create
    User user = userService.findOrCreateByEmail(req.getEmail());

    // Generate challenge (32 bytes)
    String challenge = generateChallenge();
    byte[] userId = user.getId().getBytes(StandardCharsets.UTF_8);

    // Create registration options
    PublicKeyCredentialCreationOptions options =
        relyingParty.startRegistration(
            StartRegistrationOptions.builder()
                .user(UserIdentity.builder()
                    .id(userId)
                    .name(req.getEmail())
                    .displayName(req.getDisplayName())
                    .build())
                .build()
        );

    // Store challenge in session (or database with session ID)
    session.setAttribute("passkey_challenge", challenge);
    session.setAttribute("passkey_challenge_type", "registration");
    session.setMaxInactiveInterval(600); // 10 minutes

    // Log for security audit
    auditLog.info("Passkey registration started for user: {}", user.getId());

    return ResponseEntity.ok(Map.of(
        "challenge", options.getChallenge().getBase64Url(),
        "rp", Map.of(
            "id", "example.com",
            "name", "Example Service"
        ),
        "user", Map.of(
            "id", Base64.getUrlEncoder().withoutPadding()
                .encodeToString(userId),
            "name", user.getEmail(),
            "displayName", user.getDisplayName()
        ),
        "pubKeyCredParams", List.of(
            Map.of("type", "public-key", "alg", -7),   // ES256
            Map.of("type", "public-key", "alg", -257)  // RS256
        ),
        "authenticatorSelection", Map.of(
            "residentKey", "discouraged",
            "userVerification", "required"
        ),
        "attestation", "direct"
    ));
}

@PostMapping("/api/v1/passkey/register/finish")
public ResponseEntity<?> finishRegistration(
    @RequestBody RegistrationFinishRequest req,
    HttpSession session
) {
    try {
        // Retrieve and verify challenge
        String storedChallenge = (String) session.getAttribute("passkey_challenge");
        String challengeType = (String) session.getAttribute("passkey_challenge_type");

        if (storedChallenge == null || !"registration".equals(challengeType)) {
            return ResponseEntity.status(400).body(
                Map.of("error", "invalid_session")
            );
        }

        // Clear challenge from session (single use)
        session.removeAttribute("passkey_challenge");
        session.removeAttribute("passkey_challenge_type");

        // Verify attestation
        RegistrationResult result = relyingParty.finishRegistration(
            FinishRegistrationOptions.builder()
                .request(/* reconstruct from challenge */)
                .response(AuthenticatorAttestationResponse.builder()
                    .id(Bytes.fromBase64Url(req.getId()))
                    .attestationObject(Bytes.fromBase64Url(req.getAttestationObject()))
                    .clientDataJSON(Bytes.fromBase64Url(req.getClientDataJson()))
                    .build())
                .build()
        );

        // Extract public key
        byte[] publicKey = result.getKeyPair().getPublic().getEncoded();
        long signCount = result.getSignatureCounter();

        // Store credential
        PasskeyCredential credential = new PasskeyCredential();
        credential.setUserId(getUserIdFromSession(session));
        credential.setCredentialId(req.getId());
        credential.setPublicKey(publicKey);
        credential.setSignatureCounter(signCount);
        credential.setCreatedAt(Instant.now());

        passkeyService.storeCredential(credential);

        // Generate recovery codes
        List<String> recoveryCodes = generateRecoveryCodes(6);
        recoveryCodeService.storeRecoveryCodes(
            getUserIdFromSession(session),
            recoveryCodes
        );

        auditLog.info("Passkey registered successfully for user: {}",
            getUserIdFromSession(session));

        return ResponseEntity.ok(Map.of(
            "success", true,
            "credentialId", req.getId(),
            "message", "Passkey registered successfully",
            "recoveryCodes", recoveryCodes
        ));

    } catch (RegistrationFailedException e) {
        auditLog.warn("Passkey registration failed: {}", e.getMessage());
        return ResponseEntity.status(400).body(Map.of(
            "success", false,
            "error", "attestation_verification_failed",
            "message", e.getMessage()
        ));
    }
}
```

---

## Authentication Flow

### Server-Side Authentication Process

```
1. Client calls POST /api/passkey/authenticate/begin
   ├─ Request: { username }
   └─ Response: Assertion options with challenge

2. Server generates challenge:
   ├─ 32-64 random bytes
   ├─ Base64url encode
   ├─ Retrieve user's credential IDs
   └─ Store challenge in database (10-min expiry)

3. Client receives options:
   ├─ Parses JSON
   ├─ Device selects credential
   ├─ User completes biometric/PIN verification
   ├─ Device signs challenge with private key
   └─ Device returns assertion object

4. Client calls POST /api/passkey/authenticate/finish
   ├─ Request: authenticatorData, clientDataJson, signature
   └─ Response: JWT token or error

5. Server verifies assertion:
   ├─ Retrieve stored public key
   ├─ Verify challenge exists and not expired
   ├─ Consume challenge
   ├─ Parse clientDataJson
   ├─ Verify origin matches
   ├─ Verify RP ID matches
   ├─ Verify challenge matches
   ├─ Verify signature using public key
   ├─ Verify counter incremented
   └─ Update counter in database

6. Return JWT token for authentication
```

### Code Implementation

```java
@PostMapping("/api/v1/passkey/authenticate/begin")
public ResponseEntity<?> beginAuthentication(
    @RequestBody AuthBeginRequest req,
    HttpSession session
) {
    // Validate input
    if (req.getUsername() == null || req.getUsername().isEmpty()) {
        return ResponseEntity.badRequest().body(
            Map.of("error", "username_required")
        );
    }

    // Find user
    User user = userService.findByEmail(req.getUsername());
    if (user == null) {
        // Don't reveal if user exists (security)
        return ResponseEntity.status(404).body(
            Map.of("error", "user_not_found")
        );
    }

    // Generate challenge
    String challenge = generateChallenge();

    // Get user's credential IDs
    List<PasskeyCredential> credentials = passkeyService.getCredentialsByUser(user.getId());
    List<String> credentialIds = credentials.stream()
        .map(PasskeyCredential::getCredentialId)
        .collect(Collectors.toList());

    // Store challenge
    session.setAttribute("passkey_challenge", challenge);
    session.setAttribute("passkey_challenge_type", "authentication");
    session.setAttribute("passkey_user_id", user.getId());
    session.setMaxInactiveInterval(600); // 10 minutes

    auditLog.info("Passkey authentication started for user: {}", user.getId());

    return ResponseEntity.ok(Map.of(
        "challenge", challenge,
        "rpId", "example.com",
        "allowCredentials", credentialIds.stream()
            .map(id -> Map.of(
                "id", id,
                "type", "public-key",
                "transports", List.of("internal")
            ))
            .collect(Collectors.toList()),
        "userVerification", "required",
        "timeout", 60000
    ));
}

@PostMapping("/api/v1/passkey/authenticate/finish")
public ResponseEntity<?> finishAuthentication(
    @RequestBody AuthFinishRequest req,
    HttpSession session
) {
    try {
        // Retrieve and verify challenge
        String storedChallenge = (String) session.getAttribute("passkey_challenge");
        String challengeType = (String) session.getAttribute("passkey_challenge_type");
        String userId = (String) session.getAttribute("passkey_user_id");

        if (storedChallenge == null || !"authentication".equals(challengeType)) {
            return ResponseEntity.status(400).body(
                Map.of("error", "invalid_session")
            );
        }

        // Clear from session
        session.removeAttribute("passkey_challenge");
        session.removeAttribute("passkey_challenge_type");
        session.removeAttribute("passkey_user_id");

        // Retrieve credential
        PasskeyCredential credential = passkeyService.getCredentialById(req.getId());
        if (credential == null) {
            return ResponseEntity.status(404).body(
                Map.of("error", "credential_not_found")
            );
        }

        // Verify assertion
        AssertionResult result = relyingParty.finishAssertion(
            FinishAssertionOptions.builder()
                .request(/* reconstruct from challenge */)
                .response(AuthenticatorAssertionResponse.builder()
                    .id(Bytes.fromBase64Url(req.getId()))
                    .authenticatorData(Bytes.fromBase64Url(req.getAuthenticatorData()))
                    .clientDataJSON(Bytes.fromBase64Url(req.getClientDataJson()))
                    .signature(Bytes.fromBase64Url(req.getSignature()))
                    .build())
                .build()
        );

        // Verify counter (prevent cloning)
        long storedCounter = credential.getSignatureCounter();
        long assertionCounter = result.getSignatureCounter();

        if (assertionCounter <= storedCounter) {
            auditLog.error("Possible credential cloning for user: {}", userId);
            throw new SecurityException("Signature counter not incremented");
        }

        // Update counter
        credential.setSignatureCounter(assertionCounter);
        credential.setLastUsedAt(Instant.now());
        passkeyService.updateCredential(credential);

        // Generate JWT
        String token = jwtService.generateToken(userId);

        auditLog.info("Passkey authentication successful for user: {}", userId);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "token", token,
            "expiresIn", 3600,
            "tokenType", "Bearer"
        ));

    } catch (AssertionFailedException e) {
        auditLog.warn("Passkey authentication failed: {}", e.getMessage());
        return ResponseEntity.status(401).body(Map.of(
            "success", false,
            "error", "assertion_verification_failed",
            "message", e.getMessage()
        ));
    }
}
```

---

## Storage Requirements

### Database Schema

```sql
-- Users table
CREATE TABLE users (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email VARCHAR(255) UNIQUE NOT NULL,
  display_name VARCHAR(255),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Passkey credentials
CREATE TABLE passkey_credentials (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  credential_id TEXT NOT NULL UNIQUE,  -- Base64url encoded
  public_key BYTEA NOT NULL,            -- DER encoded public key
  signature_counter BIGINT NOT NULL DEFAULT 0,
  algorithm INTEGER NOT NULL,           -- -7 (ES256) or -257 (RS256)
  attestation_format VARCHAR(50),       -- "direct", "packed", etc.
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  last_used_at TIMESTAMP,
  device_name VARCHAR(255),             -- User-friendly device name
  is_active BOOLEAN DEFAULT TRUE,

  UNIQUE(user_id, credential_id)
);

-- Passkey challenges (temporary)
CREATE TABLE passkey_challenges (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  challenge TEXT NOT NULL UNIQUE,
  challenge_type ENUM('registration', 'authentication'),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  expires_at TIMESTAMP NOT NULL,
  used BOOLEAN DEFAULT FALSE
);

-- Recovery codes
CREATE TABLE recovery_codes (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  code_hash TEXT NOT NULL UNIQUE,  -- SHA256 hash of code
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  used_at TIMESTAMP,

  UNIQUE(user_id, code_hash)
);

-- Audit log
CREATE TABLE audit_logs (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID REFERENCES users(id),
  action VARCHAR(255) NOT NULL,
  ip_address INET,
  user_agent TEXT,
  status VARCHAR(50),  -- "success" or "failed"
  error_message TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indexes
CREATE INDEX idx_passkey_creds_user ON passkey_credentials(user_id);
CREATE INDEX idx_passkey_creds_cred_id ON passkey_credentials(credential_id);
CREATE INDEX idx_challenges_user ON passkey_challenges(user_id);
CREATE INDEX idx_challenges_expires ON passkey_challenges(expires_at);
CREATE INDEX idx_challenges_challenge ON passkey_challenges(challenge);
CREATE INDEX idx_recovery_codes_user ON recovery_codes(user_id);
CREATE INDEX idx_audit_logs_user ON audit_logs(user_id);
CREATE INDEX idx_audit_logs_created ON audit_logs(created_at);
```

---

## Error Handling

### Error Response Format

```json
{
  "success": false,
  "error": "error_code",
  "message": "Human-readable error message",
  "code": "ERROR_CODE"
}
```

### Common Errors

| Error | HTTP | Cause | Solution |
|-------|------|-------|----------|
| `challenge_not_found` | 400 | Challenge expired or invalid | User restarts registration |
| `attestation_verification_failed` | 400 | Attestation signature invalid | Check device is genuine |
| `assertion_verification_failed` | 401 | Signature verification failed | User retries authentication |
| `counter_not_incremented` | 401 | Possible cloning attack | Reject authentication |
| `credential_not_found` | 404 | Credential deleted or invalid ID | Refresh credential list |
| `user_not_found` | 404 | User doesn't exist | Create account first |
| `invalid_session` | 400 | Session expired or invalid | Start process from beginning |
| `origin_mismatch` | 400 | Origin doesn't match | Verify domain configuration |
| `rp_id_mismatch` | 400 | RP ID invalid | Check server configuration |

### Error Logging

```java
private void logAuthenticationFailure(String userId, String reason) {
    auditLog.warn(
        "Authentication failure - User: {}, Reason: {}, IP: {}",
        userId,
        reason,
        request.getRemoteAddr()
    );
}
```

---

## Security Checklist

- [ ] HTTPS only (no HTTP)
- [ ] Challenge generation uses `SecureRandom`
- [ ] Challenge expires in < 10 minutes
- [ ] Challenge single-use only
- [ ] Origin verified matches configuration
- [ ] RP ID verified matches configuration
- [ ] Attestation verified using FIDO2 library
- [ ] Public key extracted and stored securely
- [ ] User verification flag checked (bit 2)
- [ ] User present flag checked (bit 0)
- [ ] Counter incremented and validated
- [ ] Sessions expire after inactivity
- [ ] CSRF tokens used for state-changing requests
- [ ] Rate limiting on registration/authentication
- [ ] Audit logging all operations
- [ ] Recovery codes stored hashed
- [ ] Database credentials encrypted at rest
- [ ] TLS 1.3+ enforced
- [ ] HSTS headers set
- [ ] CSP headers configured

---

## Example Implementations

See `PASSKEY_WEBAUTHN_IMPLEMENTATION.md` for complete examples including:

- Java Spring Boot implementation
- TypeScript/Node.js Express implementation
- Challenge generation
- Attestation verification
- Assertion verification
- Error handling

---

**Document Version**: 1.0
**Status**: Ready for Implementation
**Last Updated**: 2025-10-18