package com.sentinel.chat.messaging.transport

import com.sentinel.chat.messaging.model.MessagePacket
import com.sentinel.chat.messaging.service.MessageDecryptor
import com.sentinel.chat.utils.SerializationUtils
import com.sentinel.chat.network.ConnectionHandler

class MessageReceiver(
    private val connectionHandler: ConnectionHandler,
    private val decryptor: MessageDecryptor
) {

    companion object {
        private const val MAX_PACKET_SIZE = 128 * 1024
    }


    fun receive(): String? {

        val rawBytes = connectionHandler.receiveRawBytes() ?: return null

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
}