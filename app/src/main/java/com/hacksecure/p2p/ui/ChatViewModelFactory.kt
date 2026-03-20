package com.hacksecure.p2p.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.hacksecure.p2p.network.ConnectionRepository
import com.hacksecure.p2p.session.SessionRepository

class ChatViewModelFactory(
    private val connectionRepository: ConnectionRepository,
    private val sessionRepository: SessionRepository,
    private val peerId: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return ChatViewModel(connectionRepository, sessionRepository, peerId) as T
    }
}
