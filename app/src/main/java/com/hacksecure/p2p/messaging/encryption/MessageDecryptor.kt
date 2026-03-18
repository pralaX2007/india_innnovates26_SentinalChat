package com.hacksecure.p2p.messaging.encryption

import com.hacksecure.p2p.Protocol.Ratchet.DoubleRatchet
import com.hacksecure.p2p.Protocol.Ratchet.DoubleRatchet.EncryptedMessage
import com.hacksecure.p2p.Protocol.Ratchet.DoubleRatchet.Header
import java.util.Base64

class MessageDecryptor(
    private val ratchet: DoubleRatchet
) {

    companion object {
        private const val IV_SIZE = 12
        private const val MAX_HEADER = 512
        private const val MAX_CIPHERTEXT = 64 * 1024
    }

    data class NetworkMessage(
        val headerDh: String,
        val messageNumber: Int,
        val previousChainLength: Int,
        val iv: String,
        val ciphertext: String
    )

    fun decrypt(message: NetworkMessage): String {

        validate(message)

        val headerBytes = Base64.getDecoder().decode(message.headerDh)
        val ivBytes = Base64.getDecoder().decode(message.iv)
        val cipherBytes = Base64.getDecoder().decode(message.ciphertext)

        val header = Header(
            dhPublicKey = headerBytes,
            messageNumber = message.messageNumber,
            previousChainLength = message.previousChainLength
        )

        val encryptedMessage = EncryptedMessage(
            header = header,
            iv = ivBytes,
            ciphertext = cipherBytes
        )

        val plaintextBytes = ratchet.decrypt(encryptedMessage)

        return String(plaintextBytes, Charsets.UTF_8)
    }

    private fun validate(message: NetworkMessage) {

        require(message.headerDh.isNotEmpty())
        require(message.ciphertext.isNotEmpty())
        require(message.iv.isNotEmpty())
        require(message.messageNumber >= 0)

        val headerBytes = Base64.getDecoder().decode(message.headerDh)
        val ivBytes = Base64.getDecoder().decode(message.iv)
        val cipherBytes = Base64.getDecoder().decode(message.ciphertext)

        require(headerBytes.size <= MAX_HEADER)
        require(ivBytes.size == IV_SIZE)
        require(cipherBytes.size <= MAX_CIPHERTEXT)
    }
}