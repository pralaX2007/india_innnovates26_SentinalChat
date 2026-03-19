package com.hacksecure.p2p.utils

import android.util.Base64

object Base64Utils {

    fun encode(bytes: ByteArray): String {
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun decode(base64: String): ByteArray {
        return Base64.decode(base64, Base64.NO_WRAP)
    }

    fun encodeNullable(bytes: ByteArray?): String? {
        return bytes?.let { encode(it) }
    }

    fun decodeNullable(value: String?): ByteArray? {
        return value?.let { decode(it) }
    }
}
