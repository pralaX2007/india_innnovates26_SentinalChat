package com.hacksecure.p2p.Protocol.Ratchet

import com.hacksecure.p2p.utils.Base64Utils
import java.security.KeyFactory
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

/**
 * Serializable snapshot of a DoubleRatchet state.
 *
 * NOTE: Only ephemeral (in-memory) EC keys can be serialized this way.
 * AndroidKeyStore-backed keys return null from getEncoded() — those
 * are identity keys and are never stored here; only ephemeral DH keys are.
 */
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
            val privateKeyBytes = ratchet.getDhKeyPair().private.encoded
                ?: throw IllegalStateException(
                    "Private key cannot be exported — ensure only ephemeral keys are stored in RatchetState"
                )
            return RatchetState(
                rootKey = ratchet.getRootKey().getKeyBytes(),
                sendingChainKey = ratchet.getSendingChainKey()?.getKeyBytes(),
                receivingChainKey = ratchet.getReceivingChainKey()?.getKeyBytes(),
                dhPrivateKey = privateKeyBytes,
                dhPublicKey = ratchet.getDhKeyPair().public.encoded,
                remoteDhPublicKey = ratchet.getRemoteDhPublicKey().encoded,
                sendMessageNumber = ratchet.getSendMessageNumber(),
                receiveMessageNumber = ratchet.getReceiveMessageNumber(),
                previousChainLength = ratchet.getPreviousChainLength()
            )
        }

        fun toRatchet(state: RatchetState): DoubleRatchet {
            val keyFactory = KeyFactory.getInstance(KEY_ALGORITHM)
            val privateKey: PrivateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(state.dhPrivateKey))
            val publicKey: PublicKey = keyFactory.generatePublic(X509EncodedKeySpec(state.dhPublicKey))
            val remotePublicKey: PublicKey = keyFactory.generatePublic(X509EncodedKeySpec(state.remoteDhPublicKey))

            return DoubleRatchet(
                rootKey = RootKey(state.rootKey),
                sendingChainKey = state.sendingChainKey?.let { ChainKey(it) },
                receivingChainKey = state.receivingChainKey?.let { ChainKey(it) },
                dhKeyPair = KeyPair(publicKey, privateKey),
                remoteDhPublicKey = remotePublicKey
            )
        }

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
