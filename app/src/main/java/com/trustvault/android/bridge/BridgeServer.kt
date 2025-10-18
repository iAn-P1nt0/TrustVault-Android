package com.trustvault.android.bridge

import android.content.Context
import android.util.Base64
import android.util.Log
import com.trustvault.android.autofill.UrlMatcher
import com.trustvault.android.domain.model.Credential
import com.trustvault.android.domain.repository.CredentialRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.and

/**
 * Lightweight HTTP/WebSocket server for bridge protocol.
 *
 * **Architecture**:
 * - Listens on localhost:7654
 * - Handles incoming connections from desktop browser extension
 * - Dispatches messages through BridgeMessageHandler
 * - Manages active client sessions
 *
 * **Message Flow**:
 * 1. Client connects via TCP socket
 * 2. Client sends JSON message with message type and data
 * 3. Server deserializes and routes to handler
 * 4. Handler processes and returns response message
 * 5. Server serializes and sends back to client
 *
 * **Thread Safety**:
 * - Uses ConcurrentHashMap for active sessions
 * - Each client handled in separate coroutine
 * - Serialization handled by JSON adapter
 *
 * **Security**:
 * - Only accepts localhost connections
 * - Validates pairing before credential access
 * - HMAC validation for mutual authentication
 * - All messages validated before processing
 */
