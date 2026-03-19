package com.hacksecure.p2p.security

import com.hacksecure.p2p.identity.KeyFingerprint
import java.security.PublicKey

object IdentityVerification {

    fun computeFingerprint(publicKey: PublicKey): String {
        return KeyFingerprint.generate(publicKey)
    }

    /**
     * Check if two public keys are the SAME key (same fingerprint).
     * Use case: detecting if a peer is re-presenting their own key.
     */
    fun areSameKey(keyA: PublicKey, keyB: PublicKey): Boolean {
        val fpA = computeFingerprint(keyA)
        val fpB = computeFingerprint(keyB)
        return KeyFingerprint.matches(keyA.encoded, keyB.encoded)
    }

    /**
     * Verify a remote peer's public key against a previously recorded fingerprint.
     * Use case: trust-on-first-use (TOFU) — check if the peer's identity
     * has changed since the last session.
     */
    fun verifyRemoteIdentity(remoteKey: PublicKey, expectedFingerprint: String): Boolean {
        val actualFingerprint = computeFingerprint(remoteKey)
        return actualFingerprint == expectedFingerprint
    }

    /**
     * @deprecated Use [verifyRemoteIdentity] instead. This method name is misleading.
     */
    @Deprecated("Use verifyRemoteIdentity()", ReplaceWith("verifyRemoteIdentity(remoteKey, expectedFingerprint)"))
    fun verifyFingerprint(remoteKey: PublicKey, expectedFingerprint: String): Boolean {
        return verifyRemoteIdentity(remoteKey, expectedFingerprint)
    }
}

