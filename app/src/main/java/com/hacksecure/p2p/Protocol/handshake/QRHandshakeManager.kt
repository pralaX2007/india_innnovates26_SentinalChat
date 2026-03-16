package com.sentinel.chat.protocol.handshake

import com.sentinel.chat.crypto.dh.DiffieHellmanHandshake
import com.sentinel.chat.identity.IdentityKeyManager
import com.sentinel.chat.crypto.kdf.HKDF
import com.sentinel.chat.protocol.Ratchet.RootKey
import com.sentinel.chat.protocol.Ratchet.DoubleRatchet
import java.security.KeyPair
import java.security.PublicKey

object QRHandshake {

    data class HandshakeResult(
        val ratchet: DoubleRatchet,
        val remoteUserId: String
    )


    fun performHandshake(
        scannedPayload: String
    ): HandshakeResult {

        val parsed = QRCodeParser.parse(scannedPayload)

        val remoteUserId = parsed.userId

        val remoteIdentityKey: PublicKey =
            DiffieHellmanHandshake.decodePublicKey(parsed.identityPublicKey)

        val remoteEphemeralKey: PublicKey =
            DiffieHellmanHandshake.decodePublicKey(parsed.ephemeralPublicKey)

        val localIdentityKeyPair = IdentityKeyManager.getIdentityKeyPair()

        val localEphemeralKeyPair: KeyPair =
            DiffieHellmanHandshake.generateEphemeralKeyPair()

        // DH1 = IK_A × IK_B
        val dh1 = DiffieHellmanHandshake.computeSharedSecretRaw(
            localIdentityKeyPair.private,
            remoteIdentityKey
        )

        // DH2 = EK_A × EK_B
        val dh2 = DiffieHellmanHandshake.computeSharedSecretRaw(
            localEphemeralKeyPair.private,
            remoteEphemeralKey
        )

        // DH3 = EK_A × IK_B
        val dh3 = DiffieHellmanHandshake.computeSharedSecretRaw(
            localEphemeralKeyPair.private,
            remoteIdentityKey
        )

        val combinedSecret = concat(dh1, dh2, dh3)

        val masterSecret = HKDF.deriveKey(
            salt = null,
            ikm = combinedSecret,
            info = "SentinelMessenger_QRHandshake_v1".toByteArray(),
            length = 32
        )

        val rootKey = RootKey(masterSecret)

        val ratchet = DoubleRatchet(
            rootKey = rootKey,
            sendingChainKey = null,
            receivingChainKey = null,
            dhKeyPair = localEphemeralKeyPair,
            remoteDhPublicKey = remoteEphemeralKey
        )

        return HandshakeResult(
            ratchet = ratchet,
            remoteUserId = remoteUserId
        )
    }

   
    private fun concat(vararg arrays: ByteArray): ByteArray {

        var totalLength = 0
        for (array in arrays) {
            totalLength += array.size
        }

        val result = ByteArray(totalLength)

        var position = 0
        for (array in arrays) {
            System.arraycopy(array, 0, result, position, array.size)
            position += array.size
        }

        return result
    }
}