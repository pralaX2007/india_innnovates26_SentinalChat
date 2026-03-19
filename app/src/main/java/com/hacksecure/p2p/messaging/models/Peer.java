package com.hacksecure.p2p.messaging.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

// TODO [INTEROP] Migrate to Kotlin data class to eliminate boilerplate getters/setters.
public class Peer {
    private String deviceId;
    private String publicKey;
    private String ipAddress;

    public Peer(@NonNull String deviceId, @Nullable String publicKey, @Nullable String ipAddress) {
        this.deviceId = deviceId;
        this.publicKey = publicKey;
        this.ipAddress = ipAddress;
    }

    @NonNull
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(@NonNull String deviceId) { this.deviceId = deviceId; }

    @Nullable
    public String getPublicKey() { return publicKey; }
    public void setPublicKey(@Nullable String publicKey) { this.publicKey = publicKey; }

    @Nullable
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(@Nullable String ipAddress) { this.ipAddress = ipAddress; }

    @NonNull
    @Override
    public String toString() {
        return "Peer{" +
                "deviceId='" + deviceId + '\'' +
                ", ipAddress='" + ipAddress + '\'' +
                '}';
    }
}
