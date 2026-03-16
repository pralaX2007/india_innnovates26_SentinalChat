package com.sentinel.chat.crypto.aes

import com.sentinel.chat.crypto.utils.SecureRandomProvider
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object AESGCMCipher {

    private const val AES_MODE = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val IV_LENGTH = 12   // 96 bits (recommended for GCM)

    data class Ciphertext(
        val iv: ByteArray,
        val ciphertext: ByteArray
    )


    fun encrypt(
        key: ByteArray,
        plaintext: ByteArray,
        associatedData: ByteArray? = null
    ): Ciphertext {

        require(key.size == 32) { "AES-256 requires a 32 byte key" }

        val iv = ByteArray(IV_LENGTH)
        SecureRandomProvider.nextBytes(IV_LENGTH).copyInto(iv)

        val cipher = Cipher.getInstance(AES_MODE)

        val keySpec = SecretKeySpec(key, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)

        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)

        if (associatedData != null) {
            cipher.updateAAD(associatedData)
        }

        val ciphertext = cipher.doFinal(plaintext)

        return Ciphertext(iv, ciphertext)
    }


    fun decrypt(
        key: ByteArray,
        iv: ByteArray,
        ciphertext: ByteArray,
        associatedData: ByteArray? = null
    ): ByteArray {

        require(key.size == 32) { "AES-256 requires a 32 byte key" }
        require(iv.size == IV_LENGTH) { "IV must be $IV_LENGTH bytes for GCM" }

        val cipher = Cipher.getInstance(AES_MODE)

        val keySpec = SecretKeySpec(key, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)

        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)

        if (associatedData != null) {
            cipher.updateAAD(associatedData)
        }

        return cipher.doFinal(ciphertext)
    }
}