package com.hacksecure.p2p.crypto;

import android.util.Base64;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.KeyAgreement;

public class KeyManager {
    private KeyPair keyPair;

    public void generateKeyPair() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        ECGenParameterSpec ecSpec = new ECGenParameterSpec("secp256r1");
        kpg.initialize(ecSpec);
        this.keyPair = kpg.generateKeyPair();
    }

    public PublicKey getPublicKey() {
        return keyPair != null ? keyPair.getPublic() : null;
    }

    public PrivateKey getPrivateKey() {
        return keyPair != null ? keyPair.getPrivate() : null;
    }

    public String publicKeyToBase64() {
        PublicKey pub = getPublicKey();
        if (pub == null) return null;
        return Base64.encodeToString(pub.getEncoded(), Base64.NO_WRAP);
    }

    public static PublicKey publicKeyFromBase64(String base64) throws Exception {
        byte[] data = Base64.decode(base64, Base64.NO_WRAP);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(data);
        KeyFactory fact = KeyFactory.getInstance("EC");
        return fact.generatePublic(spec);
    }

    public byte[] computeSharedSecret(PublicKey theirPublicKey) throws Exception {
        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(keyPair.getPrivate());
        ka.doPhase(theirPublicKey, true);
        return ka.generateSecret();
    }

    /**
     * Securely clears the key pair reference.
     */
    public void wipe() {
        keyPair = null;
    }
}
