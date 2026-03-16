package com.sentinel.chat.network.qr

import com.sentinel.chat.crypto.dh.DiffieHellmanHandshake
import com.sentinel.chat.crypto.identity.IdentityKeyManager
import com.sentinel.chat.crypto.kdf.HKDF
import com.sentinel.chat.crypto.ratchet.RootKey
import com.sentinel.chat.crypto.ratchet.DoubleRatchet
import java.security.KeyPair
import java.security.PublicKey

object QRHandshake {

    data class HandshakeResult(
        val ratchet: DoubleRatchet,
        val remoteUserId: String
    )

    /**
     * Perform QR-based handshake and initialize Double Ratchet
     */
    fun performHandshake(
        scannedPayload: String
    ): HandshakeResult {

        val parsed = QRCodeParser.parse(scannedPayload)

        val remoteUserId = parsed.userId
        val remoteIdentityKeyBytes = parsed.publicKey

        val remoteIdentityKey: PublicKey =
            DiffieHellmanHandshake.decodePublicKey(remoteIdentityKeyBytes)

        val localIdentityKeyPair = IdentityKeyManager.getIdentityKeyPair()

        val ephemeralKeyPair: KeyPair =
            DiffieHellmanHandshake.generateEphemeralKeyPair()

        val dh1 = DiffieHellmanHandshake.computeSharedSecret(
            localIdentityKeyPair.private,
            remoteIdentityKey
        )

        val dh2 = DiffieHellmanHandshake.computeSharedSecret(
            ephemeralKeyPair.private,
            remoteIdentityKey
        )

        val masterSecret = HKDF.deriveKey(
            salt = null,
            ikm = dh1 + dh2,
            info = "QRHandshakeMasterSecret".toByteArray(),
            length = 32
        )

        val rootKey = RootKey(masterSecret)

        val ratchet = DoubleRatchet(
            rootKey = rootKey,
            sendingChainKey = null,
            receivingChainKey = null,
            dhKeyPair = ephemeralKeyPair,
            remoteDhPublicKey = remoteIdentityKey
        )

        return HandshakeResult(
            ratchet = ratchet,
            remoteUserId = remoteUserId
        )
    }

    /**
     * Utility operator for concatenating byte arrays
     */
    private operator fun ByteArray.plus(other: ByteArray): ByteArray {
        val result = ByteArray(this.size + other.size)
        System.arraycopy(this, 0, result, 0, this.size)
        System.arraycopy(other, 0, result, this.size, other.size)
        return result
    }
}