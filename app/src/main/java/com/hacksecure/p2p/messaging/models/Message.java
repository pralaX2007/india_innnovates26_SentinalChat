package com.hacksecure.p2p.messaging.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

// TODO [INTEROP] Migrate to Kotlin data class — this is 33 lines of boilerplate
//  that Kotlin can express in 1 line: data class Message(val senderId: String, ...)
public class Message {
    private String senderId;
    private String encryptedPayload;
    private int counter;
    private long timestamp;

    public Message(@NonNull String senderId, @NonNull String encryptedPayload, int counter, long timestamp) {
        this.senderId = senderId;
        this.encryptedPayload = encryptedPayload;
        this.counter = counter;
        this.timestamp = timestamp;
    }

    @NonNull
    public String getSenderId() { return senderId; }
    public void setSenderId(@NonNull String senderId) { this.senderId = senderId; }

    @NonNull
    public String getEncryptedPayload() { return encryptedPayload; }
    public void setEncryptedPayload(@NonNull String encryptedPayload) { this.encryptedPayload = encryptedPayload; }

    public int getCounter() { return counter; }
    public void setCounter(int counter) { this.counter = counter; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
