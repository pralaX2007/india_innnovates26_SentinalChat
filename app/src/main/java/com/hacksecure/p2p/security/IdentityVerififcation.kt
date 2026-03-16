package com.sentinel.chat.crypto.identity

import java.security.PublicKey

object IdentityVerification {


    fun computeFingerprint(publicKey: PublicKey): String {

        return KeyFingerprint.generate(publicKey)
    }

    fun verifyKeys(localKey: PublicKey, remoteKey: PublicKey): Boolean {

        val localFingerprint = computeFingerprint(localKey)
        val remoteFingerprint = computeFingerprint(remoteKey)

        return localFingerprint == remoteFingerprint
    }


    fun verifyFingerprint(
        remoteKey: PublicKey,
        expectedFingerprint: String
    ): Boolean {

        val actualFingerprint = computeFingerprint(remoteKey)

        return actualFingerprint == expectedFingerprint
    }
}