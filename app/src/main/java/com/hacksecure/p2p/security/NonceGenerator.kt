package com.sentinel.chat.crypto.utils

import com.sentinel.chat.crypto.random.SecureRandomProvider

object NonceGenerator {

    private const val GCM_NONCE_SIZE = 12


    fun generateGcmNonce(): ByteArray {

        val nonce = ByteArray(GCM_NONCE_SIZE)
        SecureRandomProvider.nextBytes(nonce)

        return nonce
    }


    fun generateRatchetNonce(): ByteArray {
        return generateGcmNonce()
    }
}