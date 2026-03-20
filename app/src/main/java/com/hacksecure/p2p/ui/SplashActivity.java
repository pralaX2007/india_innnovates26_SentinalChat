package com.hacksecure.p2p.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.hacksecure.p2p.R;
import com.hacksecure.p2p.ui.connection.DeviceDiscoveryActivity;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        boolean identityConfirmed = getPreferences(MODE_PRIVATE)
            .getBoolean("identity_confirmed", false);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(this,
                identityConfirmed
                    ? DeviceDiscoveryActivity.class
                    : IdentityActivity.class);
            startActivity(intent);
            finish();
        }, 1200);
    }
}
