package com.hacksecure.p2p.utils

import com.hacksecure.p2p.messaging.models.MessageMetadata
import com.hacksecure.p2p.messaging.models.MessagePacket
import com.hacksecure.p2p.messaging.models.RatchetHeader
import java.nio.ByteBuffer

object SerializationUtils {

    private const val VERSION: Byte = 1

    private const val MAX_HEADER = 512
    private const val MAX_CIPHERTEXT = 64 * 1024
    private const val IV_SIZE = 12

    fun serialize(packet: MessagePacket): ByteArray {

        val header = packet.header
        val metadata = packet.metadata

        val headerBytes = header.dhPublicKey
        val iv = packet.iv
        val cipher = packet.ciphertext

        require(headerBytes.size <= MAX_HEADER)
        require(iv.size == IV_SIZE)
        require(cipher.size <= MAX_CIPHERTEXT)

        val senderBytes = metadata.senderId.toByteArray(Charsets.UTF_8)
        val messageIdBytes = metadata.messageId.toByteArray(Charsets.UTF_8)

        val buffer = ByteBuffer.allocate(
            1 +
                    4 + headerBytes.size +
                    4 + 4 +
                    4 + iv.size +
                    4 + cipher.size +
                    4 + senderBytes.size +
                    4 + messageIdBytes.size +
                    8 +
                    8
        )

        buffer.put(VERSION)

        buffer.putInt(headerBytes.size)
        buffer.put(headerBytes)

        buffer.putInt(header.messageNumber)
        buffer.putInt(header.previousChainLength)

        buffer.putInt(iv.size)
        buffer.put(iv)

        buffer.putInt(cipher.size)
        buffer.put(cipher)

        buffer.putInt(senderBytes.size)
        buffer.put(senderBytes)

        buffer.putInt(messageIdBytes.size)
        buffer.put(messageIdBytes)

        buffer.putLong(metadata.timestamp)
        buffer.putLong(metadata.ttlSeconds)

        return buffer.array()
    }

    fun deserialize(data: ByteArray): MessagePacket {

        val buffer = ByteBuffer.wrap(data)

        val version = buffer.get()
        require(version == VERSION) { "Unsupported packet version" }

        val headerLen = buffer.int
        require(headerLen in 1..MAX_HEADER)

        val headerBytes = ByteArray(headerLen)
        buffer.get(headerBytes)

        val messageNumber = buffer.int
        val previousChainLength = buffer.int

        val ivLen = buffer.int
        require(ivLen == IV_SIZE)

        val iv = ByteArray(ivLen)
        buffer.get(iv)

        val cipherLen = buffer.int
        require(cipherLen in 1..MAX_CIPHERTEXT)

        val cipher = ByteArray(cipherLen)
        buffer.get(cipher)

        val senderLen = buffer.int
        val senderBytes = ByteArray(senderLen)
        buffer.get(senderBytes)

        val messageIdLen = buffer.int
        val messageIdBytes = ByteArray(messageIdLen)
        buffer.get(messageIdBytes)

        val timestamp = buffer.long
        val ttl = buffer.long

        val header = RatchetHeader(
            dhPublicKey = headerBytes,
            messageNumber = messageNumber,
            previousChainLength = previousChainLength
        )

        val metadata = MessageMetadata(
            senderId = String(senderBytes, Charsets.UTF_8),
            messageId = String(messageIdBytes, Charsets.UTF_8),
            timestamp = timestamp,
            ttlSeconds = ttl
        )

        return MessagePacket(
            header = header,
            iv = iv,
            ciphertext = cipher,
            metadata = metadata
        )
    }
}