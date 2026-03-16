package com.sentinel.chat.crypto.kdf

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.ceil

object HKDF{
    private const val HMAC_ALGORITHM = "HmacSHA256"
    private const val HASH_LEN =32

    //Extraction from shared secret
    fun extract(salt : ByteArray? ,inputKeyMaterial : ByteArray) : ByteArray
    {
        val actualSalt = salt ?: ByteArray(HASH_LEN){0}
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(actualSalt, HMAC_ALGORITHM))

        return mac.doFinal(inputKeyMaterial)
    }

    //Expansion of random input
    fun expand (prk : ByteArray, info : ByteArray?,outputLength : Int) : ByteArray
    {
        require(prk.size >= HASH_LEN){"PRK length must be >= $HASH_LEN"}
        require(outputLength > 0) { "Output  length must be > 0"}
        require(outputLength <= 255 * HASH_LEN) {"Output Length too large"}

        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(prk,HMAC_ALGORITHM))

        val infoBytes = info ?: ByteArray(0)
        val n =  ceil(outputLength.toDouble() / HASH_LEN).toInt()

        var previousBlock = ByteArray(0)
        val output = ByteArray(outputLength)

        var offset =0
        for(i in 1..n)
        {
            mac.reset()

            mac.update(previousBlock)
            mac.update(infoBytes)
            mac.update(i.toByte())

            //HMAc calculation done here
            val block = mac.doFinal()
            val bytestoCopy = minOf(HASH_LEN,outputLength - offset)

            System.arraycopy(block,0,output,offset,bytestoCopy)
            offset += bytestoCopy
            previousBlock = block
        }
        return output
    }

    fun deriveKey (salt : ByteArray? ,ikm : ByteArray , info : ByteArray? , length: Int) : ByteArray
    {
        val prk = extract(salt,ikm)

        return expand(prk , info , length )
    }
}