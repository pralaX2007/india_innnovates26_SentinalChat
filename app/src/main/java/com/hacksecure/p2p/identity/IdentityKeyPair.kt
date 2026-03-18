package com.hacksecure.p2p.identity

import java.security.PrivateKey
import java.security.PublicKey


class IdentityKeyPair(

    val publicKey: PublicKey,

    private val privateKey: PrivateKey

) {


    val fingerprint: String = KeyFingerprint.generate(publicKey)


    fun getPrivateKey(): PrivateKey {
        return privateKey
    }


    fun exportPublicKey(): ByteArray {
        return publicKey.encoded
    }


    fun exportPrivateKey(): ByteArray {
        return privateKey.encoded
    }

    override fun toString(): String {
        return "IdentityKeyPair(fingerprint=$fingerprint)"
    }
}