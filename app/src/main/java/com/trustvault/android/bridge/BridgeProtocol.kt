package com.trustvault.android.bridge

/**
 * Local bridge protocol for desktop browser extension integration.
 *
 * **Protocol Overview**:
 * - Lightweight HTTP/WebSocket service on localhost:7654
 * - KeePassXC-Browser-inspired message format
 * - Mutual device pairing via shared secret
 * - Secure credential queries with URL filtering
 *
 * **Message Flow**:
 * 1. Client connects and sends Handshake
 * 2. Server responds with HandshakeResponse (includes protocol version)
 * 3. Client sends TestAssociate (pairing request with shared secret)
 * 4. Server validates and responds with AssociateResponse
 * 5. Client sends GetLogins (query for credentials matching URL)
 * 6. Server filters credentials and responds with LoginResponse
 *
 * **Security Model**:
 * - Shared secret required for pairing (QR code or manual entry)
 * - Pairing persists per device/browser combination
 * - All messages encrypted via HMAC validation
 * - Credentials never sent to unpaired clients
 * - LocalHost only (no remote access)
 */
object BridgeProtocol {
    const val VERSION = "1.0"
    const val PROTOCOL = "TrustVault-Bridge"
    const val PORT = 7654
    const val LOCALHOST = "127.0.0.1"
}

/**
 * Message type identifier for bridge protocol.
 */
enum class BridgeMessageType(val id: String) {
    HANDSHAKE("Handshake"),
    HANDSHAKE_RESPONSE("HandshakeResponse"),
    TEST_ASSOCIATE("TestAssociate"),
    ASSOCIATE_RESPONSE("AssociateResponse"),
    GET_LOGINS("GetLogins"),
    LOGIN_RESPONSE("LoginResponse"),
    LOCK("Lock"),
    DISCONNECT("Disconnect"),
    ERROR("Error");

    companion object {
        fun fromString(value: String): BridgeMessageType? {
            return values().find { it.id == value }
        }
    }
}

/**
 * Base message for all bridge protocol communications.
 */
sealed class BridgeMessage {
    abstract val messageType: BridgeMessageType
    abstract val requestId: String
}

/**
 * Initial handshake message from client.
 *
 * **Purpose**: Establish connection and verify protocol compatibility
 * **Fields**:
 * - clientName: Browser extension identifier (e.g., "TrustVault-Browser-Chrome")
 * - clientVersion: Browser extension version
 * - protocol: Protocol identifier ("TrustVault-Bridge")
 * - protocolVersion: Protocol version for compatibility
 */
data class Handshake(
    override val requestId: String,
    val clientName: String,
    val clientVersion: String,
    val protocol: String = BridgeProtocol.PROTOCOL,
    val protocolVersion: String = BridgeProtocol.VERSION
) : BridgeMessage() {
    override val messageType = BridgeMessageType.HANDSHAKE
}

/**
 * Server response to handshake.
 *
 * **Purpose**: Confirm protocol compatibility and provide server info
 * **Fields**:
 * - appName: "TrustVault"
 * - appVersion: Application version
 * - protocol: Confirmed protocol identifier
 * - protocolVersion: Confirmed protocol version
 * - serverIdHash: Hash of server identifier (for device tracking)
 */
data class HandshakeResponse(
    override val requestId: String,
    val appName: String = "TrustVault",
    val appVersion: String = "1.0.0",
    val protocol: String = BridgeProtocol.PROTOCOL,
    val protocolVersion: String = BridgeProtocol.VERSION,
    val serverIdHash: String = "" // SHA-256 hash for device identification
) : BridgeMessage() {
    override val messageType = BridgeMessageType.HANDSHAKE_RESPONSE
}

/**
 * Pairing request from client (TestAssociate in KeePassXC protocol).
 *
 * **Purpose**: Request device pairing with shared secret validation
 * **Fields**:
 * - key: Client public key (base64 encoded)
 * - keyHash: HMAC-SHA256 of key with shared secret (proves secret knowledge)
 * - deviceName: Human-readable device name (e.g., "Chrome on MacBook")
 *
 * **Security**:
 * - keyHash proves client knows the shared secret
 * - Does NOT transmit secret in plain text
 * - Server validates HMAC before accepting pairing
 */
