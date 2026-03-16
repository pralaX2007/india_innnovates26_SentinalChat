package com.hacksecure.p2p.models;

public class Message {
    private String senderId;
    private String encryptedPayload;
    private int counter;
    private long timestamp;

    public Message(String senderId, String encryptedPayload, int counter, long timestamp) {
        this.senderId = senderId;
        this.encryptedPayload = encryptedPayload;
        this.counter = counter;
        this.timestamp = timestamp;
    }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getEncryptedPayload() { return encryptedPayload; }
    public void setEncryptedPayload(String encryptedPayload) { this.encryptedPayload = encryptedPayload; }

    public int getCounter() { return counter; }
    public void setCounter(int counter) { this.counter = counter; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
