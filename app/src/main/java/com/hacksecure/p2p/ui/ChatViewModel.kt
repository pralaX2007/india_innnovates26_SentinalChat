package com.hacksecure.p2p.ui

import android.os.Build
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.hacksecure.p2p.Protocol.Ratchet.DoubleRatchet
import com.hacksecure.p2p.messaging.models.MessageMetadata
import com.hacksecure.p2p.messaging.models.MessagePacket
import com.hacksecure.p2p.messaging.models.RatchetHeader
import com.hacksecure.p2p.network.ConnectionRepository
import com.hacksecure.p2p.network.ConnectionState
import com.hacksecure.p2p.session.SessionManager
import com.hacksecure.p2p.session.SessionRepository
import com.hacksecure.p2p.session.SessionState
import com.hacksecure.p2p.utils.Logger
import com.hacksecure.p2p.utils.SerializationUtils
import java.util.UUID

class ChatViewModel(
    private val connectionRepository: ConnectionRepository,
    private val sessionRepository: SessionRepository,
    private val peerId: String
) : ViewModel() {

    private val selfId = "Device_" + Build.MODEL

    val messages: MutableLiveData<List<ChatMessage>> = MutableLiveData(emptyList())
    val connectionState: LiveData<ConnectionState> get() = connectionRepository.connectionState

    private val session: SessionState
        get() = SessionManager.getSession(peerId)
            ?: error("No session for peer $peerId")

    init {
        // Initialize callback
        connectionRepository.onRawMessageReceived = { rawData ->
            onRawMessageReceived(rawData)
        }
        
        // Load existing messages if any
        val stored = sessionRepository.getMessagesForPeer(peerId)
        val loadedMessages = stored.map {
            // Note: we're only loading metadata, actual decryption would happen if we stored plaintexts or keys over time.
            // As per instructions, "plaintext is only held in the ChatViewModel.messages LiveData in memory — it is not re-decrypted from storage after expiry"
            // So we'll ignore initial loads for the sake of in-memory ephemerality, or only show placeholders.
            // For now, let's keep it simple and just start fresh, or decipher if possible (which we can't easily without past keys)
            ChatMessage(text = "[Encrypted Message]", isSelf = false, ttlSeconds = it.ttlSeconds, timestamp = it.timestamp)
        }
    }

    fun send(text: String, ttlSeconds: Long = 0L) {
        val plaintext = text.toByteArray(Charsets.UTF_8)
        val ratchet = session.ratchet
        val encrypted = ratchet.encrypt(plaintext)

        val header = RatchetHeader(
            encrypted.header.dhPublicKey,
            encrypted.header.messageNumber,
            encrypted.header.previousChainLength
        )
        val metadata = MessageMetadata(
            senderId = selfId,
            messageId = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            ttlSeconds = ttlSeconds
        )
        val packet = MessagePacket(header, encrypted.iv, encrypted.ciphertext, metadata)
        connectionRepository.send(packet)

        sessionRepository.storeMessage(
            messageId = metadata.messageId,
            peerId = peerId,
            ciphertext = encrypted.ciphertext,
            timestamp = metadata.timestamp,
            ttlSeconds = ttlSeconds
        )

        addMessageToUI(ChatMessage(text = text, isSelf = true, ttlSeconds = ttlSeconds,
            timestamp = metadata.timestamp))
    }

    private fun onRawMessageReceived(rawData: ByteArray) {
        val ratchet = session.ratchet
        val packet = SerializationUtils.deserialize(rawData) as MessagePacket

        val ratchetKeyId = packet.header.dhPublicKey.joinToString(":")
        if (!SessionManager.validateMessage(peerId, ratchetKeyId, packet.header.messageNumber)) {
            Logger.e("Replay detected, dropping message")
            return
        }

        val encrypted = DoubleRatchet.EncryptedMessage(
            header = DoubleRatchet.Header(
                packet.header.dhPublicKey,
                packet.header.messageNumber,
                packet.header.previousChainLength
            ),
            iv = packet.iv,
            ciphertext = packet.ciphertext
        )

        val plaintext = ratchet.decrypt(encrypted)
        val text = String(plaintext, Charsets.UTF_8)

        addMessageToUI(ChatMessage(text = text, isSelf = false,
            ttlSeconds = packet.metadata.ttlSeconds, timestamp = packet.metadata.timestamp))
            
        // Store incoming message
        sessionRepository.storeMessage(
            messageId = packet.metadata.messageId,
            peerId = peerId,
            ciphertext = packet.ciphertext,
            timestamp = packet.metadata.timestamp,
            ttlSeconds = packet.metadata.ttlSeconds
        )
    }

    fun destroySession() {
        SessionManager.destroySession(peerId)
        sessionRepository.deleteSession(peerId)
        connectionRepository.disconnect()
    }

    private fun addMessageToUI(msg: ChatMessage) {
        val current = messages.value.orEmpty().toMutableList()
        current.add(msg)
        messages.postValue(current)
    }

    data class ChatMessage(
        val text: String,
        val isSelf: Boolean,
        val ttlSeconds: Long,
        val timestamp: Long
    )
}
