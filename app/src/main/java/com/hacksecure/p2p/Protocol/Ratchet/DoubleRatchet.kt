package com.sentinel.chat.crypto.ratchet

import com.sentinel.chat.crypto.aead.AESGCMCipher
import com.sentinel.chat.crypto.dh.DiffieHellmanHandshake
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
            encrypted.ciphertext
        )
    }


    fun decrypt(message: EncryptedMessage): ByteArray {

        val header = message.header

        val remoteKey = DiffieHellmanHandshake.decodePublicKey(header.dhPublicKey)

        if (!remoteKey.equals(remoteDhPublicKey)) {

            performDhRatchet(remoteKey)
        }

        val chainKey = receivingChainKey
            ?: throw IllegalStateException("Receiving chain not initialized")

        val step = chainKey.deriveMessageKey()

        receivingChainKey = step.nextChainKey

        val messageKey = step.messageKey

        val aad = serializeHeader(header)

        val plaintext = AESGCMCipher.decrypt(
            key = messageKey,
            iv = message.iv,
            ciphertext = message.ciphertext,
            associatedData = aad
        )

        receiveMessageNumber++

        return plaintext
    }


    private fun performDhRatchet(newRemoteKey: PublicKey) {

        previousChainLength = sendMessageNumber
        sendMessageNumber = 0
        receiveMessageNumber = 0

        val dh1 = DiffieHellmanHandshake.computeSharedSecret(
            dhKeyPair.private,
            newRemoteKey
        )

        val rootStep1 = rootKey.derive(dh1)

        rootKey = rootStep1.newRootKey
        receivingChainKey = rootStep1.chainKey

        dhKeyPair = DiffieHellmanHandshake.generateEphemeralKeyPair()

        val dh2 = DiffieHellmanHandshake.computeSharedSecret(
            dhKeyPair.private,
            newRemoteKey
        )

        val rootStep2 = rootKey.derive(dh2)

        rootKey = rootStep2.newRootKey
        sendingChainKey = rootStep2.chainKey

        remoteDhPublicKey = newRemoteKey
    }


    private fun serializeHeader(header: Header): ByteArray {

        val buffer = mutableListOf<Byte>()

        buffer.addAll(header.dhPublicKey.toList())

        buffer.addAll(intToBytes(header.messageNumber).toList())

        buffer.addAll(intToBytes(header.previousChainLength).toList())

        return buffer.toByteArray()
    }

    private fun intToBytes(value: Int): ByteArray {

        return byteArrayOf(
            (value shr 24).toByte(),
            (value shr 16).toByte(),
            (value shr 8).toByte(),
            value.toByte()
        )
    }
}