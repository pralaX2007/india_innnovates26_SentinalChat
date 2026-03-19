package com.hacksecure.p2p.identity

import java.security.MessageDigest
import java.security.PublicKey
import java.util.Locale

object KeyFingerprint {

    private const val HASH_ALGORITHM = "SHA-256"

    fun generate(publicKey: PublicKey): String {
        return generate(publicKey.encoded)
    }

    fun generate(publicKeyBytes: ByteArray): String {
        val digest = MessageDigest.getInstance(HASH_ALGORITHM)
        val hash = digest.digest(publicKeyBytes)
        return formatFingerprint(hash)
    }

    private fun formatFingerprint(hash: ByteArray): String {
        val hex = hash.joinToString("") { "%02X".format(it) }
        return hex.chunked(4).joinToString("-")
    }

    fun matches(publicKeyA: ByteArray, publicKeyB: ByteArray): Boolean {
        val fpA = generate(publicKeyA)
        val fpB = generate(publicKeyB)
        return constantTimeEquals(fpA, fpB)
    }

    /**
     * Constant-time comparison — pads shorter string to avoid length timing leak.
     */
    private fun constantTimeEquals(a: String, b: String): Boolean {
        val maxLen = maxOf(a.length, b.length)
        var result = a.length xor b.length  // encodes length difference without branching
        for (i in 0 until maxLen) {
            val ca = if (i < a.length) a[i].code else 0
            val cb = if (i < b.length) b[i].code else 0
            result = result or (ca xor cb)
        }
        return result == 0
    }
}
