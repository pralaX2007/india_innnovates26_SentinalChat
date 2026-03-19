package com.hacksecure.p2p.identity

import java.security.PrivateKey
import java.security.PublicKey

class IdentityKeyPair(
    val publicKey: PublicKey,
    private val privateKey: PrivateKey
) {
    val fingerprint: String = KeyFingerprint.generate(publicKey)

    fun getPrivateKey(): PrivateKey = privateKey

    fun exportPublicKey(): ByteArray = publicKey.encoded

    /**
     * NOTE: AndroidKeyStore private keys cannot be exported (.encoded returns null).
     * Only call this on software/ephemeral keys.
     */
    fun exportPrivateKeyUnsafe(): ByteArray? = privateKey.encoded

    override fun toString(): String = "IdentityKeyPair(fingerprint=$fingerprint)"
}
