package com.sentinel.chat.messaging.ttl

import com.sentinel.chat.database.MessageDatabase
import com.sentinel.chat.database.SessionDatabase
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MessageTTLManager(

    private val messageDatabase: MessageDatabase,
    private val sessionDatabase: SessionDatabase

) {

    companion object {


        private const val CLEANUP_INTERVAL_SECONDS = 30
    }

    private val scheduler = Executors.newSingleThreadScheduledExecutor()


    fun start() {

        scheduler.scheduleAtFixedRate(
            { performCleanup() },
            CLEANUP_INTERVAL_SECONDS.toLong(),
            CLEANUP_INTERVAL_SECONDS.toLong(),
            TimeUnit.SECONDS
        )
    }


    fun stop() {
        scheduler.shutdownNow()
    }


    private fun performCleanup() {

        val now = Instant.now().toEpochMilli()

        cleanupMessages(now)
        cleanupSessions(now)
    }


    private fun cleanupMessages(now: Long) {

        val allMessages = messageDatabase.getAllMessages()

        for (message in allMessages) {

            val expiryTime = message.timestamp + message.ttlSeconds * 1000

            if (now >= expiryTime) {
                messageDatabase.deleteMessage(message.messageId)
            }
        }
    }


    private fun cleanupSessions(now: Long) {

        val sessions = sessionDatabase.getAllSessions()

        for (session in sessions) {

            val sessionAge = now - session.sessionStartTime

            val maxSessionAge = TimeUnit.HOURS.toMillis(24)

            if (sessionAge > maxSessionAge) {
                sessionDatabase.deleteSession(session.peerId)
            }
        }
    }
}