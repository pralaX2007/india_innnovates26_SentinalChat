package com.hacksecure.p2p.session

import com.hacksecure.p2p.Protocol.Ratchet.DoubleRatchet
import com.hacksecure.p2p.security.ReplayProtection
import java.time.Instant

data class SessionState(

    val peerId: String,

    var ratchet: DoubleRatchet,

    val sessionStartTime: Long = Instant.now().toEpochMilli(),

    /**
     * Fix #6: Use the proper ReplayProtection class which is keyed on
     * (ratchetKeyId, messageNumber) and has a bounded cache with pruning.
     * Previously used an unbounded MutableSet<Int> keyed only on messageNumber.
     */
    val replayProtection: ReplayProtection = ReplayProtection(maxCacheSize = 2000)

) {

    /**
     * Check if a message is a replay, keyed by ratchet key ID and message number.
     * This correctly handles message numbers resetting on DH ratchet steps.
     */
    fun isReplay(ratchetKeyId: String, messageNumber: Int): Boolean {
        return replayProtection.isReplay(ratchetKeyId, messageNumber)
    }

    /**
     * Legacy overload for callers that don't have ratchetKeyId — uses a default.
     * Should be migrated away from.
     */
    fun isReplay(messageNumber: Int): Boolean {
        return replayProtection.isReplay("default", messageNumber)
    }

    fun recordMessage(messageNumber: Int) {
        // Recording is handled inside isReplay() — it adds to the set automatically
    }
}