data class TestAssociate(
    override val requestId: String,
    val key: String, // Client's public key (base64)
    val keyHash: String, // HMAC-SHA256(key, sharedSecret)
    val deviceName: String = "Unknown Device"
) : BridgeMessage() {
    override val messageType = BridgeMessageType.TEST_ASSOCIATE
}

/**
 * Server response to pairing request.
 *
 * **Purpose**: Confirm successful pairing
 * **Fields**:
 * - id: Unique pairing identifier (UUID)
 * - hash: HMAC-SHA256 of response data (proves mutual knowledge of secret)
 * - success: Whether pairing was successful
 * - errorMessage: Optional error description if pairing failed
 */
data class AssociateResponse(
    override val requestId: String,
    val id: String = "", // Pairing ID (UUID)
    val hash: String = "", // HMAC-SHA256 for mutual authentication
    val success: Boolean = false,
    val errorMessage: String = ""
) : BridgeMessage() {
    override val messageType = BridgeMessageType.ASSOCIATE_RESPONSE
}

/**
 * Credential query request from client.
 *
 * **Purpose**: Request credentials matching a URL
 * **Fields**:
 * - url: URL to match credentials against (e.g., "https://github.com")
 * - id: Pairing ID (proves device is paired)
 *
 * **Security**:
 * - Pairing ID required (no credentials sent without pairing)
 * - Server filters credentials by URL using UrlMatcher
 * - Only credentials with matching domain are returned
 */
data class GetLogins(
    override val requestId: String,
    val url: String,
    val id: String = "" // Pairing ID
) : BridgeMessage() {
    override val messageType = BridgeMessageType.GET_LOGINS
}

/**
 * Credential response to query.
 *
 * **Purpose**: Return credentials matching the requested URL
 * **Fields**:
 * - entries: List of matching credentials
 * - hash: HMAC-SHA256 of response (for integrity verification)
 * - count: Number of credentials returned
 *
 * **Security**:
 * - Only sent to paired clients (verified by pairing ID)
 * - Credentials are filtered by URL matching
 * - Response is HMAC-signed for integrity
 */
data class LoginResponse(
    override val requestId: String,
    val entries: List<BridgeCredential> = emptyList(),
    val hash: String = "",
    val count: Int = 0
) : BridgeMessage() {
    override val messageType = BridgeMessageType.LOGIN_RESPONSE
}

/**
 * Credential entry returned in LoginResponse.
 *
 * **Fields**:
 * - name: Credential title (e.g., "GitHub - John Doe")
 * - login: Username or email
 * - password: Password (only sent to paired clients)
 * - totp: One-time password if configured
 *
 * **Security**:
 * - TOTP not auto-filled (requires user interaction)
 * - Passwords encrypted in transit via TLS
 * - Only sent to authenticated, paired clients
 */
data class BridgeCredential(
    val name: String,
    val login: String,
    val password: String,
    val totp: String = ""
)

/**
 * Lock request from client (security feature).
 *
 * **Purpose**: Lock the app (equivalent to logging out)
 * **Fields**:
 * - id: Pairing ID (verify client is paired)
 *
 * **Note**: Server responds with standard response (no special format)
 */
data class Lock(
    override val requestId: String,
    val id: String = ""
) : BridgeMessage() {
    override val messageType = BridgeMessageType.LOCK
}

/**
 * Error message for any protocol error.
 *
 * **Purpose**: Communicate protocol/runtime errors to client
 * **Fields**:
 * - code: Error code (e.g., "UNAUTHORIZED", "INVALID_MESSAGE")
 * - message: Human-readable error description
 */
data class ErrorMessage(
    override val requestId: String,
    val code: String,
    val message: String
) : BridgeMessage() {
    override val messageType = BridgeMessageType.ERROR
}

/**
 * Bridge protocol error codes.
 */
object BridgeErrorCode {
    const val PROTOCOL_ERROR = "PROTOCOL_ERROR"
    const val UNAUTHORIZED = "UNAUTHORIZED"
    const val NOT_PAIRED = "NOT_PAIRED"
    const val INVALID_SHARED_SECRET = "INVALID_SHARED_SECRET"
    const val PAIRING_EXISTS = "PAIRING_EXISTS"
    const val DATABASE_LOCKED = "DATABASE_LOCKED"
    const val URL_NOT_MATCHED = "URL_NOT_MATCHED"
    const val INTERNAL_ERROR = "INTERNAL_ERROR"
}
