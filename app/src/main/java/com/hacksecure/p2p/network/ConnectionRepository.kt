package com.hacksecure.p2p.network

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.MutableLiveData
import com.hacksecure.p2p.messaging.models.MessagePacket
import com.hacksecure.p2p.network.transport.MessageSender
import com.hacksecure.p2p.network.wifidirect.ConnectionHandler

class ConnectionRepository(
    private val connectionHandler: ConnectionHandler
) {
    val connectionState: MutableLiveData<ConnectionState> =
        MutableLiveData(ConnectionState.IDLE)

    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 5
    private val reconnectDelayMs = longArrayOf(1000, 2000, 4000, 8000, 16000)
    private var lastHostIp: String = ""

    var onRawMessageReceived: ((ByteArray) -> Unit)? = null

    fun startServer() {
        connectionState.postValue(ConnectionState.CONNECTING)
        connectionHandler.setListener(listener)
        connectionHandler.startServer()
    }

    fun connectToHost(hostIp: String) {
        lastHostIp = hostIp
        connectionState.postValue(ConnectionState.CONNECTING)
        connectionHandler.setListener(listener)
        connectionHandler.connectToHost(hostIp)
    }

    fun send(packet: MessagePacket) {
        MessageSender(connectionHandler).send(packet)
    }

    fun disconnect() {
        connectionHandler.close()
        connectionState.postValue(ConnectionState.DISCONNECTED)
    }

    private val listener = object : ConnectionHandler.MessageReceiveListener {
        override fun onConnected() {
            reconnectAttempts = 0
            connectionState.postValue(ConnectionState.CONNECTED)
        }

        override fun onMessageReceived(rawData: ByteArray) {
            onRawMessageReceived?.invoke(rawData)
        }

        override fun onConnectionLost() {
            connectionState.postValue(ConnectionState.DISCONNECTED)
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        if (reconnectAttempts >= maxReconnectAttempts) {
            connectionState.postValue(ConnectionState.FAILED)
            return
        }
        val delay = reconnectDelayMs[reconnectAttempts]
        reconnectAttempts++
        connectionState.postValue(ConnectionState.RECONNECTING)
        Handler(Looper.getMainLooper()).postDelayed({
            // Re-use same handler — ConnectionHandler will retry same host
            connectionHandler.connectToHost(lastHostIp)
        }, delay)
    }
}
