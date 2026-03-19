package com.hacksecure.p2p.network.transport

import com.hacksecure.p2p.messaging.models.MessagePacket
import com.hacksecure.p2p.Protocol.Ratchet.DoubleRatchet
import com.hacksecure.p2p.utils.SerializationUtils
import com.hacksecure.p2p.network.wifidirect.ConnectionHandler

class MessageReceiver(
    private val connectionHandler: ConnectionHandler,
    private val ratchet: DoubleRatchet
) {

    companion object {
        private const val MAX_PACKET_SIZE = 128 * 1024
    }


    /**
     * Receive and decrypt a message from the given raw bytes
     */
    fun receive(rawData: ByteArray): String {

        val packet = SerializationUtils.deserialize(rawData)

        val encrypted = DoubleRatchet.EncryptedMessage(
            header = DoubleRatchet.Header(
                packet.header.dhPublicKey,
                packet.header.messageNumber,
                packet.header.previousChainLength
            ),
            iv = packet.iv,
            ciphertext = packet.ciphertext
        )
        if (!sessionManager.validateMessage(peerId, packet.header.messageNumber)) {
            throw IllegalStateException("Replay detected")
        }
        val plaintext = decryptor.decrypt(encrypted)

        return String(plaintext, Charsets.UTF_8)
    }

    /**
     * No-arg version for compatibility — returns null (use listener pattern instead)
     */

}