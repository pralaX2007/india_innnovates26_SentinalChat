package com.hacksecure.p2p.ui;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;

import com.hacksecure.p2p.Protocol.Ratchet.DoubleRatchet;
import com.hacksecure.p2p.Protocol.handshake.HandshakeManager;
import com.hacksecure.p2p.R;
import com.hacksecure.p2p.SentinelChatApp;
import com.hacksecure.p2p.crypto.dh.DiffieHellmanHandshake;
import com.hacksecure.p2p.identity.IdentityKeyManager;
import com.hacksecure.p2p.security.IdentityVerification;
import com.hacksecure.p2p.session.SessionManager;
import com.hacksecure.p2p.storage.SessionDatabase;
import com.hacksecure.p2p.ui.connection.QRCodeScanner;

import java.security.KeyPair;

public class HandshakeActivity extends AppCompatActivity {

    private QRCodeScanner qrCodeScanner;
    private KeyPair localEphemeralKeyPair;
    private DoubleRatchet ratchet;

    private String peerId;
    private byte[] remoteIdentityKeyBytes;
    private String peerFingerprint;
    private String groupOwnerAddress;
    private boolean isHost;
    private boolean handshakeDone = false;

    private enum Phase { QR_EXCHANGE, FINGERPRINT_VERIFY }
    private Phase currentPhase = Phase.QR_EXCHANGE;

    private View layoutQrPhase;
    private View layoutVerifyPhase;
    
    private ImageView ivQrCode;
    private PreviewView previewView;
    private TextView tvScanInstruction;

    private TextView tvMyFingerprint;
    private TextView tvPeerFingerprint;
    private Button btnConfirm;
    private Button btnReject;

    private final String selfId = "Device_" + Build.MODEL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_handshake);

        isHost = getIntent().getBooleanExtra("IS_HOST", false);
        groupOwnerAddress = getIntent().getStringExtra("GROUP_OWNER_ADDRESS");

        layoutQrPhase = findViewById(R.id.layoutQrPhase);
        layoutVerifyPhase = findViewById(R.id.layoutVerifyPhase);
        ivQrCode = findViewById(R.id.ivQrCode);
        previewView = findViewById(R.id.previewView);
        tvScanInstruction = findViewById(R.id.tvScanInstruction);
        
        tvMyFingerprint = findViewById(R.id.tvMyFingerprint);
        tvPeerFingerprint = findViewById(R.id.tvPeerFingerprint);
        btnConfirm = findViewById(R.id.btnConfirm);
        btnReject = findViewById(R.id.btnReject);

        startQrExchange();

        btnConfirm.setOnClickListener(v -> onHandshakeVerified());
        btnReject.setOnClickListener(v -> onHandshakeMismatch());
    }

    private void startQrExchange() {
        HandshakeManager.HandshakeSetup setup = HandshakeManager.INSTANCE.prepareQR(selfId);
        ivQrCode.setImageBitmap(setup.getQrBitmap());
        localEphemeralKeyPair = setup.getLocalEphemeralKeyPair();

        qrCodeScanner = new QRCodeScanner(this, payload -> {
            onQRCodeDetected(payload);
            return kotlin.Unit.INSTANCE;
        });
        qrCodeScanner.startScanner(this, previewView);
    }

    private void onQRCodeDetected(String payload) {
        if (handshakeDone) return;
        handshakeDone = true;
        qrCodeScanner.stopScanner();

        runOnUiThread(() -> {
            try {
                HandshakeManager.HandshakeResult result;
                if (!isHost) {
                    result = HandshakeManager.INSTANCE.executeAsInitiator(payload);
                } else {
                    result = HandshakeManager.INSTANCE.executeAsResponder(payload, localEphemeralKeyPair);
                }

                this.ratchet = result.getRatchet();
                this.peerId = result.getPeerId();
                this.remoteIdentityKeyBytes = result.getPeerIdentityKeyBytes();
                this.peerFingerprint = result.getPeerFingerprint();

                performTofuCheck();
            } catch (Exception e) {
                Toast.makeText(this, "Handshake failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    private void performTofuCheck() {
        SessionDatabase.StoredSession prevSession = SentinelChatApp.sessionRepository.loadSession(peerId);
        if (prevSession != null) {
            boolean keyUnchanged = IdentityVerification.INSTANCE.verifyRemoteIdentity(
                    DiffieHellmanHandshake.INSTANCE.decodePublicKey(remoteIdentityKeyBytes),
                    com.hacksecure.p2p.identity.KeyFingerprint.INSTANCE.generate(prevSession.getIdentityKey())
            );

            if (!keyUnchanged) {
                new AlertDialog.Builder(this)
                        .setTitle("Security Warning")
                        .setMessage("This peer's identity has changed since your last session.\nOnly continue if you trust this change.")
                        .setPositiveButton("Trust New Key", (d, w) -> showFingerprintVerify())
                        .setNegativeButton("Abort", (d, w) -> onHandshakeMismatch())
                        .setCancelable(false)
                        .show();
                return;
            }
        }
        showFingerprintVerify();
    }

    private void showFingerprintVerify() {
        currentPhase = Phase.FINGERPRINT_VERIFY;
        layoutQrPhase.setVisibility(View.GONE);
        tvScanInstruction.setVisibility(View.GONE);
        layoutVerifyPhase.setVisibility(View.VISIBLE);

        tvMyFingerprint.setText(IdentityKeyManager.INSTANCE.getFingerprint());
        tvPeerFingerprint.setText(peerFingerprint);
    }

    private void onHandshakeVerified() {
        SentinelChatApp.sessionRepository.saveSession(
                peerId,
                remoteIdentityKeyBytes,
                System.currentTimeMillis()
        );

        SessionManager.INSTANCE.createSession(peerId, ratchet);

        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra("PEER_ID", peerId);
        intent.putExtra("IS_HOST", isHost);
        intent.putExtra("GROUP_OWNER_ADDRESS", groupOwnerAddress);
        startActivity(intent);
        finish();
    }

    private void onHandshakeMismatch() {
        if (peerId != null) {
            SessionManager.INSTANCE.destroySession(peerId);
        }
        SentinelChatApp.connectionRepository.disconnect();
        Toast.makeText(this, "Session aborted", Toast.LENGTH_LONG).show();
        finish();
    }
}
