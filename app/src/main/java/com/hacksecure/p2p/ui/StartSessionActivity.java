package com.hacksecure.p2p.ui;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.hacksecure.p2p.R;
import com.hacksecure.p2p.utils.SessionManager;

public class StartSessionActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start_session);

        findViewById(R.id.btnCreateSession).setOnClickListener(v -> {
            SessionManager.getInstance().createSession();
            navigateToKeySetup(true);
        });

        findViewById(R.id.btnJoinSession).setOnClickListener(v -> {
            SessionManager.getInstance().createSession();
            navigateToKeySetup(false);
        });
    }

    private void navigateToKeySetup(boolean isHost) {
        Intent intent = new Intent(this, KeySetupActivity.class);
        intent.putExtra("IS_HOST", isHost);
        startActivity(intent);
    }
}
