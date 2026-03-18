package com.hacksecure.p2p.security

import com.hacksecure.p2p.identity.KeyFingerprint
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