package com.hacksecure.p2p.session

import com.hacksecure.p2p.Protocol.Ratchet.DoubleRatchet

object SessionManager {

    private val store = EphemeralSessionStore()

    fun createSession(peerId: String, ratchet: DoubleRatchet): SessionState {
        val session = SessionState(
            peerId = peerId,
            ratchet = ratchet
        )
        store.put(peerId, session)
        return session
    }

    fun getSession(peerId: String): SessionState? {
        return store.get(peerId)
    }

    fun destroySession(peerId: String) {
        store.remove(peerId)
    }

    fun validateMessage(peerId: String, messageNumber: Int): Boolean {
        val session = getSession(peerId) ?: return false
        if (session.isReplay(messageNumber)) {
            return false
        }
        session.recordMessage(messageNumber)
        return true
    }

    fun destroyAllSessions() {
        store.clear()
    }
}
