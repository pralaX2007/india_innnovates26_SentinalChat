package com.sentinel.chat.network.qr

import java.util.Base64

object QRCodeParser {

    data class IdentityQRData(
        val userId: String,
        val publicKey: ByteArray
    )

    fun parse(payload: String): IdentityQRData {

        val parts = payload.split(":")

        require(parts.size == 2) { "Invalid QR payload" }

        val userId = parts[0]

        val publicKey = Base64.getDecoder().decode(parts[1])

        return IdentityQRData(userId, publicKey)
    }
}