package com.hacksecure.p2p.network.transport

import com.hacksecure.p2p.messaging.models.MessagePacket
import com.hacksecure.p2p.utils.SerializationUtils
import com.hacksecure.p2p.network.wifidirect.ConnectionHandler

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