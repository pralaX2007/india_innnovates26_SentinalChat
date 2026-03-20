package com.hacksecure.p2p.ui;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.hacksecure.p2p.R;
import com.hacksecure.p2p.SentinelChatApp;
import com.hacksecure.p2p.identity.IdentityKeyManager;
import com.hacksecure.p2p.session.SessionManager;

public class SessionDetailActivity extends AppCompatActivity {

    private String peerId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_session_detail);

        peerId = getIntent().getStringExtra("PEER_ID");
        long sessionStartTime = getIntent().getLongExtra("SESSION_START_TIME", 0);

        TextView tvPeerFingerprint = findViewById(R.id.tvPeerFingerprint);
        TextView tvSessionStats = findViewById(R.id.tvSessionStats);
        TextView tvMyFingerprint = findViewById(R.id.tvMyFingerprint);
        Button btnDestroySession = findViewById(R.id.btnDestroySession);

        tvMyFingerprint.setText(IdentityKeyManager.INSTANCE.getFingerprint());

        com.hacksecure.p2p.storage.SessionDatabase.StoredSession s = SentinelChatApp.sessionRepository.loadSession(peerId);
        if (s != null) {
            tvPeerFingerprint.setText(com.hacksecure.p2p.identity.KeyFingerprint.INSTANCE.generate(s.getIdentityKey()));
        } else {
            tvPeerFingerprint.setText("Unknown");
        }

        com.hacksecure.p2p.session.SessionState state = SessionManager.INSTANCE.getSession(peerId);
        if (state != null) {
            String stats = "Started: " + ((System.currentTimeMillis() - sessionStartTime) / 60000) + " minutes ago\n" +
                    "Messages sent: " + state.getRatchet().getSendMessageNumber() + "\n" +
                    "Messages received: " + state.getRatchet().getReceiveMessageNumber();
            tvSessionStats.setText(stats);
        }

        btnDestroySession.setOnClickListener(v -> destroySession());
    }

    private void destroySession() {
        new AlertDialog.Builder(this)
                .setTitle("Destroy session?")
                .setMessage("All keys and message history will be deleted. This cannot be undone.")
                .setPositiveButton("Destroy", (d, w) -> {
                    SessionManager.INSTANCE.destroySession(peerId);
                    SentinelChatApp.sessionRepository.deleteSession(peerId);
                    setResult(RESULT_OK);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
