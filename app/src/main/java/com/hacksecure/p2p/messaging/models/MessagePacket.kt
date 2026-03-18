package com.hacksecure.p2p.messaging.models


data class MessagePacket(


    val header: RatchetHeader,


    val iv: ByteArray,


    val ciphertext: ByteArray,


    val metadata: MessageMetadata
)