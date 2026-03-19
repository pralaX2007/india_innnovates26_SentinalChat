package com.hacksecure.p2p;

import android.app.Application;
import com.hacksecure.p2p.identity.IdentityKeyManager;
import com.hacksecure.p2p.utils.Logger;

public class SentinelChatApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Logger.init(this);
        try {
            IdentityKeyManager.INSTANCE.initialize(this);
        } catch (Exception e) {
            Logger.e("Failed to initialize IdentityKeyManager", e);
        }
    }
}
