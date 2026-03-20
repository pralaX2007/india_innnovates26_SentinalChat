package com.hacksecure.p2p.Protocol.handshake

import android.graphics.Bitmap
import com.hacksecure.p2p.Protocol.Ratchet.DoubleRatchet
import com.hacksecure.p2p.crypto.dh.DiffieHellmanHandshake
import com.hacksecure.p2p.identity.IdentityKeyManager
import com.hacksecure.p2p.identity.KeyFingerprint
import com.hacksecure.p2p.network.qr.QRCodeParser
import com.hacksecure.p2p.ui.connection.QRCodeGenerator
import java.security.KeyPair

object HandshakeManager {

    data class HandshakeSetup(
        val qrPayload: String,
        val qrBitmap: Bitmap,
        val localEphemeralKeyPair: KeyPair
    )

    data class HandshakeResult(
        val ratchet: DoubleRatchet,
        val peerId: String,
        val peerIdentityKeyBytes: ByteArray,
        val peerFingerprint: String
    )

    fun prepareQR(selfId: String): HandshakeSetup {
        val identityKey = IdentityKeyManager.getPublicKeyBytes()
        val ephemeralKeyPair = DiffieHellmanHandshake.generateEphemeralKeyPair()
        val ephemeralKey = ephemeralKeyPair.public.encoded
        val payload = QRCodeGenerator.createIdentityPayload(selfId, identityKey, ephemeralKey)
        val bitmap = QRCodeGenerator.generateQRCode(payload)
        return HandshakeSetup(payload, bitmap, ephemeralKeyPair)
    }

    fun executeAsInitiator(scannedPayload: String): HandshakeResult {
        val result = QRHandshake.performHandshake(scannedPayload)
        val parsed = QRCodeParser.parse(scannedPayload)
        val fingerprint = KeyFingerprint.generate(parsed.identityKey)
        return HandshakeResult(
            ratchet = result.ratchet,
            peerId = result.remoteUserId,
            peerIdentityKeyBytes = parsed.identityKey,
            peerFingerprint = fingerprint
        )
    }

    fun executeAsResponder(
        scannedPayload: String,
        localEphemeralKeyPair: KeyPair
    ): HandshakeResult {
        val result = QRHandshake.performHandshakeAsResponder(scannedPayload, localEphemeralKeyPair)
        val parsed = QRCodeParser.parse(scannedPayload)
        val fingerprint = KeyFingerprint.generate(parsed.identityKey)
        return HandshakeResult(
            ratchet = result.ratchet,
            peerId = result.remoteUserId,
            peerIdentityKeyBytes = parsed.identityKey,
            peerFingerprint = fingerprint
        )
    }
}
