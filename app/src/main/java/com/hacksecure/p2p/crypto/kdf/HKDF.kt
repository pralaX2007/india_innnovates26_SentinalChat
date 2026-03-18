package com.hacksecure.p2p.crypto.kdf

import java.util.Arrays
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.ceil

object HKDF {

    private const val HMAC_ALGORITHM = "HmacSHA256"
    private const val HASH_LEN = 32

    // Extract step from RFC 5869
    fun extract(
        salt: ByteArray?,
        inputKeyMaterial: ByteArray
    ): ByteArray {

        val actualSalt = salt ?: ByteArray(HASH_LEN) { 0 }

        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(actualSalt, HMAC_ALGORITHM))

        val prk = mac.doFinal(inputKeyMaterial)

        return prk
    }


    fun expand(
        prk: ByteArray,
        info: ByteArray?,
        outputLength: Int
    ): ByteArray {

        require(prk.size == HASH_LEN) { "PRK must be $HASH_LEN bytes" }
        require(outputLength > 0) { "Output length must be > 0" }
        require(outputLength <= 255 * HASH_LEN) { "Output length too large" }

        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(prk, HMAC_ALGORITHM))

        val infoBytes = info ?: ByteArray(0)
        val n = ceil(outputLength.toDouble() / HASH_LEN).toInt()

        var previousBlock = ByteArray(0)
        val output = ByteArray(outputLength)

        var offset = 0

        for (i in 1..n) {

            mac.reset()

            mac.update(previousBlock)
            mac.update(infoBytes)
            mac.update(i.toByte())

            val block = mac.doFinal()

            val bytesToCopy = minOf(HASH_LEN, outputLength - offset)

            System.arraycopy(block, 0, output, offset, bytesToCopy)

            offset += bytesToCopy
            previousBlock = block
        }

        return output
    }

    fun deriveKey(
        salt: ByteArray?,
        ikm: ByteArray,
        info: ByteArray?,
        length: Int
    ): ByteArray {

        val prk = extract(salt, ikm)

        val okm = expand(prk, info, length)

        // Memory hygiene
        Arrays.fill(prk, 0)

        return okm
    }
}