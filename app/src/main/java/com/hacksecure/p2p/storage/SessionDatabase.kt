package com.sentinel.chat.database

import com.sentinel.chat.session.SessionState
import java.util.concurrent.ConcurrentHashMap

class SessionDatabase {

    data class StoredSession(

        val peerId: String,

        val identityKey: ByteArray,

        val serializedRatchetState: ByteArray,

        val sessionStartTime: Long
    )

    private val sessionStore = ConcurrentHashMap<String, StoredSession>()


    fun saveSession(
        peerId: String,
        identityKey: ByteArray,
        ratchetState: ByteArray,
        startTime: Long
    ) {

        val stored = StoredSession(
            peerId = peerId,
            identityKey = identityKey,
            serializedRatchetState = ratchetState,
            sessionStartTime = startTime
        )

        sessionStore[peerId] = stored
    }


    fun getSession(peerId: String): StoredSession? {

        return sessionStore[peerId]
    }


    fun deleteSession(peerId: String) {

        sessionStore.remove(peerId)
    }


    fun getAllSessions(): List<StoredSession> {

        return sessionStore.values.toList()
    }


    fun clear() {

        sessionStore.clear()
    }
}