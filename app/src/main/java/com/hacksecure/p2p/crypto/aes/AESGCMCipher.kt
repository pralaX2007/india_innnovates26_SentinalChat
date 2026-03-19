package com.hacksecure.p2p.crypto.aes

import com.hacksecure.p2p.security.SecureRandomProvider
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object AESGCMCipher {

    private const val AES_MODE = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val IV_LENGTH = 12

    data class Ciphertext(
        val iv: ByteArray,
        val ciphertextWithTag: ByteArray
    )

    fun encrypt(
        key: ByteArray,
        plaintext: ByteArray,
        associatedData: ByteArray
    ): Ciphertext {
        require(key.size == 32) { "AES-256 requires a 32 byte key" }

        // Copy key so we don't wipe the caller's reference
        val keyCopy = key.copyOf()

        val iv = SecureRandomProvider.nextBytes(IV_LENGTH)
        val cipher = Cipher.getInstance(AES_MODE)
        val keySpec = SecretKeySpec(keyCopy, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)

        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        cipher.updateAAD(associatedData)
        val ciphertextWithTag = cipher.doFinal(plaintext)

        // Wipe only our local copy
        Arrays.fill(keyCopy, 0)

        return Ciphertext(iv, ciphertextWithTag)
    }

    fun decrypt(
        key: ByteArray,
        iv: ByteArray,
        ciphertextWithTag: ByteArray,
        associatedData: ByteArray
    ): ByteArray {
        require(key.size == 32) { "AES-256 requires a 32 byte key" }
        require(iv.size == IV_LENGTH) { "IV must be $IV_LENGTH bytes for GCM" }

        // Copy key so we don't wipe the caller's reference
        val keyCopy = key.copyOf()

        val cipher = Cipher.getInstance(AES_MODE)
        val keySpec = SecretKeySpec(keyCopy, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)

        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
        cipher.updateAAD(associatedData)
        val plaintext = cipher.doFinal(ciphertextWithTag)

        // Wipe only our local copy
        Arrays.fill(keyCopy, 0)

        return plaintext
    }
}
