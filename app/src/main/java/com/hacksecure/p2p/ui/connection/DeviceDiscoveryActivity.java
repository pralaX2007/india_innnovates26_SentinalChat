package com.hacksecure.p2p.ui.connection;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hacksecure.p2p.R;
import com.hacksecure.p2p.network.wifidirect.PeerDiscovery;
import com.hacksecure.p2p.network.wifidirect.WifiDirectManager;
import com.hacksecure.p2p.ui.HandshakeActivity;
import com.hacksecure.p2p.utils.Logger;

import java.util.ArrayList;
import java.util.List;

public class DeviceDiscoveryActivity extends AppCompatActivity implements WifiDirectManager.WifiDirectListener {
    private WifiDirectManager wifiDirectManager;
    private PeerAdapter adapter;
    private TextView tvStatus;
    private TextView tvEmptyState;
    private boolean isHost = false;
    private static final int PERMISSIONS_REQUEST_CODE = 1001;

    private final PeerDiscovery peerDiscovery = new PeerDiscovery();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_discovery);

        tvStatus = findViewById(R.id.tvStatus);
        tvEmptyState = findViewById(R.id.tvEmptyState);
        Button btnHost = findViewById(R.id.btnHost);
        Button btnJoin = findViewById(R.id.btnJoin);

        RecyclerView rvPeers = findViewById(R.id.rvPeers);
        rvPeers.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PeerAdapter(new ArrayList<>(), this::onPeerClicked);
        rvPeers.setAdapter(adapter);

        wifiDirectManager = new WifiDirectManager(this);
        wifiDirectManager.setListener(this);
        wifiDirectManager.initialize();

        btnHost.setOnClickListener(v -> {
            isHost = true;
            checkPermissionsAndStart();
        });

        btnJoin.setOnClickListener(v -> {
            isHost = false;
            checkPermissionsAndStart();
        });

        tvStatus.setText("Select role to begin");
    }

    private void checkPermissionsAndStart() {
        List<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        permissions.add(Manifest.permission.CAMERA);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        }

        boolean allGranted = true;
        for (String p : permissions) {
            if (ActivityCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (allGranted) {
            startWifiDirectAction();
        } else {
            ActivityCompat.requestPermissions(this, permissions.toArray(new String[0]), PERMISSIONS_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                startWifiDirectAction();
            } else {
                Toast.makeText(this, "Permissions required to connect", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void startWifiDirectAction() {
        if (isHost) {
            tvStatus.setText(R.string.waiting_for_peers);
            wifiDirectManager.createGroup();
        } else {
            tvStatus.setText(R.string.discover_peers);
            wifiDirectManager.startDiscovery();
        }
    }

    private void onPeerClicked(WifiP2pDevice device) {
        WifiP2pDevice verifiedPeer = peerDiscovery.findPeerByAddress(device.deviceAddress);
        if (verifiedPeer != null) {
            wifiDirectManager.connectToPeer(verifiedPeer, isHost);
        } else {
            wifiDirectManager.connectToPeer(device, isHost);
        }
    }

    @Override
    public void onPeersAvailable(List<WifiP2pDevice> peers) {
        peerDiscovery.updatePeers(peers);
        runOnUiThread(() -> {
            if (peers.isEmpty() && !isHost) {
                tvEmptyState.setVisibility(View.VISIBLE);
            } else {
                tvEmptyState.setVisibility(View.GONE);
            }
            adapter.updatePeers(peers);
        });
    }

    @Override
    public void onConnected(WifiP2pInfo info) {
        runOnUiThread(() -> {
            Toast.makeText(this, "Connected!", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, HandshakeActivity.class);
            intent.putExtra("IS_HOST", isHost);
            intent.putExtra("GROUP_OWNER_ADDRESS", info.groupOwnerAddress.getHostAddress());
            startActivity(intent);
            finish();
        });
    }

    @Override
    public void onDisconnected() {
        runOnUiThread(() -> Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onError(String message) {
        runOnUiThread(() -> Toast.makeText(this, "Error: " + message, Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        wifiDirectManager.cleanup();
    }

    static class PeerAdapter extends RecyclerView.Adapter<PeerAdapter.ViewHolder> {
        private final List<WifiP2pDevice> peers;
        private final OnPeerClickListener listener;

        public interface OnPeerClickListener {
            void onPeerClick(WifiP2pDevice device);
        }

        PeerAdapter(List<WifiP2pDevice> peers, OnPeerClickListener listener) {
            this.peers = peers;
            this.listener = listener;
        }

        void updatePeers(List<WifiP2pDevice> newPeers) {
            peers.clear();
            peers.addAll(newPeers);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_peer, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            WifiP2pDevice device = peers.get(position);
            holder.tvName.setText(device.deviceName);
            holder.tvAddress.setText(device.deviceAddress);
            holder.itemView.setOnClickListener(v -> listener.onPeerClick(device));
        }

        @Override
        public int getItemCount() { return peers.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvAddress;
            ViewHolder(View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tvDeviceName);
                tvAddress = itemView.findViewById(R.id.tvDeviceAddress);
            }
        }
    }
}
