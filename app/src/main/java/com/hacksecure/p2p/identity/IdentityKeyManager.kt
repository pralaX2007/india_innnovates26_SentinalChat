package com.hacksecure.p2p.identity

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PublicKey
import java.security.PrivateKey

object IdentityKeyManager {

    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val IDENTITY_KEY_ALIAS = "SentinelChatIdentityKey"


    fun initialize(context: Context) {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
        keyStore.load(null)

        if (!keyStore.containsAlias(IDENTITY_KEY_ALIAS)) {
            generateIdentityKey()
        }
    }


    private fun generateIdentityKey(): KeyPair {

        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            KEYSTORE_PROVIDER
        )

        val parameterSpec = KeyGenParameterSpec.Builder(
            IDENTITY_KEY_ALIAS,
            KeyProperties.PURPOSE_AGREE_KEY
        )
            .setAlgorithmParameterSpec(
                java.security.spec.ECGenParameterSpec("secp256r1")
            )
            .setDigests(KeyProperties.DIGEST_SHA256)
            .build()

        keyPairGenerator.initialize(parameterSpec)

        return keyPairGenerator.generateKeyPair()
    }


    fun getPublicKey(): PublicKey {

        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
        keyStore.load(null)

        val cert = keyStore.getCertificate(IDENTITY_KEY_ALIAS)
            ?: throw IllegalStateException("Identity key not initialized")

        return cert.publicKey
    }

    fun getPrivateKey(): PrivateKey {

        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
        keyStore.load(null)

        val entry = keyStore.getEntry(IDENTITY_KEY_ALIAS, null)
                as KeyStore.PrivateKeyEntry

        return entry.privateKey
    }


    fun getIdentityKeyPair(): KeyPair {
        return KeyPair(getPublicKey(), getPrivateKey())
    }


    fun getPublicKeyBytes(): ByteArray {
        return getPublicKey().encoded
    }
}