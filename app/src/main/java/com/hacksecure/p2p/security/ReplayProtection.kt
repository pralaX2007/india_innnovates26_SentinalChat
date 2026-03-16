package com.sentinel.chat.crypto.security

import java.util.concurrent.ConcurrentHashMap

class ReplayProtection(
    private val maxCacheSize: Int = 2000
) {

    private val seenMessages = ConcurrentHashMap<String, MutableSet<Int>>()

    /**
     * Check if a message is a replay
     */
    fun isReplay(
        sessionId: String,
        messageNumber: Int
    ): Boolean {

        val sessionSet = seenMessages.computeIfAbsent(sessionId) {
            ConcurrentHashMap.newKeySet()
        }

        synchronized(sessionSet) {

            if (sessionSet.contains(messageNumber)) {
                return true
            }

            sessionSet.add(messageNumber)

            if (sessionSet.size > maxCacheSize) {
                pruneOldEntries(sessionSet)
            }

            return false
        }
    }

    /**
     * Remove oldest message numbers to keep cache bounded
     */
    private fun pruneOldEntries(set: MutableSet<Int>) {

        val sorted = set.sorted()

        val removeCount = set.size - maxCacheSize

        for (i in 0 until removeCount) {
            set.remove(sorted[i])
        }
    }

    /**
     * Clear replay cache for a session
     */
    fun clearSession(sessionId: String) {
        seenMessages.remove(sessionId)
    }

    /**
     * Clear everything (useful on logout)
     */
    fun clearAll() {
        seenMessages.clear()
    }
}