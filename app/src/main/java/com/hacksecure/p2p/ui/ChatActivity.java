package com.hacksecure.p2p.ui;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.ImageView;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hacksecure.p2p.R;
import com.hacksecure.p2p.Protocol.Ratchet.DoubleRatchet;
import com.hacksecure.p2p.identity.IdentityKeyManager;
import com.hacksecure.p2p.messaging.models.MessageMetadata;
import com.hacksecure.p2p.messaging.models.MessagePacket;
import com.hacksecure.p2p.messaging.models.RatchetHeader;
import com.hacksecure.p2p.network.transport.MessageReceiver;
import com.hacksecure.p2p.network.transport.MessageSender;
import com.hacksecure.p2p.network.wifidirect.ConnectionHandler;
import com.hacksecure.p2p.session.SessionManager;
import com.hacksecure.p2p.ui.connection.QRCodeGenerator;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ChatActivity extends AppCompatActivity {

    private ConnectionHandler connectionHandler;
    private MessageSender messageSender;
    private MessageReceiver messageReceiver;

    private SessionManager sessionManager;
    private DoubleRatchet ratchet;

    private MessageAdapter adapter;

    private EditText etMessage;
    private View btnSend;
    private ImageView ivQr;

    private final String selfId = "Device_" + android.os.Build.MODEL;
    private String peerId = "peer";

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        ivQr = findViewById(R.id.ivQr);

        RecyclerView rvMessages = findViewById(R.id.rvMessages);
        rvMessages.setLayoutManager(new LinearLayoutManager(this));

        adapter = new MessageAdapter(new ArrayList<>(), selfId);
        rvMessages.setAdapter(adapter);

        connectionHandler = new ConnectionHandler();
        sessionManager = SessionManager.INSTANCE;

        // ✅ Generate QR
        generateAndDisplayQR();

        // ⚠️ Ratchet must be initialized after handshake
        if (sessionManager.getSession(peerId) != null) {
            ratchet = sessionManager.getSession(peerId).getRatchet();
        }

        messageSender = new MessageSender(connectionHandler);

        // ✅ Pass ratchet directly (NO decryptor)
        messageReceiver = new MessageReceiver(connectionHandler, ratchet);

        btnSend.setOnClickListener(v -> sendMessage());

        boolean isHost = getIntent().getBooleanExtra("IS_HOST", false);
        String host = getIntent().getStringExtra("GROUP_OWNER_ADDRESS");

        connectionHandler.setListener(new ConnectionHandler.MessageReceiveListener() {

            @Override
            public void onConnected() {}

            @Override
            public void onMessageReceived(byte[] rawData) {

                try {
                    String message = messageReceiver.receive(rawData);

                    if (message != null) {
                        runOnUiThread(() ->
                                adapter.addMessage(message, false)
                        );
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onConnectionLost() {}
        });

        if (isHost) {
            connectionHandler.startServer();
        } else {
            connectionHandler.connectToHost(host);
        }
    }

    private byte[] generateEphemeralKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(256);
            KeyPair pair = generator.generateKeyPair();
            return pair.getPublic().getEncoded();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void generateAndDisplayQR() {

        byte[] identityKey = IdentityKeyManager.INSTANCE.getPublicKeyBytes();
        byte[] ephemeralKey = generateEphemeralKey();

        String payload = QRCodeGenerator.INSTANCE.createIdentityPayload(
                selfId,
                identityKey,
                ephemeralKey
        );

        Bitmap qrBitmap = QRCodeGenerator.INSTANCE.generateQRCode(payload);
        ivQr.setImageBitmap(qrBitmap);
    }

    private void sendMessage() {

        if (ratchet == null) return;

        String text = etMessage.getText().toString().trim();
        if (text.isEmpty()) return;

        byte[] plaintext = text.getBytes(StandardCharsets.UTF_8);

        DoubleRatchet.EncryptedMessage encrypted = ratchet.encrypt(plaintext);

        RatchetHeader header = new RatchetHeader(
                encrypted.getHeader().getDhPublicKey(),
                encrypted.getHeader().getMessageNumber(),
                encrypted.getHeader().getPreviousChainLength()
        );

        MessageMetadata metadata = new MessageMetadata(
                selfId,
                UUID.randomUUID().toString(),
                System.currentTimeMillis(),
                0
        );

        MessagePacket packet = new MessagePacket(
                header,
                encrypted.getIv(),
                encrypted.getCiphertext(),
                metadata
        );

        messageSender.send(packet);

        adapter.addMessage(text, true);
        etMessage.setText("");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        connectionHandler.close();
        sessionManager.destroySession(peerId);
    }

    // Adapter unchanged
}