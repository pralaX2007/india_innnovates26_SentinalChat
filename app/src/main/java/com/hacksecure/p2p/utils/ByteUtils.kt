package com.sentinel.chat.utils

import java.nio.ByteBuffer
import java.util.Arrays

object ByteUtils {


    fun concat(vararg arrays: ByteArray): ByteArray {

        var totalLength = 0
        for (arr in arrays) {
            totalLength += arr.size
        }

        val result = ByteArray(totalLength)

        var position = 0
        for (arr in arrays) {
            System.arraycopy(arr, 0, result, position, arr.size)
            position += arr.size
        }

        return result
    }


    fun intToBytes(value: Int): ByteArray {

        return ByteBuffer.allocate(4)
            .putInt(value)
            .array()
    }


    fun bytesToInt(bytes: ByteArray): Int {

        require(bytes.size == 4) {
            "Invalid integer byte length"
        }

        return ByteBuffer.wrap(bytes).int
    }


    fun wipeArray(array: ByteArray?) {

        if (array != null) {
            Arrays.fill(array, 0)
        }
    }
}