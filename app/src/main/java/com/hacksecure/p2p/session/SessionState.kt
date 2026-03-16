package com.sentinel.chat.session

import com.sentinel.chat.Protocol.Ratchet.DoubleRatchet
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

data class SessionState(

    val peerId: String,

    var ratchet: DoubleRatchet,

    val sessionStartTime: Long = Instant.now().toEpochMilli(),


    val replayWindow: MutableSet<Int> = ConcurrentHashMap.newKeySet()

) {


    fun isReplay(messageNumber: Int): Boolean {
        return replayWindow.contains(messageNumber)
    }


    fun recordMessage(messageNumber: Int) {
        replayWindow.add(messageNumber)
    }

}