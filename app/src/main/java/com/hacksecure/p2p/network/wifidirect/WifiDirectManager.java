package com.hacksecure.p2p.network.wifidirect;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.Looper;

import com.hacksecure.p2p.utils.Logger;

import java.util.ArrayList;
import java.util.List;

public class WifiDirectManager {
    private final Context context;
    private final WifiP2pManager manager;
    private final WifiP2pManager.Channel channel;
    private BroadcastReceiver receiver;
    private WifiDirectListener listener;

    public interface WifiDirectListener {
        void onPeersAvailable(List<WifiP2pDevice> peers);
        void onConnected(WifiP2pInfo info);
        void onDisconnected();
        void onError(String message);
    }

    public WifiDirectManager(Context context) {
        this.context = context;
        this.manager = (WifiP2pManager) context.getSystemService(Context.WIFI_P2P_SERVICE);
        this.channel = manager.initialize(context, Looper.getMainLooper(), null);
    }

    public void setListener(WifiDirectListener listener) {
        this.listener = listener;
    }

    public void initialize() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        receiver = new BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
                    int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
                    if (state != WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                        if (listener != null) listener.onError("Wi-Fi Direct is not enabled");
                    }
                } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
                    manager.requestPeers(channel, peers -> {
                        if (listener != null) listener.onPeersAvailable(new ArrayList<>(peers.getDeviceList()));
                    });
                } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
                    NetworkInfo networkInfo;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        networkInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO, NetworkInfo.class);
                    } else {
                        networkInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
                    }

                    if (networkInfo != null && networkInfo.isConnected()) {
                        manager.requestConnectionInfo(channel, info -> {
                            if (listener != null) listener.onConnected(info);
                        });
                    } else {
                        if (listener != null) listener.onDisconnected();
                    }
                }
            }
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, intentFilter, Context.RECEIVER_EXPORTED);
        } else {
            context.registerReceiver(receiver, intentFilter);
        }
    }

    @SuppressLint("MissingPermission")
    public void startDiscovery() {
        manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() { Logger.d("Discovery started"); }
            @Override
            public void onFailure(int reason) { 
                if (listener != null) listener.onError("Discovery failed: " + reason); 
            }
        });
    }

    public void stopDiscovery() {
        manager.stopPeerDiscovery(channel, null);
    }

    @SuppressLint("MissingPermission")
    public void connectToPeer(WifiP2pDevice device) {
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;
        manager.connect(channel, config, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() { Logger.d("Connecting to " + device.deviceName); }
            @Override
            public void onFailure(int reason) {
                if (listener != null) listener.onError("Connection failed: " + reason);
            }
        });
    }

    @SuppressLint("MissingPermission")
    public void createGroup() {
        manager.createGroup(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() { Logger.d("Group created"); }
            @Override
            public void onFailure(int reason) {
                if (listener != null) listener.onError("Group creation failed: " + reason);
            }
        });
    }

    public void disconnect() {
        manager.removeGroup(channel, null);
    }

    public void cleanup() {
        if (receiver != null) {
            context.unregisterReceiver(receiver);
            receiver = null;
        }
    }
}
