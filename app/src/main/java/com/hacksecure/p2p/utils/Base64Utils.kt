package com.sentinel.chat.utils

import java.util.Base64

object Base64Utils {

    private val encoder = Base64.getEncoder()
    private val decoder = Base64.getDecoder()

    fun encode(bytes: ByteArray): String {
        return encoder.encodeToString(bytes)
    }

    fun decode(base64: String): ByteArray {
        return decoder.decode(base64)
    }

    fun encodeNullable(bytes: ByteArray?): String? {
        return bytes?.let { encode(it) }
    }

    fun decodeNullable(value: String?): ByteArray? {
        return value?.let { decode(it) }
    }
}