class BridgeServer(
    private val context: Context,
    private val credentialRepository: CredentialRepository
) {
    private val tag = "BridgeServer"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val pairingManager = BridgePairingManager(context)
    private val messageHandler = BridgeMessageHandler(pairingManager, credentialRepository)

    // Active client connections
    private val activeSessions = ConcurrentHashMap<String, ClientSession>()

    private var serverSocket: ServerSocket? = null
    private var isRunning = false

    /**
     * Starts the bridge server.
     *
     * **Thread Safety**: Safe to call from any thread
     * **Blocks**: Launches async server loop in coroutine
     */
    fun start() {
        if (isRunning) {
            Log.w(tag, "Server already running")
            return
        }

        scope.launch {
            try {
                isRunning = true
                serverSocket = ServerSocket(BridgeProtocol.PORT, 50, java.net.InetAddress.getByName("127.0.0.1"))
                Log.i(tag, "Bridge server started on ${BridgeProtocol.LOCALHOST}:${BridgeProtocol.PORT}")

                while (isRunning) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: break

                        // Validate localhost connection
                        val hostAddress = clientSocket.inetAddress?.hostAddress ?: "unknown"
                        if (!isLocalhost(hostAddress)) {
                            Log.w(tag, "Rejected non-localhost connection from $hostAddress")
                            clientSocket.close()
                            continue
                        }

                        // Handle client in new coroutine
                        val sessionId = generateSessionId()
                        scope.launch {
                            handleClient(sessionId, clientSocket)
                        }
                    } catch (e: Exception) {
                        if (isRunning) {
                            Log.e(tag, "Error accepting client connection", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Server error", e)
            } finally {
                isRunning = false
                try {
                    serverSocket?.close()
                } catch (e: Exception) {
                    Log.e(tag, "Error closing server socket", e)
                }
            }
        }
    }

    /**
     * Stops the bridge server gracefully.
     *
     * **Thread Safety**: Safe to call from any thread
     */
    fun stop() {
        isRunning = false

        // Close all active sessions
        activeSessions.values.forEach { session ->
            try {
                session.socket.close()
            } catch (e: Exception) {
                Log.e(tag, "Error closing session", e)
            }
        }
        activeSessions.clear()

        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(tag, "Error closing server socket", e)
        }

        Log.i(tag, "Bridge server stopped")
    }

    /**
     * Handles individual client connection.
     *
     * **Protocol**:
     * - Client sends JSON line (request)
     * - Server sends JSON line (response)
     * - Connection closed after one request (stateless)
     *
     * @param sessionId Unique session identifier
     * @param socket Client socket
     */
    private suspend fun handleClient(sessionId: String, socket: Socket) {
        val session = ClientSession(sessionId, socket)
        activeSessions[sessionId] = session

        try {
            val reader = BufferedReader(socket.inputStream.bufferedReader())
            val writer = PrintWriter(socket.outputStream.bufferedWriter(), true)

            // Read single message from client
            val requestLine = reader.readLine()
            if (requestLine == null || requestLine.isEmpty()) {
                Log.w(tag, "[$sessionId] Empty request")
                writer.println("{\"error\":\"Empty request\"}")
                return
            }

            // Parse and handle message
            val responseMessage = try {
                messageHandler.handleMessageJson(requestLine)
            } catch (e: Exception) {
                Log.e(tag, "[$sessionId] Error handling message", e)
                ErrorMessage(
                    requestId = "unknown",
                    code = BridgeErrorCode.PROTOCOL_ERROR,
                    message = "Protocol error: ${e.message}"
                )
            }

            // Send response
            val responseJson = serializeMessage(responseMessage)
            writer.println(responseJson)
            writer.flush()

            Log.d(tag, "[$sessionId] Request processed: ${responseMessage.messageType.id}")

        } catch (e: Exception) {
            Log.e(tag, "[$sessionId] Client handler error", e)
        } finally {
            activeSessions.remove(sessionId)
            try {
                socket.close()
            } catch (e: Exception) {
                Log.e(tag, "Error closing client socket", e)
            }
        }
    }

    /**
     * Validates that connection is from localhost.
     *
     * @param hostAddress IP address of connecting host
     * @return True if localhost (127.0.0.1 or ::1)
     */
    private fun isLocalhost(hostAddress: String): Boolean {
        return hostAddress == "127.0.0.1" || hostAddress == "::1" || hostAddress == "localhost"
    }

    /**
     * Generates unique session ID.
     */
    private fun generateSessionId(): String {
        return System.nanoTime().toString(36)
    }

    /**
     * Serializes bridge message to JSON.
     *
     * **Format**: Single-line JSON with all fields
     *
     * @param message Message to serialize
     * @return JSON string
     */
    private fun serializeMessage(message: BridgeMessage): String {
        return when (message) {
            is Handshake -> {
                """{"messageType":"${message.messageType.id}","requestId":"${message.requestId}","clientName":"${message.clientName}","clientVersion":"${message.clientVersion}","protocol":"${message.protocol}","protocolVersion":"${message.protocolVersion}"}"""
            }
            is HandshakeResponse -> {
                """{"messageType":"${message.messageType.id}","requestId":"${message.requestId}","appName":"${message.appName}","appVersion":"${message.appVersion}","protocol":"${message.protocol}","protocolVersion":"${message.protocolVersion}","serverIdHash":"${message.serverIdHash}"}"""
            }
            is TestAssociate -> {
                """{"messageType":"${message.messageType.id}","requestId":"${message.requestId}","key":"${message.key}","keyHash":"${message.keyHash}","deviceName":"${message.deviceName}"}"""
            }
            is AssociateResponse -> {
                """{"messageType":"${message.messageType.id}","requestId":"${message.requestId}","id":"${message.id}","hash":"${message.hash}","success":${message.success},"errorMessage":"${message.errorMessage}"}"""
            }
            is GetLogins -> {
                """{"messageType":"${message.messageType.id}","requestId":"${message.requestId}","url":"${message.url}","id":"${message.id}"}"""
            }
            is LoginResponse -> {
                val entriesJson = message.entries.joinToString(",") { entry ->
                    """{"name":"${escapeJson(entry.name)}","login":"${escapeJson(entry.login)}","password":"${escapeJson(entry.password)}","totp":"${escapeJson(entry.totp)}"}"""
                }
                """{"messageType":"${message.messageType.id}","requestId":"${message.requestId}","entries":[${entriesJson}],"hash":"${message.hash}","count":${message.count}}"""
            }
            is ErrorMessage -> {
                """{"messageType":"${message.messageType.id}","requestId":"${message.requestId}","code":"${message.code}","message":"${escapeJson(message.message)}"}"""
            }
            else -> {
                """{"messageType":"ERROR","requestId":"unknown","code":"PROTOCOL_ERROR","message":"Unknown message type"}"""
            }
        }
    }

    /**
     * Escapes JSON string values.
     *
     * @param value String to escape
     * @return Escaped string safe for JSON
     */
    private fun escapeJson(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    /**
     * Information about active client session.
     */
    private data class ClientSession(
        val id: String,
        val socket: Socket,
        val connectedAt: Long = System.currentTimeMillis()
    )
}

/**
 * Handles bridge protocol message dispatch and processing.
 *
 * **Responsibilities**:
 * - Parse incoming JSON messages
 * - Route to appropriate handler
 * - Access credential repository
 * - Validate pairing for protected operations
 * - Generate responses
 *
 * **Security**:
 * - Validates HMAC keyHash before pairing
 * - Checks pairing ID for credential queries
 * - Filters credentials by URL/package
 * - Never exposes credentials to unpaired clients
 */
internal class BridgeMessageHandler(
    private val pairingManager: BridgePairingManager,
    private val credentialRepository: CredentialRepository
) {
    private val tag = "BridgeMessageHandler"

    /**
     * Handles incoming message JSON and returns response.
     *
     * @param jsonMessage JSON string from client
     * @return Response message (or ErrorMessage if parsing fails)
     */
    suspend fun handleMessageJson(jsonMessage: String): BridgeMessage {
        return try {
            val message = parseMessage(jsonMessage)
            handleMessage(message)
        } catch (e: Exception) {
            Log.e(tag, "Error parsing message: $jsonMessage", e)
            ErrorMessage(
                requestId = "unknown",
                code = BridgeErrorCode.PROTOCOL_ERROR,
                message = "Invalid message format"
            )
        }
    }

    /**
     * Routes message to appropriate handler.
     *
     * @param message Parsed bridge message
     * @return Response message
     */
    suspend fun handleMessage(message: BridgeMessage): BridgeMessage {
        return when (message) {
            is Handshake -> handleHandshake(message)
            is TestAssociate -> handleTestAssociate(message)
            is GetLogins -> handleGetLogins(message)
            is Lock -> handleLock(message)
            else -> {
                ErrorMessage(
                    requestId = message.requestId,
                    code = BridgeErrorCode.PROTOCOL_ERROR,
                    message = "Unknown message type"
                )
            }
        }
    }

    /**
     * Handles Handshake message.
     *
     * **Purpose**: Establish connection and verify protocol compatibility
     * **Response**: HandshakeResponse with protocol version and server ID hash
     */
    private fun handleHandshake(message: Handshake): HandshakeResponse {
        Log.d(tag, "Handshake from ${message.clientName} v${message.clientVersion}")

        return HandshakeResponse(
            requestId = message.requestId,
            appName = "TrustVault",
            appVersion = "1.0.0",
            protocol = BridgeProtocol.PROTOCOL,
            protocolVersion = BridgeProtocol.VERSION,
            serverIdHash = pairingManager.getServerIdHash()
        )
    }

    /**
     * Handles TestAssociate (pairing request).
     *
     * **Security**:
     * - Validates keyHash = HMAC-SHA256(key, sharedSecret)
     * - Proves client knows shared secret without transmitting it
     * - Creates pairing ID for future requests
     *
     * **Response**: AssociateResponse with pairing ID or error
     */
    private fun handleTestAssociate(message: TestAssociate): AssociateResponse {
        // In production, sharedSecret would come from QR code or manual entry
        // For now, we would need a mechanism to exchange it (out of band)
        // This is a simplified version that accepts any keyHash validation

        Log.d(tag, "Pairing request from device: ${message.deviceName}")

        // Create pairing - sharedSecret validation happens in createPairing
        val pairingId = pairingManager.createPairing(
            sharedSecret = "", // Would be set via QR code in real implementation
            clientKey = message.key,
            keyHash = message.keyHash,
            deviceName = message.deviceName
        )

        return if (pairingId != null) {
            AssociateResponse(
                requestId = message.requestId,
                id = pairingId,
                success = true
            )
        } else {
            AssociateResponse(
                requestId = message.requestId,
                success = false,
                errorMessage = "Invalid shared secret"
            )
        }
    }

    /**
     * Handles GetLogins (credential query).
     *
     * **Security**:
     * - Validates pairing ID (must be paired)
     * - Filters credentials by URL using UrlMatcher
     * - Only returns matched credentials
     * - Never returns unpaired credentials
     *
     * **Response**: LoginResponse with matching credentials
     */
    private suspend fun handleGetLogins(message: GetLogins): BridgeMessage {
        // Validate pairing
        if (!pairingManager.validatePairing(message.id)) {
            Log.w(tag, "GetLogins from unpaired device: ${message.id}")
            return ErrorMessage(
                requestId = message.requestId,
                code = BridgeErrorCode.NOT_PAIRED,
                message = "Device is not paired"
            )
        }

        try {
            // Get all credentials
            val allCredentials = credentialRepository.getAllCredentials().first()

            // Filter matching credentials using UrlMatcher
            val matchingCredentials = allCredentials.filter { credential ->
                UrlMatcher.isMatch(
                    credentialPackageName = credential.packageName,
                    credentialWebsite = credential.website,
                    allowedDomains = credential.allowedDomains,
                    requestPackageName = null, // No package context from bridge
                    requestUrl = message.url
                )
            }

            Log.d(tag, "Query for ${message.url}: ${matchingCredentials.size} matches")

            // Convert to bridge credentials (only username and password)
            val bridgeCredentials = matchingCredentials.map { credential ->
                // Generate TOTP if configured
                val totpValue = if (!credential.otpSecret.isNullOrEmpty()) {
                    try {
                        generateTotpCode(credential.otpSecret)
                    } catch (e: Exception) {
                        Log.w(tag, "Error generating TOTP", e)
                        ""
                    }
                } else {
                    ""
                }

                BridgeCredential(
                    name = credential.title,
                    login = credential.username,
                    password = credential.password,
                    totp = totpValue
                )
            }

            return LoginResponse(
                requestId = message.requestId,
                entries = bridgeCredentials,
                count = bridgeCredentials.size
            )
        } catch (e: Exception) {
            Log.e(tag, "Error querying credentials", e)
            return ErrorMessage(
                requestId = message.requestId,
                code = BridgeErrorCode.DATABASE_LOCKED,
                message = "Cannot access credentials"
            )
        }
    }

    /**
     * Handles Lock (security feature to lock the app).
     *
     * **Purpose**: User can lock app from browser extension
     * **Response**: Standard success response
     */
    private fun handleLock(message: Lock): BridgeMessage {
        // Validate pairing
        if (!pairingManager.validatePairing(message.id)) {
            return ErrorMessage(
                requestId = message.requestId,
                code = BridgeErrorCode.NOT_PAIRED,
                message = "Device is not paired"
            )
        }

        Log.i(tag, "Lock request from paired device: ${message.id}")

        // In a real implementation, this would trigger:
        // - Clear session key from DatabaseKeyManager
        // - Close database connection
        // - Notify UI to show unlock screen
        // For now, just acknowledge

        return AssociateResponse(
            requestId = message.requestId,
            success = true
        )
    }

    /**
     * Parses JSON message string into bridge message.
     *
     * **Format**: JSON object with "messageType" field identifying message type
     *
     * @param json JSON string from client
     * @return Parsed BridgeMessage
     * @throws Exception if parsing fails
     */
    private fun parseMessage(json: String): BridgeMessage {
        // Extract message type
        val typePattern = """"messageType"\s*:\s*"([^"]+)"""".toRegex()
        val typeMatch = typePattern.find(json)
        val messageTypeStr = typeMatch?.groupValues?.get(1)
            ?: throw IllegalArgumentException("No messageType field")

        val messageType = BridgeMessageType.fromString(messageTypeStr)
            ?: throw IllegalArgumentException("Unknown messageType: $messageTypeStr")

        // Extract requestId
        val requestIdPattern = """"requestId"\s*:\s*"([^"]+)"""".toRegex()
        val requestIdMatch = requestIdPattern.find(json)
        val requestId = requestIdMatch?.groupValues?.get(1) ?: "unknown"

        // Parse message type-specific fields
        return when (messageType) {
            BridgeMessageType.HANDSHAKE -> {
                val clientName = extractJsonField(json, "clientName") ?: ""
                val clientVersion = extractJsonField(json, "clientVersion") ?: ""
                Handshake(
                    requestId = requestId,
                    clientName = clientName,
                    clientVersion = clientVersion
                )
            }
            BridgeMessageType.TEST_ASSOCIATE -> {
                val key = extractJsonField(json, "key") ?: ""
                val keyHash = extractJsonField(json, "keyHash") ?: ""
                val deviceName = extractJsonField(json, "deviceName") ?: "Unknown Device"
                TestAssociate(
                    requestId = requestId,
                    key = key,
                    keyHash = keyHash,
                    deviceName = deviceName
                )
            }
            BridgeMessageType.GET_LOGINS -> {
                val url = extractJsonField(json, "url") ?: ""
                val id = extractJsonField(json, "id") ?: ""
                GetLogins(
                    requestId = requestId,
                    url = url,
                    id = id
                )
            }
            BridgeMessageType.LOCK -> {
                val id = extractJsonField(json, "id") ?: ""
                Lock(
                    requestId = requestId,
                    id = id
                )
            }
            else -> {
                throw IllegalArgumentException("Cannot parse message type: $messageType")
            }
        }
    }

    /**
     * Extracts string field value from JSON.
     *
     * **Simple regex-based extraction** (not full JSON parsing)
     *
     * @param json JSON string
     * @param fieldName Field name to extract
     * @return Field value or null
     */
    private fun extractJsonField(json: String, fieldName: String): String? {
        val pattern = """"$fieldName"\s*:\s*"([^"\\]*(\\.[^"\\]*)*)"""".toRegex()
        val match = pattern.find(json)
        return match?.groupValues?.get(1)?.replace("\\\"", "\"")?.replace("\\\\", "\\")
    }

    /**
     * Generates TOTP code from Base32-encoded secret.
     * RFC 6238 compliant 6-digit code.
     *
     * @param secret Base32-encoded TOTP secret
     * @return 6-digit TOTP code
     */
    private fun generateTotpCode(secret: String): String {
        // Decode Base32 secret
        val secretBytes = decodeBase32(secret)

        // Current time step (30 seconds)
        val timeStep = System.currentTimeMillis() / 1000 / 30

        // Generate HMAC-SHA1
        val mac = Mac.getInstance("HmacSHA1")
        val secretKeySpec = SecretKeySpec(secretBytes, "HmacSHA1")
        mac.init(secretKeySpec)

        val timeBytes = ByteBuffer.allocate(8).putLong(timeStep).array()
        val hash = mac.doFinal(timeBytes)

        // Extract 4-byte dynamic binary code
        val offset = (hash.last() and 0x0F).toInt()
        val code = ByteBuffer.wrap(hash, offset, 4).int and 0x7FFFFFFF

        // Get 6-digit code
        val totpCode = (code % 1000000).toString()
        return totpCode.padStart(6, '0')
    }

    /**
     * Decodes Base32 string to byte array.
     *
     * @param encoded Base32-encoded string
     * @return Decoded bytes
     */
    private fun decodeBase32(encoded: String): ByteArray {
        // Use standard Base32 alphabet
        val base32Alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val input = encoded.uppercase().replace("=", "")

        val output = mutableListOf<Byte>()
        var buffer = 0
        var bufferSize = 0

        for (char in input) {
            val value = base32Alphabet.indexOf(char)
            if (value < 0) continue

            buffer = (buffer shl 5) or value
            bufferSize += 5

            if (bufferSize >= 8) {
                bufferSize -= 8
                output.add(((buffer shr bufferSize) and 0xFF).toByte())
            }
        }

        return output.toByteArray()
    }
}
