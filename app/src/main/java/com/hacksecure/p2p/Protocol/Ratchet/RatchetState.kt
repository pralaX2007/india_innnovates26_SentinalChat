package com.hacksecure.p2p.Protocol.Ratchet

import java.security.KeyFactory
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

data class RatchetState(

    val rootKey: ByteArray,

    val sendingChainKey: ByteArray?,

    val receivingChainKey: ByteArray?,

    val dhPrivateKey: ByteArray,

    val dhPublicKey: ByteArray,

    val remoteDhPublicKey: ByteArray,

    val sendMessageNumber: Int,

    val receiveMessageNumber: Int,

    val previousChainLength: Int
) {

    companion object {

        private const val KEY_ALGORITHM = "EC"

        /**
         * Build RatchetState from active DoubleRatchet engine
         */
        fun fromRatchet(ratchet: DoubleRatchet): RatchetState {

            return RatchetState(
                rootKey = ratchet.getRootKey().getKeyBytes(),
                sendingChainKey = ratchet.getSendingChainKey()?.getKeyBytes(),
                receivingChainKey = ratchet.getReceivingChainKey()?.getKeyBytes(),
                dhPrivateKey = ratchet.getDhKeyPair().private.encoded,
                dhPublicKey = ratchet.getDhKeyPair().public.encoded,
                remoteDhPublicKey = ratchet.getRemoteDhPublicKey().encoded,
                sendMessageNumber = ratchet.getSendMessageNumber(),
                receiveMessageNumber = ratchet.getReceiveMessageNumber(),
                previousChainLength = ratchet.getPreviousChainLength()
            )
        }

        /**
         * Restore DoubleRatchet engine from stored state
         */
        fun toRatchet(state: RatchetState): DoubleRatchet {

            val keyFactory = KeyFactory.getInstance(KEY_ALGORITHM)

            val privateKey: PrivateKey = keyFactory.generatePrivate(
                PKCS8EncodedKeySpec(state.dhPrivateKey)
            )

            val publicKey: PublicKey = keyFactory.generatePublic(
                X509EncodedKeySpec(state.dhPublicKey)
            )

            val remotePublicKey: PublicKey = keyFactory.generatePublic(
                X509EncodedKeySpec(state.remoteDhPublicKey)
            )

            val keyPair = KeyPair(publicKey, privateKey)

            val rootKey = RootKey(state.rootKey)

            val sendingChainKey = state.sendingChainKey?.let {
                ChainKey(it)
            }

            val receivingChainKey = state.receivingChainKey?.let {
                ChainKey(it)
            }

            return DoubleRatchet(
                rootKey = rootKey,
                sendingChainKey = sendingChainKey,
                receivingChainKey = receivingChainKey,
                dhKeyPair = keyPair,
                remoteDhPublicKey = remotePublicKey
            )
        }
    }

    /**
     * Encode state for database storage
     */
    fun encode(): Map<String, String> {

        return mapOf(
            "rootKey" to encode(rootKey),
            "sendingChainKey" to encodeNullable(sendingChainKey),
            "receivingChainKey" to encodeNullable(receivingChainKey),
            "dhPrivateKey" to encode(dhPrivateKey),
            "dhPublicKey" to encode(dhPublicKey),
            "remoteDhPublicKey" to encode(remoteDhPublicKey),
            "sendMessageNumber" to sendMessageNumber.toString(),
            "receiveMessageNumber" to receiveMessageNumber.toString(),
            "previousChainLength" to previousChainLength.toString()
        )
    }

    private fun encode(bytes: ByteArray): String {
        return Base64.getEncoder().encodeToString(bytes)
    }

    private fun encodeNullable(bytes: ByteArray?): String {
        return bytes?.let { encode(it) } ?: ""
    }
}