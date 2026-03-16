package com.sentinel.chat.messaging.service

import com.sentinel.chat.crypto.ratchet.DoubleRatchet
import com.sentinel.chat.crypto.ratchet.DoubleRatchet.EncryptedMessage
import com.sentinel.chat.crypto.ratchet.DoubleRatchet.Header
import java.util.Base64

class MessageDecryptor(
    private val ratchet: DoubleRatchet
) {

    data class NetworkMessage(
        val headerDh: String,
        val messageNumber: Int,
        val previousChainLength: Int,
        val iv: String,
        val ciphertext: String
    )

    /**
     * Decrypt message received from network
     */
    fun decrypt(message: NetworkMessage): String {

        validate(message)

        val header = Header(
            dhPublicKey = Base64.getDecoder().decode(message.headerDh),
            messageNumber = message.messageNumber,
            previousChainLength = message.previousChainLength
        )

        val encryptedMessage = EncryptedMessage(
            header = header,
            iv = Base64.getDecoder().decode(message.iv),
            ciphertext = Base64.getDecoder().decode(message.ciphertext)
        )

        val plaintextBytes = ratchet.decrypt(encryptedMessage)

        return plaintextBytes.toString(Charsets.UTF_8)
    }

    /**
     * Basic structural validation
     */
    private fun validate(message: NetworkMessage) {

        require(message.headerDh.isNotEmpty()) {
            "Invalid message: missing header key"
        }

        require(message.ciphertext.isNotEmpty()) {
            "Invalid message: empty ciphertext"
        }

        require(message.iv.isNotEmpty()) {
            "Invalid message: missing IV"
        }

        require(message.messageNumber >= 0) {
            "Invalid message number"
        }
    }
}