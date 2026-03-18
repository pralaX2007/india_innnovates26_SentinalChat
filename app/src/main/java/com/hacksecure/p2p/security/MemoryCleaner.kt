package com.hacksecure.p2p.security

import java.util.Arrays

object MemoryCleaner {


    fun wipe(array: ByteArray?) {

        if (array != null) {
            Arrays.fill(array, 0)
        }
    }


    fun wipeAll(vararg arrays: ByteArray?) {

        arrays.forEach {
            wipe(it)
        }
    }


    fun wipeCollection(collection: Collection<ByteArray?>) {

        for (item in collection) {
            wipe(item)
        }
    }
}