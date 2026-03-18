package com.hacksecure.p2p.Protocol.Ratchet

import com.hacksecure.p2p.crypto.aes.AESGCMCipher
import com.hacksecure.p2p.crypto.dh.DiffieHellmanHandshake
import java.nio.ByteBuffer
import java.security.KeyPair
import java.security.PublicKey

class DoubleRatchet(

    private var rootKey: RootKey,

    private var sendingChainKey: ChainKey?,
    private var receivingChainKey: ChainKey?,

    private var dhKeyPair: KeyPair,
    private var remoteDhPublicKey: PublicKey
) {

    private var sendMessageNumber = 0
    private var receiveMessageNumber = 0
    private var previousChainLength = 0

    private val skippedMessageKeys =
        mutableMapOf<Pair<String, Int>, ByteArray>()

    private val MAX_SKIP = 2000

    data class Header(
        val dhPublicKey: ByteArray,
        val messageNumber: Int,
        val previousChainLength: Int
    )

    data class EncryptedMessage(
        val header: Header,
        val iv: ByteArray,
        val ciphertext: ByteArray
    )

    fun getRootKey(): RootKey = rootKey
    fun getSendingChainKey(): ChainKey? = sendingChainKey
    fun getReceivingChainKey(): ChainKey? = receivingChainKey
    fun getDhKeyPair(): KeyPair = dhKeyPair
    fun getRemoteDhPublicKey(): PublicKey = remoteDhPublicKey
    fun getSendMessageNumber(): Int = sendMessageNumber
    fun getReceiveMessageNumber(): Int = receiveMessageNumber
    fun getPreviousChainLength(): Int = previousChainLength

    fun encrypt(plaintext: ByteArray): EncryptedMessage {

        val chainKey = sendingChainKey
            ?: throw IllegalStateException("Sending chain not initialized")

        val step = chainKey.deriveMessageKey()

        sendingChainKey = step.nextChainKey

        val messageKey = step.messageKey

        val header = Header(
            dhPublicKey = dhKeyPair.public.encoded,
            messageNumber = sendMessageNumber,
            previousChainLength = previousChainLength
        )

        val aad = serializeHeader(header)

        val encrypted = AESGCMCipher.encrypt(
            key = messageKey,
            plaintext = plaintext,
            associatedData = aad
        )

        sendMessageNumber++

        return EncryptedMessage(
            header,
            encrypted.iv,
            encrypted.ciphertextWithTag
        )
    }

    fun decrypt(message: EncryptedMessage): ByteArray {

        val header = message.header

        val remoteKey = DiffieHellmanHandshake.decodePublicKey(header.dhPublicKey)

        val ratchetId = remoteKey.encoded.joinToString()

        val skippedKey = skippedMessageKeys.remove(Pair(ratchetId, header.messageNumber))

        if (skippedKey != null) {

            val aad = serializeHeader(header)

            return AESGCMCipher.decrypt(
                key = skippedKey,
                iv = message.iv,
                ciphertextWithTag = message.ciphertext,
                associatedData = aad
            )
        }

        if (!remoteKey.encoded.contentEquals(remoteDhPublicKey.encoded)) {

            performDhRatchet(remoteKey)
        }

        skipMessageKeys(header.messageNumber)

        val chainKey = receivingChainKey
            ?: throw IllegalStateException("Receiving chain not initialized")

        val step = chainKey.deriveMessageKey()

        receivingChainKey = step.nextChainKey

        val messageKey = step.messageKey

        receiveMessageNumber++

        val aad = serializeHeader(header)

        return AESGCMCipher.decrypt(
            key = messageKey,
            iv = message.iv,
            ciphertextWithTag = message.ciphertext,
            associatedData = aad
        )
    }

    private fun skipMessageKeys(until: Int) {

        if (receiveMessageNumber + MAX_SKIP < until) {
            throw IllegalStateException("Too many skipped messages")
        }

        while (receiveMessageNumber < until) {

            val chainKey = receivingChainKey
                ?: throw IllegalStateException("Receiving chain not initialized")

            val step = chainKey.deriveMessageKey()

            receivingChainKey = step.nextChainKey

            val ratchetId = remoteDhPublicKey.encoded.joinToString()

            skippedMessageKeys[Pair(ratchetId, receiveMessageNumber)] =
                step.messageKey

            receiveMessageNumber++
        }
    }

    private fun performDhRatchet(newRemoteKey: PublicKey) {

        previousChainLength = sendMessageNumber
        sendMessageNumber = 0
        receiveMessageNumber = 0

        val dh1 = DiffieHellmanHandshake.computeSharedSecretRaw(
            dhKeyPair.private,
            newRemoteKey
        )

        val rootStep1 = rootKey.derive(dh1)

        rootKey = rootStep1.newRootKey
        receivingChainKey = rootStep1.chainKey

        dhKeyPair = DiffieHellmanHandshake.generateEphemeralKeyPair()

        val dh2 = DiffieHellmanHandshake.computeSharedSecretRaw(
            dhKeyPair.private,
            newRemoteKey
        )

        val rootStep2 = rootKey.derive(dh2)

        rootKey = rootStep2.newRootKey
        sendingChainKey = rootStep2.chainKey

        remoteDhPublicKey = newRemoteKey
    }

    private fun serializeHeader(header: Header): ByteArray {

        val key = header.dhPublicKey

        val buffer = ByteBuffer.allocate(key.size + 8)

        buffer.put(key)
        buffer.putInt(header.messageNumber)
        buffer.putInt(header.previousChainLength)

        return buffer.array()
    }
}