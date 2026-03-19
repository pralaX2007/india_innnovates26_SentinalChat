package com.hacksecure.p2p.identity

import android.content.Context
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.PrivateKey
import java.security.KeyStore
import java.security.spec.ECGenParameterSpec

object IdentityKeyManager {

    private const val KEY_ALIAS = "P2P_IDENTITY_KEY"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    private lateinit var keyStore: KeyStore

    /**
     * MUST be called once at app start
     */
    fun initialize(context: Context) {

        keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        if (!keyStore.containsAlias(KEY_ALIAS)) {
            generateIdentityKey()
        }
    }

    /**
     * Generate long-term identity key
     */
    private fun generateIdentityKey() {

        val keyPairGenerator = KeyPairGenerator.getInstance(
            "EC",
            ANDROID_KEYSTORE
        )

        val spec = android.security.keystore.KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            android.security.keystore.KeyProperties.PURPOSE_AGREE_KEY
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(android.security.keystore.KeyProperties.DIGEST_SHA256)
            .build()

        keyPairGenerator.initialize(spec)

        keyPairGenerator.generateKeyPair()
    }

    /**
     * Get identity key pair (wrapped)
     */
    fun getIdentityKeyPair(): IdentityKeyPair {

        val privateKey = keyStore.getKey(KEY_ALIAS, null) as PrivateKey
        val publicKey = keyStore.getCertificate(KEY_ALIAS).publicKey

        return IdentityKeyPair(
            publicKey = publicKey,
            privateKey = privateKey
        )
    }

    /**
     * Get only public key (for QR / sharing)
     */
    fun getPublicKey(): PublicKey {
        return keyStore.getCertificate(KEY_ALIAS).publicKey
    }

    /**
     * Export public key as raw bytes (for QR)
     */
    fun getPublicKeyBytes(): ByteArray {
        return getPublicKey().encoded
    }

    /**
     * Get fingerprint for verification
     */
    fun getFingerprint(): String {
        return getIdentityKeyPair().fingerprint
    }

    /**
     * Delete identity (for logout/reset)
     */
    fun clearIdentity() {
        if (keyStore.containsAlias(KEY_ALIAS)) {
            keyStore.deleteEntry(KEY_ALIAS)
        }
    }
}