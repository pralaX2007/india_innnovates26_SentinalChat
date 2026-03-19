package com.hacksecure.p2p.Protocol.handshake

import com.hacksecure.p2p.crypto.dh.DiffieHellmanHandshake
import com.hacksecure.p2p.identity.IdentityKeyManager
import com.hacksecure.p2p.crypto.kdf.HKDF
import com.hacksecure.p2p.Protocol.Ratchet.RootKey
import com.hacksecure.p2p.Protocol.Ratchet.DoubleRatchet
import com.hacksecure.p2p.network.qr.QRCodeParser
import com.hacksecure.p2p.utils.Byte64Utils
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
            DiffieHellmanHandshake.decodePublicKey(parsed.identityKey)

        val remoteEphemeralKey: PublicKey =
            DiffieHellmanHandshake.decodePublicKey(parsed.ephemeralKey)

        val localIdentityKeyPair = IdentityKeyManager.getIdentityKeyPair()

        val localEphemeralKeyPair: KeyPair =
            DiffieHellmanHandshake.generateEphemeralKeyPair()

        // ✅ DH1 = IK_A × IK_B
        val dh1 = DiffieHellmanHandshake.computeSharedSecretRaw(
            localIdentityKeyPair.private,
            remoteIdentityKey
        )

        // ✅ DH2 = EK_A × EK_B
        val dh2 = DiffieHellmanHandshake.computeSharedSecretRaw(
            localEphemeralKeyPair.private,
            remoteEphemeralKey
        )

        // ✅ DH3 = EK_A × IK_B
        val dh3 = DiffieHellmanHandshake.computeSharedSecretRaw(
            localEphemeralKeyPair.private,
            remoteIdentityKey
        )

        // ✅ Combine secrets safely
        val combinedSecret = ByteUtils.concat(dh1, dh2, dh3)

        // ✅ Derive master secret
        val masterSecret = HKDF.deriveKey(
            salt = null,
            ikm = combinedSecret,
            info = "SentinelMessenger_QRHandshake_v1".toByteArray(),
            length = 32
        )

        val rootKey = RootKey(masterSecret)

        // ✅ INITIAL CHAIN KEY DERIVATION (CRITICAL FIX)
        val initialDh = DiffieHellmanHandshake.computeSharedSecretRaw(
            localEphemeralKeyPair.private,
            remoteEphemeralKey
        )

        val rootStep = rootKey.derive(initialDh)

        val ratchet = DoubleRatchet(
            rootKey = rootStep.newRootKey,
            sendingChainKey = rootStep.chainKey,
            receivingChainKey = null,
            dhKeyPair = localEphemeralKeyPair,
            remoteDhPublicKey = remoteEphemeralKey
        )

        return HandshakeResult(
            ratchet = ratchet,
            remoteUserId = remoteUserId
        )
    }
}