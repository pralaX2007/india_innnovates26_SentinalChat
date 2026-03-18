package com.hacksecure.p2p.session

import java.util.concurrent.ConcurrentHashMap

class EphemeralSessionStore {

    private val sessions = ConcurrentHashMap<String, SessionState>()


    fun put(peerId: String, session: SessionState) {
        sessions[peerId] = session
    }


    fun get(peerId: String): SessionState? {
        return sessions[peerId]
    }


    fun remove(peerId: String) {
        sessions.remove(peerId)
    }

    fun exists(peerId: String): Boolean {
        return sessions.containsKey(peerId)
    }


    fun clear() {
        sessions.clear()
    }
}