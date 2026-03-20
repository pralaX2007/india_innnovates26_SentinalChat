package com.hacksecure.p2p.network.transport

import com.hacksecure.p2p.Protocol.Ratchet.DoubleRatchet
import com.hacksecure.p2p.network.wifidirect.ConnectionHandler
import com.hacksecure.p2p.session.SessionManager
import com.hacksecure.p2p.utils.SerializationUtils

class MessageReceiver(
    private val connectionHandler: ConnectionHandler,
    private val ratchet: DoubleRatchet,
    private val peerId: String                     // Fixed: was missing, caused undefined reference
) {
    companion object {
        private const val MAX_PACKET_SIZE = 128 * 1024
    }

    /**
     * Deserialize, replay-check, then decrypt a received raw message.
     */
    fun receive(rawData: ByteArray): String {
        require(rawData.size <= MAX_PACKET_SIZE) { "Packet too large" }

        val packet = SerializationUtils.deserialize(rawData)

        // Derive ratchetKeyId from the DH public key in the header.
        // This resets per DH ratchet step, which is exactly the epoch boundary
        // that message numbers reset on — matching how ReplayProtection is keyed.
        val ratchetKeyId = packet.header.dhPublicKey.joinToString(separator = ":")

        if (!SessionManager.validateMessage(peerId, ratchetKeyId, packet.header.messageNumber)) {
            throw IllegalStateException(
                "Replay attack detected — message #${packet.header.messageNumber} " +
                "already seen for ratchet key ${ratchetKeyId.take(16)}..."
            )
        }

        val encrypted = DoubleRatchet.EncryptedMessage(
            header = DoubleRatchet.Header(
                packet.header.dhPublicKey,
                packet.header.messageNumber,
                packet.header.previousChainLength
            ),
            iv = packet.iv,
            ciphertext = packet.ciphertext
        )

        // Fixed: was calling undefined 'decryptor.decrypt()' — now uses the ratchet directly
        val plaintext = ratchet.decrypt(encrypted)

        return String(plaintext, Charsets.UTF_8)
    }
}
