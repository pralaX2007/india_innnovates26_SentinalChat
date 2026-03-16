package com.sentinel.chat.messaging.model


data class MessagePacket(


    val header: RatchetHeader,


    val iv: ByteArray,


    val ciphertext: ByteArray,


    val metadata: MessageMetadata
)