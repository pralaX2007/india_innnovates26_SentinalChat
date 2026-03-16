package com.sentinel.chat.network.qr

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.util.Base64

object QRCodeGenerator {

    private const val QR_SIZE = 600

    /**
     * Convert identity public key to a QR payload string
     */
    fun createIdentityPayload(
        userId: String,
        publicKey: ByteArray
    ): String {

        val encodedKey = Base64.getEncoder().encodeToString(publicKey)

        return "$userId:$encodedKey"
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
                    if (bitMatrix.get(x, y))
                        android.graphics.Color.BLACK
                    else
                        android.graphics.Color.WHITE
                )
            }
        }

        return bitmap
    }
}