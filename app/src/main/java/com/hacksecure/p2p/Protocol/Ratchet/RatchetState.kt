package com.hacksecure.p2p.Protocol.Ratchet

import com.hacksecure.p2p.utils.Base64Utils
import java.security.KeyFactory
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

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

            val sendingChainKey = state.sendingChainKey?.let { ChainKey(it) }

            val receivingChainKey = state.receivingChainKey?.let { ChainKey(it) }

            return DoubleRatchet(
                rootKey = rootKey,
                sendingChainKey = sendingChainKey,
                receivingChainKey = receivingChainKey,
                dhKeyPair = keyPair,
                remoteDhPublicKey = remotePublicKey
            )
        }

        /**
         * Decode from DB map
         */
        fun decode(map: Map<String, String>): RatchetState {

            return RatchetState(
                rootKey = Base64Utils.decode(map["rootKey"]!!),
                sendingChainKey = map["sendingChainKey"]?.takeIf { it.isNotEmpty() }?.let { Base64Utils.decode(it) },
                receivingChainKey = map["receivingChainKey"]?.takeIf { it.isNotEmpty() }?.let { Base64Utils.decode(it) },
                dhPrivateKey = Base64Utils.decode(map["dhPrivateKey"]!!),
                dhPublicKey = Base64Utils.decode(map["dhPublicKey"]!!),
                remoteDhPublicKey = Base64Utils.decode(map["remoteDhPublicKey"]!!),
                sendMessageNumber = map["sendMessageNumber"]!!.toInt(),
                receiveMessageNumber = map["receiveMessageNumber"]!!.toInt(),
                previousChainLength = map["previousChainLength"]!!.toInt()
            )
        }
    }

    /**
     * Encode for DB storage
     */
    fun encode(): Map<String, String> {

        return mapOf(
            "rootKey" to Base64Utils.encode(rootKey),
            "sendingChainKey" to (sendingChainKey?.let { Base64Utils.encode(it) } ?: ""),
            "receivingChainKey" to (receivingChainKey?.let { Base64Utils.encode(it) } ?: ""),
            "dhPrivateKey" to Base64Utils.encode(dhPrivateKey),
            "dhPublicKey" to Base64Utils.encode(dhPublicKey),
            "remoteDhPublicKey" to Base64Utils.encode(remoteDhPublicKey),
            "sendMessageNumber" to sendMessageNumber.toString(),
            "receiveMessageNumber" to receiveMessageNumber.toString(),
            "previousChainLength" to previousChainLength.toString()
        )
    }
}