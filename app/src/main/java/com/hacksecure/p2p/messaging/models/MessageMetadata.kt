package com.hacksecure.p2p.messaging.models


data class MessageMetadata(


    val senderId: String,


    val messageId: String,


    val timestamp: Long,


    val ttlSeconds: Long
)