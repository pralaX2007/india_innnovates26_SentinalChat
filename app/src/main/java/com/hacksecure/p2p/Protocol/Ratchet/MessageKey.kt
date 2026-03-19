package com.hacksecure.p2p.Protocol.Ratchet

import com.hacksecure.p2p.security.MemoryCleaner

class MessageKey(

    private val key: ByteArray,
    val messageIndex: Int
) {

    companion object {

        private const val AES_KEY_LENGTH = 32

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
     * Destroy key material after use
     */
    fun destroy() {
        MemoryCleaner.wipe(key)
    }
}