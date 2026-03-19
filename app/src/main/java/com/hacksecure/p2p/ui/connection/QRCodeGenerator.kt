package com.hacksecure.p2p.ui.connection

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.hacksecure.p2p.utils.Base64Utils

object QRCodeGenerator {

    private const val QR_SIZE = 600
    private const val VERSION = "v1"

    /**
     * Convert identity public key to a QR payload string
     */
    fun createIdentityPayload(
        userId: String,
        identityKey: ByteArray,
        ephemeralKey: ByteArray
    ): String {

        val encodedIdentity = Base64Utils.encode(identityKey)
        val encodedEphemeral = Base64Utils.encode(ephemeralKey)

        return "$VERSION|$userId|$encodedIdentity|$encodedEphemeral"
    }

    /**
     * Generate QR bitmap from payload
     */
    fun generateQRCode(payload: String): Bitmap {

        val hints = mapOf(
            EncodeHintType.MARGIN to 1
        )

        val writer = QRCodeWriter()

        val bitMatrix = writer.encode(
            payload,
            BarcodeFormat.QR_CODE,
            QR_SIZE,
            QR_SIZE,
            hints
        )

        val width = bitMatrix.width
        val height = bitMatrix.height

        val bitmap = Bitmap.createBitmap(
            width,
            height,
            Bitmap.Config.RGB_565
        )

        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(
                    x,
                    y,
                    if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
                )
            }
        }

        return bitmap
    }
}