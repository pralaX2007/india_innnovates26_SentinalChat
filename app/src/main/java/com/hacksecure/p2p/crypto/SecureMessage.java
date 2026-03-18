package com.hacksecure.p2p.crypto;

import android.util.Base64;
import com.hacksecure.p2p.messaging.models.Message;
import com.hacksecure.p2p.utils.Logger;

public class SecureMessage {
    public static Message createSecureMessage(String plaintext, String senderId, int counter, EncryptionManager encryptionManager) {
        try {
            byte[] encryptedBytes = encryptionManager.encrypt(plaintext);
            String encryptedPayload = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP);
            return new Message(senderId, encryptedPayload, counter, System.currentTimeMillis());
        } catch (Exception e) {
            Logger.e("Encryption failed: " + e.getMessage());
            return null;
        }
    }

    public static String decryptMessage(Message message, EncryptionManager encryptionManager) {
        try {
            byte[] encryptedBytes = Base64.decode(message.getEncryptedPayload(), Base64.NO_WRAP);
            return encryptionManager.decrypt(encryptedBytes);
        } catch (Exception e) {
            Logger.e("Decryption failed: " + e.getMessage());
            return null;
        }
    }
}
