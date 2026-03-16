package com.sentinel.chat.crypto.dh

import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement

object DiffieHellmanHandshake {

    private const val KEY_ALGORITHM = "EC"
    private const val KEY_AGREEMENT = "ECDH"
    private const val CURVE = "secp256r1"


    fun computeSharedSecretRaw(
        privateKey: PrivateKey,
        publicKey: PublicKey
    ): ByteArray {

        require(privateKey.algorithm == KEY_ALGORITHM) { "Invalid private key algorithm" }
        require(publicKey.algorithm == KEY_ALGORITHM) { "Invalid public key algorithm" }

        val keyAgreement = KeyAgreement.getInstance(KEY_AGREEMENT)

        keyAgreement.init(privateKey)
        keyAgreement.doPhase(publicKey, true)

        return keyAgreement.generateSecret()
    }


    fun generateEphemeralKeyPair(): KeyPair {

        val keyGen = KeyPairGenerator.getInstance(KEY_ALGORITHM)

        keyGen.initialize(ECGenParameterSpec(CURVE))

        return keyGen.generateKeyPair()
    }


    fun decodePublicKey(publicKeyBytes: ByteArray): PublicKey {

        require(publicKeyBytes.isNotEmpty()) { "Public key bytes cannot be empty" }

        val keyFactory = KeyFactory.getInstance(KEY_ALGORITHM)

        val keySpec = X509EncodedKeySpec(publicKeyBytes)

        val publicKey = keyFactory.generatePublic(keySpec)

        require(publicKey.algorithm == KEY_ALGORITHM) {
            "Invalid public key algorithm"
        }

        return publicKey
    }


    fun computeSharedSecretFromBytes(
        privateKey: PrivateKey,
        publicKeyBytes: ByteArray
    ): ByteArray {

        val publicKey = decodePublicKey(publicKeyBytes)

        return computeSharedSecretRaw(privateKey, publicKey)
    }
}