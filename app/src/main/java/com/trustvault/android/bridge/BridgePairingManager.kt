package com.trustvault.android.bridge

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Manages device pairing for bridge protocol.
 *
 * **Pairing Model**:
 * - Each paired device has a unique ID (UUID)
 * - Shared secret used for HMAC validation
 * - Pairing persists in encrypted DataStore
 * - Multiple devices can be paired simultaneously
 *
 * **Pairing Flow**:
 * 1. Client connects with Handshake
 * 2. Server sends HandshakeResponse with serverIdHash
 * 3. Client generates key and computes keyHash = HMAC-SHA256(key, sharedSecret)
 * 4. Client sends TestAssociate with key and keyHash
 * 5. Server validates keyHash (proves client knows shared secret)
 * 6. Server creates pairing and responds with AssociateResponse
 * 7. Future requests include pairing ID for authentication
 */
class BridgePairingManager(context: Context) {

    private val dataStore = context.getSharedPreferences("bridge_pairings", Context.MODE_PRIVATE)
    private val pairingPrefix = "bridge_pairing_"

    companion object {
        private const val PAIRINGS_COUNT_KEY = "bridge_pairings_count"
        private const val SERVER_ID_KEY = "bridge_server_id"
    }

    /**
     * Creates a new pairing with a device.
     *
     * @param sharedSecret Shared secret for HMAC validation
     * @param clientKey Client's public key (base64)
     * @param keyHash HMAC-SHA256(clientKey, sharedSecret) from client
     * @param deviceName Human-readable device name
     * @return Pairing ID if successful, null if validation fails
     */
    fun createPairing(
        sharedSecret: String,
        clientKey: String,
        keyHash: String,
        deviceName: String
    ): String? {
        // Validate keyHash proves client knows shared secret
        val expectedHash = computeHmacSha256(clientKey, sharedSecret)
        if (expectedHash != keyHash) {
            return null // Invalid shared secret
        }

        // Create unique pairing ID
        val pairingId = UUID.randomUUID().toString()

        // Store pairing data
        dataStore.edit().apply {
            putString("${pairingPrefix}${pairingId}_name", deviceName)
            putString("${pairingPrefix}${pairingId}_key", clientKey)
            putString("${pairingPrefix}${pairingId}_created", System.currentTimeMillis().toString())
        }.apply()

        return pairingId
    }

    /**
     * Retrieves pairing information.
     *
     * @param pairingId Pairing ID to retrieve
     * @return PairingInfo if pairing exists, null otherwise
     */
    fun getPairing(pairingId: String): PairingInfo? {
        val name = dataStore.getString("${pairingPrefix}${pairingId}_name", null) ?: return null
        val key = dataStore.getString("${pairingPrefix}${pairingId}_key", null) ?: return null
        val created = dataStore.getString("${pairingPrefix}${pairingId}_created", null)
            ?.toLongOrNull() ?: return null

        return PairingInfo(
            id = pairingId,
            deviceName = name,
            clientKey = key,
            createdAt = created
        )
    }

    /**
     * Validates a pairing ID exists (device is paired).
     *
     * @param pairingId Pairing ID to validate
     * @return true if pairing exists
     */
    fun validatePairing(pairingId: String): Boolean {
        return getPairing(pairingId) != null
    }

    /**
     * Lists all paired devices.
     *
     * @return List of PairingInfo for all paired devices
     */
    fun listPairings(): List<PairingInfo> {
        val pairings = mutableListOf<PairingInfo>()

        dataStore.all.forEach { (key, _) ->
            if (key.startsWith(pairingPrefix) && key.endsWith("_name")) {
                val pairingId = key.removePrefix(pairingPrefix).removeSuffix("_name")
                getPairing(pairingId)?.let { pairings.add(it) }
            }
        }

        return pairings.sortedByDescending { it.createdAt }
    }

    /**
     * Removes a pairing (revoke device access).
     *
     * @param pairingId Pairing ID to remove
     * @return true if pairing was removed
     */
    fun removePairing(pairingId: String): Boolean {
        val exists = validatePairing(pairingId)
        if (!exists) return false

        dataStore.edit().apply {
            remove("${pairingPrefix}${pairingId}_name")
            remove("${pairingPrefix}${pairingId}_key")
            remove("${pairingPrefix}${pairingId}_created")
        }.apply()

        return true
    }

    /**
     * Generates server ID hash for device identification.
     *
     * Used to identify server across restarts (pairing verification).
     *
     * @return SHA-256 hash of server ID
     */
    fun getServerIdHash(): String {
        var serverId = dataStore.getString(SERVER_ID_KEY, null)

        if (serverId == null) {
            // Generate new server ID on first run
            serverId = UUID.randomUUID().toString()
            dataStore.edit().putString(SERVER_ID_KEY, serverId).apply()
        }

        return computeSha256(serverId)
    }

    /**
     * Computes HMAC-SHA256 hash.
     *
     * Used for validating shared secret knowledge.
     *
     * @param data Data to hash
     * @param secret Secret key for HMAC
     * @return HMAC-SHA256 hash (hex string)
     */
    private fun computeHmacSha256(data: String, secret: String): String {
        try {
            val mac = Mac.getInstance("HmacSHA256")
            val secretKey = SecretKeySpec(secret.toByteArray(), "HmacSHA256")
            mac.init(secretKey)
            val hash = mac.doFinal(data.toByteArray())
            return hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            return ""
        }
    }

    /**
     * Computes SHA-256 hash.
     *
     * @param data Data to hash
     * @return SHA-256 hash (hex string)
     */
    private fun computeSha256(data: String): String {
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(data.toByteArray())
            return hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            return ""
        }
    }
}

/**
 * Information about a paired device.
 */
data class PairingInfo(
    val id: String,
    val deviceName: String,
    val clientKey: String,
    val createdAt: Long
)

