package com.hacksecure.p2p.network.transport

import com.hacksecure.p2p.messaging.models.MessagePacket
import com.hacksecure.p2p.messaging.encryption.MessageDecryptor
import com.hacksecure.p2p.utils.SerializationUtils
import com.hacksecure.p2p.network.wifidirect.ConnectionHandler

class MessageReceiver(
    private val connectionHandler: ConnectionHandler,
    private val decryptor: MessageDecryptor
) {

    companion object {
        private const val MAX_PACKET_SIZE = 128 * 1024
    }


    /**
     * Receive and decrypt a message from the given raw bytes
     */
    fun receive(rawBytes: ByteArray): String? {

        require(rawBytes.size <= MAX_PACKET_SIZE) {
            "Incoming packet exceeds allowed size"
        }

        val packet: MessagePacket = SerializationUtils.deserialize(rawBytes)

        return decryptor.decrypt(
            MessageDecryptor.NetworkMessage(
                headerDh = packet.header.dhPublicKey.let { String(it) },
                messageNumber = packet.header.messageNumber,
                previousChainLength = packet.header.previousChainLength,
                iv = packet.iv.let { String(it) },
                ciphertext = packet.ciphertext.let { String(it) }
            )
        )
    }

    /**
     * No-arg version for compatibility — returns null (use listener pattern instead)
     */
    fun receive(): String? {
        return null
    }
}