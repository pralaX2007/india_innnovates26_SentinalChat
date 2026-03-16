package com.sentinel.chat.messaging.transport

import com.sentinel.chat.messaging.model.MessagePacket
import com.sentinel.chat.utils.SerializationUtils
import com.sentinel.chat.network.ConnectionHandler

class MessageSender(
    private val connectionHandler: ConnectionHandler
) {

    companion object {
        // Hard upper bound for packet size
        private const val MAX_PACKET_SIZE = 128 * 1024
    }


    fun send(packet: MessagePacket) {

        val serialized = SerializationUtils.serialize(packet)

        require(serialized.size <= MAX_PACKET_SIZE) {
            "Packet too large for transport layer"
        }

        connectionHandler.sendRawBytes(serialized)
    }
}