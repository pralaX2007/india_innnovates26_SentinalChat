package com.sentinel.chat.crypto.ratchet

import java.nio.ByteBuffer
import java.security.SecureRandom

class MessageKey(

    private val key: ByteArray,

    val messageIndex: Int
) {

    companion object {

        private const val AES_KEY_LENGTH = 32
        private const val IV_LENGTH = 12

        private val secureRandom = SecureRandom()

        /**
         * Create MessageKey from derived key material
         */
        fun fromBytes(
            keyMaterial: ByteArray,
            messageIndex: Int
        ): MessageKey {

            require(keyMaterial.size >= AES_KEY_LENGTH) {
                "Message key must be at least $AES_KEY_LENGTH bytes"
            }

            val key = keyMaterial.copyOfRange(0, AES_KEY_LENGTH)

            return MessageKey(key, messageIndex)
        }
    }

    /**
     * AES encryption key
     */
    fun getKeyBytes(): ByteArray {
        return key.copyOf()
    }

    /**
     * Generate IV for AES-GCM
     * IV derived from message index to avoid reuse
     */
    fun generateIV(): ByteArray {

        val buffer = ByteBuffer.allocate(IV_LENGTH)

        buffer.putInt(messageIndex)

        val random = ByteArray(IV_LENGTH - 4)
        secureRandom.nextBytes(random)

        buffer.put(random)

        return buffer.array()
    }

    /**
     * Destroy key material when no longer needed
     */
    fun destroy() {

        for (i in key.indices) {
            key[i] = 0
        }
    }
}