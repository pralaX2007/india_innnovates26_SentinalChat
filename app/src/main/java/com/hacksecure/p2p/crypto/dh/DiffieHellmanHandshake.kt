package com.sentinel.chat.crypto.dh

import java.security.KeyFactory
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement

object DiffieHellmanHandshake {

    private const val KEY_ALGORITHM = "EC"
    private const val KEY_AGREEMENT = "ECDH"


    fun computeSharedSecret(
        privateKey: PrivateKey,
        publicKey: PublicKey
    ): ByteArray {

        val keyAgreement = KeyAgreement.getInstance(KEY_AGREEMENT)

        keyAgreement.init(privateKey)
        keyAgreement.doPhase(publicKey, true)

        return keyAgreement.generateSecret()
    }

    fun generateEphemeralKeyPair(): KeyPair {
        val keyGen = KeyPairGenerator.getInstance("EC")
        keyGen.initialize(ECGenParameterSpec("secp256r1"))
        return keyGen.generateKeyPair()
    }
    fun decodePublicKey(publicKeyBytes: ByteArray): PublicKey {

        val keyFactory = KeyFactory.getInstance(KEY_ALGORITHM)

        val keySpec = X509EncodedKeySpec(publicKeyBytes)

        return keyFactory.generatePublic(keySpec)
    }


    fun computeSharedSecretFromBytes(
        privateKey: PrivateKey,
        publicKeyBytes: ByteArray
    ): ByteArray {

        val publicKey = decodePublicKey(publicKeyBytes)

        return computeSharedSecret(privateKey, publicKey)
    }
}