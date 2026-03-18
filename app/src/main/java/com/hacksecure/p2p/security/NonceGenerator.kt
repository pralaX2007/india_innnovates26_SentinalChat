package com.hacksecure.p2p.security

object NonceGenerator {

    private const val GCM_NONCE_SIZE = 12


    fun generateGcmNonce(): ByteArray {

        return SecureRandomProvider.nextBytes(GCM_NONCE_SIZE)
    }


    fun generateRatchetNonce(): ByteArray {
        return generateGcmNonce()
    }
}