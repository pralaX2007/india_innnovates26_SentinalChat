package com.sentinel.chat.crypto.identity

import java.security.MessageDigest
import java.security.PublicKey
import java.util.Locale

object KeyFingerprint {

    private const val HASH_ALGORITHM = "SHA-256"

    /**
     * Generate fingerprint from PublicKey
     */
    fun generate(publicKey: PublicKey): String {
        return generate(publicKey.encoded)
    }

    /**
     * Generate fingerprint from raw key bytes
     */
    fun generate(publicKeyBytes: ByteArray): String {

        val digest = MessageDigest.getInstance(HASH_ALGORITHM)

        val hash = digest.digest(publicKeyBytes)

        return formatFingerprint(hash)
    }

    /**
     * Format fingerprint into human readable groups
     * Example:
     * 3FA9-88D2-7C4E-91AA-FF03-8D12-09AC-77B1
     */
    private fun formatFingerprint(hash: ByteArray): String {

        val hex = hash.joinToString("") {
            "%02X".format(Locale.US, it)
        }

        return hex.chunked(4).joinToString("-")
    }

    /**
     * Compare two fingerprints safely
     */
    fun matches(
        publicKeyA: ByteArray,
        publicKeyB: ByteArray
    ): Boolean {

        val fpA = generate(publicKeyA)
        val fpB = generate(publicKeyB)

        return constantTimeEquals(fpA, fpB)
    }

    /**
     * Constant-time comparison to avoid timing leaks
     */
    private fun constantTimeEquals(a: String, b: String): Boolean {

        if (a.length != b.length) return false

        var result = 0

        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }

        return result == 0
    }
}