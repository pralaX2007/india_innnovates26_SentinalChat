package com.sentinel.chat.messaging.model


data class MessageMetadata(


    val senderId: String,


    val messageId: String,


    val timestamp: Long,


    val ttlSeconds: Long
)