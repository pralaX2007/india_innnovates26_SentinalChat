package com.hacksecure.p2p.Protocol.Ratchet

import com.hacksecure.p2p.crypto.kdf.HKDF
import java.util.Arrays

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

        val currentKey = key.copyOf()

        val derived = HKDF.deriveKey(
            salt = null,
            ikm = currentKey,
            info = INFO,
            length = DERIVED_SIZE
        )

        val nextChainKeyBytes = derived.copyOfRange(0, CHAIN_KEY_SIZE)
        val messageKeyBytes = derived.copyOfRange(CHAIN_KEY_SIZE, DERIVED_SIZE)

        // destroy previous chain key
        Arrays.fill(currentKey, 0)

        return ChainKeyStep(
            ChainKey(nextChainKeyBytes),
            messageKeyBytes
        )
    }

    fun getKeyBytes(): ByteArray {
        return key.copyOf()
    }
}