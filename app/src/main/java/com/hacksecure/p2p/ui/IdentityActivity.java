package com.hacksecure.p2p.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.hacksecure.p2p.R;
import com.hacksecure.p2p.identity.IdentityKeyManager;
import com.hacksecure.p2p.ui.connection.DeviceDiscoveryActivity;

public class IdentityActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_identity);

        String fingerprint = IdentityKeyManager.INSTANCE.getFingerprint();
        TextView tvFingerprint = findViewById(R.id.tvFingerprint);
        tvFingerprint.setText(fingerprint);

        findViewById(R.id.btnCopy).setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("SentinalChat fingerprint", fingerprint));
            Toast.makeText(this, "Fingerprint copied", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btnShare).setOnClickListener(v -> {
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_TEXT,
                "My SentinalChat fingerprint:\n" + fingerprint);
            startActivity(Intent.createChooser(share, "Share fingerprint"));
        });

        findViewById(R.id.btnContinue).setOnClickListener(v -> {
            getPreferences(MODE_PRIVATE).edit()
                .putBoolean("identity_confirmed", true)
                .apply();
            startActivity(new Intent(this, DeviceDiscoveryActivity.class));
            finish();
        });
    }
}
