package com.hacksecure.p2p;

import android.app.Application;
import com.hacksecure.p2p.identity.IdentityKeyManager;
import com.hacksecure.p2p.utils.Logger;
import com.hacksecure.p2p.network.wifidirect.ConnectionHandler;
import com.hacksecure.p2p.network.ConnectionRepository;
import com.hacksecure.p2p.session.SessionRepository;
import com.hacksecure.p2p.storage.SessionDatabase;
import com.hacksecure.p2p.storage.MessageDatabase;
import com.hacksecure.p2p.messaging.ttl.MessageTTLManager;

public class SentinelChatApp extends Application {

    public static ConnectionHandler connectionHandler;
    public static ConnectionRepository connectionRepository;
    public static SessionRepository sessionRepository;

    @Override
    public void onCreate() {
        super.onCreate();
        Logger.init(this);
        
        connectionHandler = new ConnectionHandler();
        connectionRepository = new ConnectionRepository(connectionHandler);
        
        SessionDatabase sessionDb = new SessionDatabase();
        MessageDatabase messageDb = new MessageDatabase();
        sessionRepository = new SessionRepository(sessionDb, messageDb);

        try {
            IdentityKeyManager.INSTANCE.initialize(this);
        } catch (Exception e) {
            Logger.e("Failed to initialize IdentityKeyManager", e);
        }

        MessageTTLManager ttlManager = new MessageTTLManager(messageDb, sessionDb);
        ttlManager.start();
    }
}
