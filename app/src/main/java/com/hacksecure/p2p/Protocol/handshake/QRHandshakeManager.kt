package com.hacksecure.p2p.Protocol.handshake

import com.hacksecure.p2p.crypto.dh.DiffieHellmanHandshake
import com.hacksecure.p2p.identity.IdentityKeyManager
import com.hacksecure.p2p.crypto.kdf.HKDF
import com.hacksecure.p2p.Protocol.Ratchet.RootKey
import com.hacksecure.p2p.Protocol.Ratchet.DoubleRatchet
import com.hacksecure.p2p.network.qr.QRCodeParser
import com.hacksecure.p2p.utils.ByteUtils
import com.hacksecure.p2p.security.MemoryCleaner
import java.security.KeyPair
import java.security.PublicKey

object QRHandshake {

    data class HandshakeResult(
        val ratchet: DoubleRatchet,
        val remoteUserId: String
    )

    /**
     * Perform handshake as the INITIATOR (the device that SCANNED the peer's QR code).
     *
     * DH computations as initiator:
     *   DH1 = IK_local × IK_remote
     *   DH2 = EK_local × EK_remote
     *   DH3 = EK_local × IK_remote   (initiator uses their EK against remote IK)
     *
     * The initiator gets the sending chain key; receiving chain is null until
     * the first DH ratchet step from the responder.
     */
    fun performHandshake(scannedPayload: String): HandshakeResult {

        val parsed = QRCodeParser.parse(scannedPayload)
        val remoteUserId = parsed.userId

        val remoteIdentityKey: PublicKey =
            DiffieHellmanHandshake.decodePublicKey(parsed.identityKey)

        val remoteEphemeralKey: PublicKey =
            DiffieHellmanHandshake.decodePublicKey(parsed.ephemeralKey)

        val localIdentityKeyPair = IdentityKeyManager.getIdentityKeyPair()

        val localEphemeralKeyPair: KeyPair =
            DiffieHellmanHandshake.generateEphemeralKeyPair()

        // DH1 = IK_A × IK_B
        val dh1 = DiffieHellmanHandshake.computeSharedSecretRaw(
            localIdentityKeyPair.getPrivateKey(),
            remoteIdentityKey
        )

        // DH2 = EK_A × EK_B
        val dh2 = DiffieHellmanHandshake.computeSharedSecretRaw(
            localEphemeralKeyPair.private,
            remoteEphemeralKey
        )

        // DH3 = EK_A × IK_B  (initiator: local EK against remote IK)
        val dh3 = DiffieHellmanHandshake.computeSharedSecretRaw(
            localEphemeralKeyPair.private,
            remoteIdentityKey
        )

        return finalizeHandshake(
            dh1, dh2, dh3,
            localEphemeralKeyPair,
            remoteEphemeralKey,
            remoteUserId,
            isInitiator = true
        )
    }

    /**
     * Perform handshake as the RESPONDER (the device that DISPLAYED its QR code).
     *
     * The responder must use its own ephemeral key pair (the one whose public key
     * was encoded into the QR code) for the DH computations.
     *
     * DH computations as responder:
     *   DH1 = IK_local × IK_remote        (same shared secret by ECDH commutativity)
     *   DH2 = EK_local × EK_remote        (same)
     *   DH3 = IK_local × EK_remote        (inverse of initiator's DH3)
     *
     * The responder gets the receiving chain key; sending chain is null until
     * the first DH ratchet step from the initiator side.
     */
    fun performHandshakeAsResponder(
        scannedPayload: String,
        localEphemeralKeyPair: KeyPair
    ): HandshakeResult {

        val parsed = QRCodeParser.parse(scannedPayload)
        val remoteUserId = parsed.userId

        val remoteIdentityKey: PublicKey =
            DiffieHellmanHandshake.decodePublicKey(parsed.identityKey)

        val remoteEphemeralKey: PublicKey =
            DiffieHellmanHandshake.decodePublicKey(parsed.ephemeralKey)

        val localIdentityKeyPair = IdentityKeyManager.getIdentityKeyPair()

        // DH1 = IK_B × IK_A  (same shared secret by commutativity)
        val dh1 = DiffieHellmanHandshake.computeSharedSecretRaw(
            localIdentityKeyPair.getPrivateKey(),
            remoteIdentityKey
        )

        // DH2 = EK_B × EK_A  (same)
        val dh2 = DiffieHellmanHandshake.computeSharedSecretRaw(
            localEphemeralKeyPair.private,
            remoteEphemeralKey
        )

        // DH3 = IK_B × EK_A  (inverse of initiator's DH3: IK_local against remote EK)
        val dh3 = DiffieHellmanHandshake.computeSharedSecretRaw(
            localIdentityKeyPair.getPrivateKey(),
            remoteEphemeralKey
        )

        return finalizeHandshake(
            dh1, dh2, dh3,
            localEphemeralKeyPair,
            remoteEphemeralKey,
            remoteUserId,
            isInitiator = false
        )
    }

    /**
     * Shared finalization: derive master secret, create root key, set up ratchet.
     *
     * @param isInitiator determines which chain key is assigned as sending vs receiving
     */
    private fun finalizeHandshake(
        dh1: ByteArray,
        dh2: ByteArray,
        dh3: ByteArray,
        localEphemeralKeyPair: KeyPair,
        remoteEphemeralKey: PublicKey,
        remoteUserId: String,
        isInitiator: Boolean
    ): HandshakeResult {
        // Combine secrets
        val combinedSecret = ByteUtils.concat(dh1, dh2, dh3)

        // Wipe intermediate DH results
        MemoryCleaner.wipeAll(dh1, dh2, dh3)

        // Derive master secret via HKDF
        val masterSecret = HKDF.deriveKey(
            salt = null,
            ikm = combinedSecret,
            info = "SentinelMessenger_QRHandshake_v1".toByteArray(),
            length = 32
        )

        // Wipe combined secret
        MemoryCleaner.wipe(combinedSecret)

        val rootKey = RootKey(masterSecret)

        // Initial chain key derivation
        val initialDh = DiffieHellmanHandshake.computeSharedSecretRaw(
            localEphemeralKeyPair.private,
            remoteEphemeralKey
        )

        val rootStep = rootKey.derive(initialDh)

        // Wipe DH output
        MemoryCleaner.wipe(initialDh)

        // Initiator gets sending chain; responder gets receiving chain
        val ratchet = if (isInitiator) {
            DoubleRatchet(
                rootKey = rootStep.newRootKey,
                sendingChainKey = rootStep.chainKey,
                receivingChainKey = null,
                dhKeyPair = localEphemeralKeyPair,
                remoteDhPublicKey = remoteEphemeralKey
            )
        } else {
            DoubleRatchet(
                rootKey = rootStep.newRootKey,
                sendingChainKey = null,
                receivingChainKey = rootStep.chainKey,
                dhKeyPair = localEphemeralKeyPair,
                remoteDhPublicKey = remoteEphemeralKey
            )
        }

        return HandshakeResult(
            ratchet = ratchet,
            remoteUserId = remoteUserId
        )
    }
}
