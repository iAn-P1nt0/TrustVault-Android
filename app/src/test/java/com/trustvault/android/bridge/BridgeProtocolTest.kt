package com.trustvault.android.bridge

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for BridgeProtocol and message classes.
 *
 * Tests cover:
 * - Protocol constants
 * - Message type enum
 * - Sealed class hierarchy
 * - Message instantiation
 */
class BridgeProtocolTest {

    @Test
    fun testProtocolConstants() {
        assertEquals("Protocol version", "1.0", BridgeProtocol.VERSION)
        assertEquals("Protocol name", "TrustVault-Bridge", BridgeProtocol.PROTOCOL)
        assertEquals("Port", 7654, BridgeProtocol.PORT)
        assertEquals("Localhost", "127.0.0.1", BridgeProtocol.LOCALHOST)
    }

    @Test
    fun testMessageTypeEnumValues() {
        val types = listOf(
            BridgeMessageType.HANDSHAKE,
            BridgeMessageType.HANDSHAKE_RESPONSE,
            BridgeMessageType.TEST_ASSOCIATE,
            BridgeMessageType.ASSOCIATE_RESPONSE,
            BridgeMessageType.GET_LOGINS,
            BridgeMessageType.LOGIN_RESPONSE,
            BridgeMessageType.LOCK,
            BridgeMessageType.DISCONNECT,
            BridgeMessageType.ERROR
        )

        assertEquals("Should have 9 message types", 9, types.size)
    }

    @Test
    fun testMessageTypeFromString() {
        assertEquals("Handshake", BridgeMessageType.HANDSHAKE, BridgeMessageType.fromString("Handshake"))
        assertEquals("HandshakeResponse", BridgeMessageType.HANDSHAKE_RESPONSE, BridgeMessageType.fromString("HandshakeResponse"))
        assertEquals("TestAssociate", BridgeMessageType.TEST_ASSOCIATE, BridgeMessageType.fromString("TestAssociate"))
        assertEquals("AssociateResponse", BridgeMessageType.ASSOCIATE_RESPONSE, BridgeMessageType.fromString("AssociateResponse"))
        assertEquals("GetLogins", BridgeMessageType.GET_LOGINS, BridgeMessageType.fromString("GetLogins"))
        assertEquals("LoginResponse", BridgeMessageType.LOGIN_RESPONSE, BridgeMessageType.fromString("LoginResponse"))
        assertEquals("Lock", BridgeMessageType.LOCK, BridgeMessageType.fromString("Lock"))
    }

    @Test
    fun testMessageTypeFromStringInvalid() {
        val result = BridgeMessageType.fromString("InvalidType")
        assertNull("Should return null for invalid type", result)
    }

    @Test
    fun testHandshakeMessage() {
        val msg = Handshake(
            requestId = "req-123",
            clientName = "TestClient",
            clientVersion = "1.0"
        )

        assertEquals("Message type", BridgeMessageType.HANDSHAKE, msg.messageType)
        assertEquals("Request ID", "req-123", msg.requestId)
        assertEquals("Client name", "TestClient", msg.clientName)
        assertEquals("Client version", "1.0", msg.clientVersion)
        assertEquals("Protocol", BridgeProtocol.PROTOCOL, msg.protocol)
        assertEquals("Protocol version", BridgeProtocol.VERSION, msg.protocolVersion)
    }

    @Test
    fun testHandshakeResponseMessage() {
        val msg = HandshakeResponse(
            requestId = "req-123",
            appName = "TrustVault",
            appVersion = "1.0.0",
            serverIdHash = "abcd1234"
        )

        assertEquals("Message type", BridgeMessageType.HANDSHAKE_RESPONSE, msg.messageType)
        assertEquals("Request ID", "req-123", msg.requestId)
        assertEquals("App name", "TrustVault", msg.appName)
        assertEquals("App version", "1.0.0", msg.appVersion)
        assertEquals("Server ID hash", "abcd1234", msg.serverIdHash)
    }

    @Test
    fun testTestAssociateMessage() {
        val msg = TestAssociate(
            requestId = "req-456",
            key = "base64_key",
            keyHash = "hash_value",
            deviceName = "MyDevice"
        )

        assertEquals("Message type", BridgeMessageType.TEST_ASSOCIATE, msg.messageType)
        assertEquals("Request ID", "req-456", msg.requestId)
        assertEquals("Key", "base64_key", msg.key)
        assertEquals("Key hash", "hash_value", msg.keyHash)
        assertEquals("Device name", "MyDevice", msg.deviceName)
    }

    @Test
    fun testTestAssociateMessageDefaults() {
        val msg = TestAssociate(
            requestId = "req-789",
            key = "key",
            keyHash = "hash"
        )

        assertEquals("Device name default", "Unknown Device", msg.deviceName)
    }

    @Test
    fun testAssociateResponseSuccess() {
        val msg = AssociateResponse(
            requestId = "req-456",
            id = "pairing-123",
            success = true
        )

        assertEquals("Message type", BridgeMessageType.ASSOCIATE_RESPONSE, msg.messageType)
        assertEquals("Pairing ID", "pairing-123", msg.id)
        assertTrue("Success", msg.success)
        assertEquals("Error message", "", msg.errorMessage)
    }

    @Test
    fun testAssociateResponseFailure() {
        val msg = AssociateResponse(
            requestId = "req-456",
            success = false,
            errorMessage = "Invalid secret"
        )

        assertEquals("Message type", BridgeMessageType.ASSOCIATE_RESPONSE, msg.messageType)
        assertFalse("Success", msg.success)
        assertEquals("Error message", "Invalid secret", msg.errorMessage)
    }

