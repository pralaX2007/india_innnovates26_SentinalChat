package com.hacksecure.p2p.storage

import java.util.concurrent.ConcurrentHashMap

class MessageDatabase {

    data class StoredMessage(

        val messageId: String,

        val peerId: String,

        val ciphertext: ByteArray,

        val timestamp: Long,

        val ttlSeconds: Long = 3600
    )

    private val messageStore = ConcurrentHashMap<String, StoredMessage>()


    fun storeMessage(
        messageId: String,
        peerId: String,
        ciphertext: ByteArray,
        timestamp: Long,
        ttlSeconds: Long = 3600
    ) {

        val message = StoredMessage(
            messageId = messageId,
            peerId = peerId,
            ciphertext = ciphertext,
            timestamp = timestamp,
            ttlSeconds = ttlSeconds
        )

        messageStore[messageId] = message
    }


    fun getMessage(messageId: String): StoredMessage? {

        return messageStore[messageId]
    }


    fun getMessagesForPeer(peerId: String): List<StoredMessage> {

        return messageStore.values
            .filter { it.peerId == peerId }
            .sortedBy { it.timestamp }
    }


    fun getAllMessages(): List<StoredMessage> {

        return messageStore.values.toList()
    }


    fun deleteMessage(messageId: String) {

        messageStore.remove(messageId)
    }


    fun deleteConversation(peerId: String) {

        messageStore.entries.removeIf { it.value.peerId == peerId }
    }


    fun clear() {

        messageStore.clear()
    }
}