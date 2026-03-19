package com.hacksecure.p2p.messaging.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

// TODO [INTEROP] Migrate to Kotlin data class to eliminate boilerplate.
public class Session {
    private final String sessionId;
    private final List<Peer> peers;
    private final long startTime;

    public Session(@NonNull String sessionId) {
        this.sessionId = sessionId;
        this.peers = new ArrayList<>();
        this.startTime = System.currentTimeMillis();
    }

    @NonNull
    public String getSessionId() { return sessionId; }

    @NonNull
    public List<Peer> getPeers() { return peers; }

    public long getStartTime() { return startTime; }

    public void addPeer(@Nullable Peer peer) {
        if (peer != null && !peers.contains(peer)) {
            peers.add(peer);
        }
    }

    public void removePeer(@Nullable Peer peer) {
        peers.remove(peer);
    }

    public boolean isActive() {
        return !peers.isEmpty();
    }
}
