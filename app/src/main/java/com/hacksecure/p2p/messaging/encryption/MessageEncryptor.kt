package com.sentinel.chat.crypto.messaging

import com.sentinel.chat.crypto.ratchet.DoubleRatchet
import java.nio.ByteBuffer
import java.util.Base64

class MessageEncryptor(
    private val ratchet: DoubleRatchet
) {

    data class NetworkMessage(
        val headerDh: String,
        val messageNumber: Int,
        val previousChainLength: Int,
        val iv: String,
        val ciphertext: String
    )


    fun encryptMessage(plaintext: String): NetworkMessage {

        val encrypted = ratchet.encrypt(plaintext.toByteArray())

        return NetworkMessage(
            headerDh = Base64.getEncoder().encodeToString(encrypted.header.dhPublicKey),
            messageNumber = encrypted.header.messageNumber,
            previousChainLength = encrypted.header.previousChainLength,
            iv = Base64.getEncoder().encodeToString(encrypted.iv),
            ciphertext = Base64.getEncoder().encodeToString(encrypted.ciphertext)
        )
    }


    fun decryptMessage(message: NetworkMessage): String {

        val header = DoubleRatchet.Header(
            dhPublicKey = Base64.getDecoder().decode(message.headerDh),
            messageNumber = message.messageNumber,
            previousChainLength = message.previousChainLength
        )

        val encrypted = DoubleRatchet.EncryptedMessage(
            header = header,
            iv = Base64.getDecoder().decode(message.iv),
            ciphertext = Base64.getDecoder().decode(message.ciphertext)
        )

        val plaintextBytes = ratchet.decrypt(encrypted)

        return String(plaintextBytes)
    }


    fun serialize(message: NetworkMessage): ByteArray {

        val headerBytes = Base64.getDecoder().decode(message.headerDh)
        val ivBytes = Base64.getDecoder().decode(message.iv)
        val cipherBytes = Base64.getDecoder().decode(message.ciphertext)

        val buffer = ByteBuffer.allocate(
            4 + headerBytes.size +
                    4 +
                    4 +
                    4 + ivBytes.size +
                    4 + cipherBytes.size
        )

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

        val headerLen = buffer.int
        val headerBytes = ByteArray(headerLen)
        buffer.get(headerBytes)

        val messageNumber = buffer.int
        val previousChainLength = buffer.int

        val ivLen = buffer.int
        val ivBytes = ByteArray(ivLen)
        buffer.get(ivBytes)

        val cipherLen = buffer.int
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