package com.hacksecure.p2p.identity

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec

object IdentityKeyManager {

    private const val KEY_ALIAS = "P2P_IDENTITY_KEY"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    private var keyStore: KeyStore? = null

    /**
     * Must be called once at app start (in Application.onCreate).
     */
    fun initialize(context: Context) {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE)
        ks.load(null)
        keyStore = ks
        if (!ks.containsAlias(KEY_ALIAS)) {
            generateIdentityKey()
        }
    }

    private fun generateIdentityKey() {
        val keyPairGenerator = KeyPairGenerator.getInstance("EC", ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_AGREE_KEY
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .build()
        keyPairGenerator.initialize(spec)
        keyPairGenerator.generateKeyPair()
    }

    private fun requireKeyStore(): KeyStore =
        keyStore ?: throw IllegalStateException(
            "IdentityKeyManager not initialized. Call initialize(context) in Application.onCreate()."
        )

    fun getIdentityKeyPair(): IdentityKeyPair {
        val ks = requireKeyStore()
        val privateKey = ks.getKey(KEY_ALIAS, null) as PrivateKey
        val publicKey  = ks.getCertificate(KEY_ALIAS).publicKey
        return IdentityKeyPair(publicKey = publicKey, privateKey = privateKey)
    }

    fun getPublicKey(): PublicKey {
        return requireKeyStore().getCertificate(KEY_ALIAS).publicKey
    }

    fun getPublicKeyBytes(): ByteArray {
        return getPublicKey().encoded
    }

    fun getFingerprint(): String {
        return getIdentityKeyPair().fingerprint
    }

    fun clearIdentity() {
        val ks = requireKeyStore()
        if (ks.containsAlias(KEY_ALIAS)) {
            ks.deleteEntry(KEY_ALIAS)
        }
    }
}
