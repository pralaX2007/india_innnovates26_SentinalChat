package com.hacksecure.p2p.messaging.models;

import java.util.ArrayList;
import java.util.List;

public class Session {
    private String sessionId;
    private List<Peer> peers;
    private long startTime;

    public Session(String sessionId) {
        this.sessionId = sessionId;
        this.peers = new ArrayList<>();
        this.startTime = System.currentTimeMillis();
    }

    public String getSessionId() { return sessionId; }
    public List<Peer> getPeers() { return peers; }
    public long getStartTime() { return startTime; }

    public void addPeer(Peer peer) {
        if (peer != null && !peers.contains(peer)) {
            peers.add(peer);
        }
    }

    public void removePeer(Peer peer) {
        peers.remove(peer);
    }

    public boolean isActive() {
        return !peers.isEmpty();
    }
}
