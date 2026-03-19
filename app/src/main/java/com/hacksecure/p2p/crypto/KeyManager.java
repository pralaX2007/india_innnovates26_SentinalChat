package com.hacksecure.p2p.crypto;

import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

import javax.crypto.KeyAgreement;

public class KeyManager {
    private KeyPair keyPair;

    public void generateKeyPair() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        ECGenParameterSpec ecSpec = new ECGenParameterSpec("secp256r1");
        kpg.initialize(ecSpec);
        this.keyPair = kpg.generateKeyPair();
    }

    @Nullable
    public PublicKey getPublicKey() {
        return keyPair != null ? keyPair.getPublic() : null;
    }

    @Nullable
    public PrivateKey getPrivateKey() {
        return keyPair != null ? keyPair.getPrivate() : null;
    }

    @Nullable
    public String publicKeyToBase64() {
        PublicKey pub = getPublicKey();
        if (pub == null) return null;
        return Base64.encodeToString(pub.getEncoded(), Base64.NO_WRAP);
    }

    @NonNull
    public static PublicKey publicKeyFromBase64(@NonNull String base64) throws Exception {
        byte[] data = Base64.decode(base64, Base64.NO_WRAP);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(data);
        KeyFactory fact = KeyFactory.getInstance("EC");
        return fact.generatePublic(spec);
    }

    @NonNull
    public byte[] computeSharedSecret(@NonNull PublicKey theirPublicKey) throws Exception {
        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(keyPair.getPrivate());
        ka.doPhase(theirPublicKey, true);
        return ka.generateSecret();
    }

    /**
     * Securely clears the key pair material, then nulls the reference.
     * Note: getEncoded() returns a copy, so this wipes copies — the JCA
     * internal representation can only be cleared via Android Keystore.
     */
    public void wipe() {
        if (keyPair != null) {
            byte[] privBytes = keyPair.getPrivate().getEncoded();
            if (privBytes != null) {
                Arrays.fill(privBytes, (byte) 0);
            }
            byte[] pubBytes = keyPair.getPublic().getEncoded();
            if (pubBytes != null) {
                Arrays.fill(pubBytes, (byte) 0);
            }
            keyPair = null;
        }
    }
}

