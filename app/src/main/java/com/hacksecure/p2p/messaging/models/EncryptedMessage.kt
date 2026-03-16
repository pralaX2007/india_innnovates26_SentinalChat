package com.sentinel.chat.messaging.models

import java.util.Base64

data class EncryptedMessage(

    val dhPublicKey: String,

    val messageNumber: Int,

    val previousChainLength: Int,

    val iv: String,

    val ciphertext: String
) {

    companion object {

        /**
         * Create model from raw ratchet output
         */
        fun fromBytes(
            dhPublicKey: ByteArray,
            messageNumber: Int,
            previousChainLength: Int,
            iv: ByteArray,
            ciphertext: ByteArray
        ): EncryptedMessage {

            return EncryptedMessage(
                dhPublicKey = encode(dhPublicKey),
                messageNumber = messageNumber,
                previousChainLength = previousChainLength,
                iv = encode(iv),
                ciphertext = encode(ciphertext)
            )
        }

        private fun encode(bytes: ByteArray): String {
            return Base64.getEncoder().encodeToString(bytes)
        }

        private fun decode(value: String): ByteArray {
            return Base64.getDecoder().decode(value)
        }
    }

    /**
     * Decode header public key
     */
    fun dhPublicKeyBytes(): ByteArray {
        return Base64.getDecoder().decode(dhPublicKey)
    }

    /**
     * Decode IV
     */
    fun ivBytes(): ByteArray {
        return Base64.getDecoder().decode(iv)
    }

    /**
     * Decode ciphertext
     */
    fun ciphertextBytes(): ByteArray {
        return Base64.getDecoder().decode(ciphertext)
    }

    /**
     * Basic structural validation
     */
    fun validate() {

        require(dhPublicKey.isNotBlank()) {
            "Invalid encrypted message: missing DH key"
        }

        require(ciphertext.isNotBlank()) {
            "Invalid encrypted message: ciphertext missing"
        }

        require(iv.isNotBlank()) {
            "Invalid encrypted message: IV missing"
        }

        require(messageNumber >= 0) {
            "Invalid message number"
        }

        require(previousChainLength >= 0) {
            "Invalid chain length"
        }
    }
}