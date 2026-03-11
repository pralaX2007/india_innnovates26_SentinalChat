package com.hacksecure.p2p.network;

import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;

import java.util.ArrayList;
import java.util.List;

public class PeerDiscovery {
    private final List<WifiP2pDevice> discoveredPeers = new ArrayList<>();

    public void updatePeers(WifiP2pDeviceList deviceList) {
        discoveredPeers.clear();
        if (deviceList != null) {
            discoveredPeers.addAll(deviceList.getDeviceList());
        }
    }

    public List<WifiP2pDevice> getPeers() {
        return new ArrayList<>(discoveredPeers);
    }

    public void clearPeers() {
        discoveredPeers.clear();
    }

    public WifiP2pDevice findPeerByAddress(String address) {
        if (address == null) return null;
        for (WifiP2pDevice device : discoveredPeers) {
            if (address.equals(device.deviceAddress)) {
                return device;
            }
        }
        return null;
    }
}
