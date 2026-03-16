package com.sentinel.chat.crypto.ratchet

import com.sentinel.chat.crypto.kdf.HKDF

class ChainKey(private val key: ByteArray) {

    companion object {
        private const val CHAIN_KEY_SIZE = 32
        private const val MESSAGE_KEY_SIZE = 32
        private const val DERIVED_SIZE = CHAIN_KEY_SIZE + MESSAGE_KEY_SIZE

        private val INFO = "DoubleRatchetChainKey".toByteArray()
    }

    data class ChainKeyStep(
        val nextChainKey: ChainKey,
        val messageKey: ByteArray
    )


    fun deriveMessageKey(): ChainKeyStep {

        val derived = HKDF.deriveKey(
            salt = key,
            ikm = byteArrayOf(0x01),
            info = INFO,
            length = DERIVED_SIZE
        )

        val nextChainKeyBytes = derived.copyOfRange(0, CHAIN_KEY_SIZE)
        val messageKeyBytes = derived.copyOfRange(CHAIN_KEY_SIZE, DERIVED_SIZE)

        return ChainKeyStep(
            ChainKey(nextChainKeyBytes),
            messageKeyBytes
        )
    }

    fun getKeyBytes(): ByteArray {
        return key.copyOf()
    }
}