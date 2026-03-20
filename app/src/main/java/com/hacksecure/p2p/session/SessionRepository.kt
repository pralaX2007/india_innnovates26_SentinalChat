package com.hacksecure.p2p.session

import com.hacksecure.p2p.Protocol.Ratchet.RatchetState
import com.hacksecure.p2p.storage.MessageDatabase
import com.hacksecure.p2p.storage.SessionDatabase
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hacksecure.p2p.utils.SerializationUtils

class SessionRepository(
    private val sessionDatabase: SessionDatabase,
    private val messageDatabase: MessageDatabase
) {

    fun saveSession(peerId: String, identityKey: ByteArray, startTime: Long) {
        val gson = Gson()
        val ratchetState = SessionManager.getSession(peerId)
            ?.let { RatchetState.fromRatchet(it.ratchet).encode() }
            ?.let { gson.toJson(it).toByteArray(Charsets.UTF_8) }
            ?: ByteArray(0)

        sessionDatabase.saveSession(peerId, identityKey, ratchetState, startTime)
    }

    fun loadSession(peerId: String): SessionDatabase.StoredSession? {
        return sessionDatabase.getSession(peerId)
    }

    fun deleteSession(peerId: String) {
        sessionDatabase.deleteSession(peerId)
        messageDatabase.deleteConversation(peerId)
    }

    fun storeMessage(
        messageId: String,
        peerId: String,
        ciphertext: ByteArray,
        timestamp: Long,
        ttlSeconds: Long
    ) {
        messageDatabase.storeMessage(messageId, peerId, ciphertext, timestamp, ttlSeconds)
    }

    fun getMessagesForPeer(peerId: String): List<MessageDatabase.StoredMessage> {
        return messageDatabase.getMessagesForPeer(peerId)
    }
}
