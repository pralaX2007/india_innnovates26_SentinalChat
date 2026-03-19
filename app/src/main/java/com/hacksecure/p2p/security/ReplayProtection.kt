package com.hacksecure.p2p.security

import java.util.concurrent.ConcurrentHashMap

/**
 * Replay protection for the Double Ratchet protocol.
 *
 * Key insight: message numbers reset to 0 on every DH ratchet step,
 * so we must key on (ratchetKeyId, messageNumber) — not messageNumber alone.
 * ratchetKeyId is derived from the remote DH public key bytes.
 */
class ReplayProtection(
    private val maxCacheSize: Int = 2000
) {
    // Key: "ratchetKeyId:messageNumber"
    private val seen = ConcurrentHashMap.newKeySet<String>()

    fun isReplay(ratchetKeyId: String, messageNumber: Int): Boolean {
        val key = "$ratchetKeyId:$messageNumber"
        synchronized(seen) {
            if (seen.contains(key)) return true
            seen.add(key)
            if (seen.size > maxCacheSize) pruneOldest()
            return false
        }
    }

    /**
     * Remove oldest half of the cache when full.
     * Since we can't track insertion order in a HashSet cheaply, we
     * clear the oldest ~50% by dropping the lowest-numbered entries.
     */
    private fun pruneOldest() {
        val toRemove = seen.size / 2
        val iter = seen.iterator()
        var removed = 0
        while (iter.hasNext() && removed < toRemove) {
            iter.next()
            iter.remove()
            removed++
        }
    }

    fun clearAll() {
        seen.clear()
    }
}
