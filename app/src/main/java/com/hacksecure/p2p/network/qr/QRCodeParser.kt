package com.hacksecure.p2p.network.qr

import com.hacksecure.p2p.utils.Base64Utils

object QRCodeParser {

    data class IdentityQRData(
        val version: String,
        val userId: String,
        val identityKey: ByteArray,
        val ephemeralKey: ByteArray
    )

    fun parse(payload: String): IdentityQRData {

        require(payload.isNotBlank()) {
            "QR payload cannot be empty"
        }

        val parts = payload.split("|")

        require(parts.size == 4) {
            "Invalid QR format. Expected: v1|userId|identityKey|ephemeralKey"
        }

        val version = parts[0]
        val userId = parts[1]

        require(version == "v1") {
            "Unsupported QR version"
        }

        val identityKey = try {
            Base64Utils.decode(parts[2])
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid identity key encoding", e)
        }

        val ephemeralKey = try {
            Base64Utils.decode(parts[3])
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid ephemeral key encoding", e)
        }

        require(identityKey.isNotEmpty()) {
            "Identity key cannot be empty"
        }

        require(ephemeralKey.isNotEmpty()) {
            "Ephemeral key cannot be empty"
        }

        return IdentityQRData(
            version,
            userId,
            identityKey,
            ephemeralKey
        )
    }
}