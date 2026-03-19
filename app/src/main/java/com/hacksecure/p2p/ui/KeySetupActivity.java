package com.hacksecure.p2p.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.hacksecure.p2p.R;
import com.hacksecure.p2p.identity.IdentityKeyManager;
import com.hacksecure.p2p.ui.connection.DeviceDiscoveryActivity;
import com.hacksecure.p2p.utils.Logger;

public class KeySetupActivity extends AppCompatActivity {
    private boolean isHost;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_key_setup);

        isHost = getIntent().getBooleanExtra("IS_HOST", false);

        TextView tvFingerprint = findViewById(R.id.tvFingerprint);

        try {
            // Fix #22: Use the real identity key fingerprint from Android Keystore
            // instead of a truncated Base64 string from the legacy software KeyManager
            String fingerprint = IdentityKeyManager.INSTANCE.getFingerprint();
            tvFingerprint.setText(fingerprint);
        } catch (Exception e) {
            Logger.e("Failed to get identity fingerprint", e);
            Toast.makeText(this, "Failed to load identity key", Toast.LENGTH_SHORT).show();
        }

        findViewById(R.id.btnContinue).setOnClickListener(v -> {
            Intent intent = new Intent(this, DeviceDiscoveryActivity.class);
            intent.putExtra("IS_HOST", isHost);
            startActivity(intent);
        });
    }
}

