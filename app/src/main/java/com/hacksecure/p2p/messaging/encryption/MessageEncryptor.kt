package com.hacksecure.p2p.messaging.encryption

import com.hacksecure.p2p.Protocol.Ratchet.DoubleRatchet
import java.nio.ByteBuffer
import java.util.Base64

class MessageEncryptor(
    private val ratchet: DoubleRatchet
) {

    companion object {
        private const val VERSION: Byte = 1
        private const val MAX_HEADER = 512
        private const val MAX_CIPHERTEXT = 64 * 1024
        private const val IV_SIZE = 12
    }

    data class NetworkMessage(
        val headerDh: String,
        val messageNumber: Int,
        val previousChainLength: Int,
        val iv: String,
        val ciphertext: String
    )

    fun encryptMessage(plaintext: String): NetworkMessage {

        val encrypted = ratchet.encrypt(
            plaintext.toByteArray(Charsets.UTF_8)
        )

        return NetworkMessage(
            headerDh = Base64.getEncoder().encodeToString(encrypted.header.dhPublicKey),
            messageNumber = encrypted.header.messageNumber,
            previousChainLength = encrypted.header.previousChainLength,
            iv = Base64.getEncoder().encodeToString(encrypted.iv),
            ciphertext = Base64.getEncoder().encodeToString(encrypted.ciphertext)
        )
    }

    fun serialize(message: NetworkMessage): ByteArray {

        val headerBytes = Base64.getDecoder().decode(message.headerDh)
        val ivBytes = Base64.getDecoder().decode(message.iv)
        val cipherBytes = Base64.getDecoder().decode(message.ciphertext)

        require(headerBytes.size <= MAX_HEADER)
        require(ivBytes.size == IV_SIZE)
        require(cipherBytes.size <= MAX_CIPHERTEXT)

        val buffer = ByteBuffer.allocate(
            1 +
                    4 + headerBytes.size +
                    4 + 4 +
                    4 + ivBytes.size +
                    4 + cipherBytes.size
        )

        buffer.put(VERSION)

        buffer.putInt(headerBytes.size)
        buffer.put(headerBytes)

        buffer.putInt(message.messageNumber)
        buffer.putInt(message.previousChainLength)

        buffer.putInt(ivBytes.size)
        buffer.put(ivBytes)

        buffer.putInt(cipherBytes.size)
        buffer.put(cipherBytes)

        return buffer.array()
    }

    fun deserialize(data: ByteArray): NetworkMessage {

        val buffer = ByteBuffer.wrap(data)

        val version = buffer.get()
        require(version == VERSION) { "Unsupported protocol version" }

        val headerLen = buffer.int
        require(headerLen in 1..MAX_HEADER)

        val headerBytes = ByteArray(headerLen)
        buffer.get(headerBytes)

        val messageNumber = buffer.int
        val previousChainLength = buffer.int

        val ivLen = buffer.int
        require(ivLen == IV_SIZE)

        val ivBytes = ByteArray(ivLen)
        buffer.get(ivBytes)

        val cipherLen = buffer.int
        require(cipherLen in 1..MAX_CIPHERTEXT)

        val cipherBytes = ByteArray(cipherLen)
        buffer.get(cipherBytes)

        return NetworkMessage(
            headerDh = Base64.getEncoder().encodeToString(headerBytes),
            messageNumber = messageNumber,
            previousChainLength = previousChainLength,
            iv = Base64.getEncoder().encodeToString(ivBytes),
            ciphertext = Base64.getEncoder().encodeToString(cipherBytes)
        )
    }
}