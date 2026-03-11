package com.hacksecure.p2p.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.hacksecure.p2p.R;
import com.hacksecure.p2p.crypto.KeyManager;
import com.hacksecure.p2p.utils.Logger;
import com.hacksecure.p2p.utils.SessionManager;

public class KeySetupActivity extends AppCompatActivity {
    private KeyManager keyManager;
    private boolean isHost;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_key_setup);

        isHost = getIntent().getBooleanExtra("IS_HOST", false);
        keyManager = new KeyManager();
        SessionManager.getInstance().setKeyManager(keyManager);

        TextView tvFingerprint = findViewById(R.id.tvFingerprint);

        try {
            keyManager.generateKeyPair();
            String base64Key = keyManager.publicKeyToBase64();
            if (base64Key != null) {
                String fingerprint = base64Key.substring(0, Math.min(base64Key.length(), 16));
                tvFingerprint.setText(fingerprint);
            }
        } catch (Exception e) {
            Logger.e("Key generation failed", e);
            Toast.makeText(this, "Failed to generate keys", Toast.LENGTH_SHORT).show();
        }

        findViewById(R.id.btnContinue).setOnClickListener(v -> {
            Intent intent = new Intent(this, DeviceDiscoveryActivity.class);
            intent.putExtra("IS_HOST", isHost);
            startActivity(intent);
        });
    }
}
