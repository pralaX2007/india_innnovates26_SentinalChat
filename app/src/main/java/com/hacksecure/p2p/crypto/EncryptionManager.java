package com.hacksecure.p2p.crypto;

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

    public EncryptionManager(byte[] sharedSecret) throws Exception {
        // Simple HKDF-like key derivation using HMAC-SHA256
        this.aesKey = deriveKey(sharedSecret);
    }

    private byte[] deriveKey(byte[] sharedSecret) throws Exception {
        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secret_key = new SecretKeySpec(sharedSecret, "HmacSHA256");
        sha256_HMAC.init(secret_key);
        // Use a fixed salt and info for this basic scaffold
        byte[] salt = "HackSecure2026Salt".getBytes();
        sha256_HMAC.update(salt);
        byte[] derived = sha256_HMAC.doFinal("HackSecure2026Info".getBytes());
        // Wipe the temporary sharedSecret if possible? 
        // We can't easily wipe the sharedSecret passed in here unless we manage it.
        return derived;
    }

    public byte[] encrypt(String plaintext) throws Exception {
        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"), spec);
        
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes());
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
        return new String(plaintext);
    }

    /**
     * Securely wipes the AES key by zeroing the byte array.
     */
    public void wipe() {
        if (aesKey != null) {
            Arrays.fill(aesKey, (byte) 0);
        }
    }
}
