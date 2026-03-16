package com.sentinel.chat.messaging.model


data class RatchetHeader(


    val dhPublicKey: ByteArray,


    val messageNumber: Int,


    val previousChainLength: Int
)