package com.sentinel.chat.Protocol.Ratchet

import com.sentinel.chat.crypto.kdf.HKDF

class RootKey(private val key: ByteArray) {

    companion object {
        private const val ROOT_KEY_SIZE = 32
        private const val CHAIN_KEY_SIZE = 32
        private const val DERIVED_KEY_SIZE = ROOT_KEY_SIZE + CHAIN_KEY_SIZE

        private val INFO = "DoubleRatchetRootKey".toByteArray()
    }

    data class RootKeyStep(
        val newRootKey: RootKey,
        val chainKey: ChainKey
    )

    fun derive(dhOutput: ByteArray): RootKeyStep {

        // Safety check for invalid DH result
        require(dhOutput.isNotEmpty()) {
            "DH output cannot be empty"
        }

        val derived = HKDF.deriveKey(
            salt = key,
            ikm = dhOutput,
            info = INFO,
            length = DERIVED_KEY_SIZE
        )

        val newRootKeyBytes = derived.copyOfRange(0, ROOT_KEY_SIZE)
        val chainKeyBytes = derived.copyOfRange(ROOT_KEY_SIZE, DERIVED_KEY_SIZE)

        val newRootKey = RootKey(newRootKeyBytes)
        val chainKey = ChainKey(chainKeyBytes)

        return RootKeyStep(newRootKey, chainKey)
    }

    fun getKeyBytes(): ByteArray {
        return key.copyOf()
    }
}
