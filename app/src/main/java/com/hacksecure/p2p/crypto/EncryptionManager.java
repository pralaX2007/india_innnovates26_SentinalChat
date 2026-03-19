package com.hacksecure.p2p.crypto;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class EncryptionManager {
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private final byte[] aesKey;

    /**
     * @param sharedSecret  raw ECDH shared secret bytes
     * @param sessionSalt   session-specific salt (e.g. concat of both ephemeral public keys).
     *                      Pass a non-null, non-static value to properly differentiate sessions.
     */
    public EncryptionManager(byte[] sharedSecret, byte[] sessionSalt) throws Exception {
        this.aesKey = deriveKey(sharedSecret, sessionSalt);
    }

    private byte[] deriveKey(byte[] sharedSecret, byte[] salt) {
        // Use proper RFC 5869 HKDF (extract + expand) instead of single-round HMAC
        return com.hacksecure.p2p.crypto.kdf.HKDF.INSTANCE.deriveKey(
                salt,
                sharedSecret,
                "SentinelChat_AES_Key".getBytes(StandardCharsets.UTF_8),
                32
        );
    }

    public byte[] encrypt(String plaintext) throws Exception {
        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"), spec);

        // Fix: always use UTF-8
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        byte[] encrypted = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, encrypted, 0, iv.length);
        System.arraycopy(ciphertext, 0, encrypted, iv.length, ciphertext.length);
        return encrypted;
    }

    public String decrypt(byte[] encrypted) throws Exception {
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, encrypted, 0, GCM_IV_LENGTH);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"), spec);

        byte[] plaintext = cipher.doFinal(encrypted, GCM_IV_LENGTH, encrypted.length - GCM_IV_LENGTH);
        // Fix: always use UTF-8
        return new String(plaintext, StandardCharsets.UTF_8);
    }

    public void wipe() {
        if (aesKey != null) {
            Arrays.fill(aesKey, (byte) 0);
        }
    }
}