    @Test
    fun testGetLoginsMessage() {
        val msg = GetLogins(
            requestId = "req-789",
            url = "https://github.com",
            id = "pairing-123"
        )

        assertEquals("Message type", BridgeMessageType.GET_LOGINS, msg.messageType)
        assertEquals("Request ID", "req-789", msg.requestId)
        assertEquals("URL", "https://github.com", msg.url)
        assertEquals("Pairing ID", "pairing-123", msg.id)
    }

    @Test
    fun testLoginResponseMessage() {
        val entries = listOf(
            BridgeCredential("GitHub", "user@example.com", "password123", ""),
            BridgeCredential("Gmail", "john@gmail.com", "pass456", "123456")
        )

        val msg = LoginResponse(
            requestId = "req-789",
            entries = entries,
            count = 2
        )

        assertEquals("Message type", BridgeMessageType.LOGIN_RESPONSE, msg.messageType)
        assertEquals("Entries count", 2, msg.entries.size)
        assertEquals("Count field", 2, msg.count)
        assertEquals("First entry name", "GitHub", msg.entries[0].name)
        assertEquals("Second entry TOTP", "123456", msg.entries[1].totp)
    }

    @Test
    fun testLoginResponseEmpty() {
        val msg = LoginResponse(
            requestId = "req-789"
        )

        assertEquals("Message type", BridgeMessageType.LOGIN_RESPONSE, msg.messageType)
        assertTrue("Entries should be empty", msg.entries.isEmpty())
        assertEquals("Count should be 0", 0, msg.count)
    }

    @Test
    fun testLockMessage() {
        val msg = Lock(
            requestId = "req-999",
            id = "pairing-123"
        )

        assertEquals("Message type", BridgeMessageType.LOCK, msg.messageType)
        assertEquals("Request ID", "req-999", msg.requestId)
        assertEquals("Pairing ID", "pairing-123", msg.id)
    }

    @Test
    fun testErrorMessage() {
        val msg = ErrorMessage(
            requestId = "req-999",
            code = "NOT_PAIRED",
            message = "Device is not paired"
        )

        assertEquals("Message type", BridgeMessageType.ERROR, msg.messageType)
        assertEquals("Request ID", "req-999", msg.requestId)
        assertEquals("Error code", "NOT_PAIRED", msg.code)
        assertEquals("Error message", "Device is not paired", msg.message)
    }

    @Test
    fun testBridgeCredential() {
        val cred = BridgeCredential(
            name = "GitHub Account",
            login = "john@example.com",
            password = "encrypted_password",
            totp = "123456"
        )

        assertEquals("Name", "GitHub Account", cred.name)
        assertEquals("Login", "john@example.com", cred.login)
        assertEquals("Password", "encrypted_password", cred.password)
        assertEquals("TOTP", "123456", cred.totp)
    }

    @Test
    fun testBridgeCredentialNoTotp() {
        val cred = BridgeCredential(
            name = "GitHub Account",
            login = "john@example.com",
            password = "encrypted_password"
        )

        assertEquals("TOTP default", "", cred.totp)
    }

    @Test
    fun testBridgeErrorCodes() {
        assertEquals("PROTOCOL_ERROR", "PROTOCOL_ERROR", BridgeErrorCode.PROTOCOL_ERROR)
        assertEquals("UNAUTHORIZED", "UNAUTHORIZED", BridgeErrorCode.UNAUTHORIZED)
        assertEquals("NOT_PAIRED", "NOT_PAIRED", BridgeErrorCode.NOT_PAIRED)
        assertEquals("INVALID_SHARED_SECRET", "INVALID_SHARED_SECRET", BridgeErrorCode.INVALID_SHARED_SECRET)
        assertEquals("PAIRING_EXISTS", "PAIRING_EXISTS", BridgeErrorCode.PAIRING_EXISTS)
        assertEquals("DATABASE_LOCKED", "DATABASE_LOCKED", BridgeErrorCode.DATABASE_LOCKED)
        assertEquals("URL_NOT_MATCHED", "URL_NOT_MATCHED", BridgeErrorCode.URL_NOT_MATCHED)
        assertEquals("INTERNAL_ERROR", "INTERNAL_ERROR", BridgeErrorCode.INTERNAL_ERROR)
    }

    @Test
    fun testMessagePolymorphism() {
        // Test that messages can be stored in BridgeMessage list
        val messages: List<BridgeMessage> = listOf(
            Handshake("req1", "Client", "1.0"),
            HandshakeResponse("req1", serverIdHash = "hash"),
            TestAssociate("req2", "key", "keyhash"),
            AssociateResponse("req2", "pairing-123", success = true),
            GetLogins("req3", "https://example.com", "pairing-123"),
            LoginResponse("req3", emptyList()),
            Lock("req4", "pairing-123"),
            ErrorMessage("req5", "ERROR", "Test error")
        )

        assertEquals("Should have 8 messages", 8, messages.size)
        assertTrue("All messages are BridgeMessage", messages.all { it is BridgeMessage })
        assertEquals("Message types are correct", 8, messages.map { it.messageType }.distinct().size)
    }

    @Test
    fun testMessageRequestIdPreservation() {
        val requestId = "unique-req-id-12345"

        val handshake = Handshake(requestId, "Client", "1.0")
        val response = HandshakeResponse(requestId, serverIdHash = "hash")

        assertEquals("Request ID should match in response", requestId, handshake.requestId)
        assertEquals("Request ID should match in response", requestId, response.requestId)
    }

    @Test
    fun testMessageImmutability() {
        val original = TestAssociate("req", "key", "hash", "Device")

        // Data classes should be immutable
        assertEquals("Original name", "Device", original.deviceName)

        // Cannot modify (data class properties are val)
        // This test passes if it compiles
    }
}
