package com.hacksecure.p2p.utils;

import com.hacksecure.p2p.crypto.EncryptionManager;
import com.hacksecure.p2p.crypto.KeyManager;
import com.hacksecure.p2p.messaging.models.Session;
import java.util.UUID;

/**
 * Legacy session manager for key setup and device discovery phase.
 * After handshake is complete, com.hacksecure.p2p.session.SessionManager
 * takes over to manage the ratchet-based session.
 */
// TODO [ARCH] Rename to LegacySessionManager or merge into session.SessionManager
//  to eliminate the confusing duplicate name across packages.
public class SessionManager {
    private static SessionManager instance;
    private Session currentSession;
    private KeyManager keyManager;
    private EncryptionManager encryptionManager;

    private SessionManager() {}

    public static synchronized SessionManager getInstance() {
        if (instance == null) {
            instance = new SessionManager();
        }
        return instance;
    }

    public void createSession() {
        String sessionId = UUID.randomUUID().toString();
        currentSession = new Session(sessionId);
    }

    public Session getSession() {
        return currentSession;
    }

    public void setKeyManager(KeyManager keyManager) {
        this.keyManager = keyManager;
    }

    public KeyManager getKeyManager() {
        return keyManager;
    }

    public void setEncryptionManager(EncryptionManager encryptionManager) {
        this.encryptionManager = encryptionManager;
    }

    public EncryptionManager getEncryptionManager() {
        return encryptionManager;
    }

    public void endSession() {
        if (keyManager != null) {
            keyManager.wipe();
            keyManager = null;
        }
        if (encryptionManager != null) {
            encryptionManager.wipe();
            encryptionManager = null;
        }
        currentSession = null;
    }
}
