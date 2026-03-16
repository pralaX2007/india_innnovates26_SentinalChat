package com.hacksecure.p2p.models;

import androidx.annotation.NonNull;

public class Peer {
    private String deviceId;
    private String publicKey;
    private String ipAddress;

    public Peer(String deviceId, String publicKey, String ipAddress) {
        this.deviceId = deviceId;
        this.publicKey = publicKey;
        this.ipAddress = ipAddress;
    }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getPublicKey() { return publicKey; }
    public void setPublicKey(String publicKey) { this.publicKey = publicKey; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    @NonNull
    @Override
    public String toString() {
        return "Peer{" +
                "deviceId='" + deviceId + '\'' +
                ", ipAddress='" + ipAddress + '\'' +
                '}';
    }
}
