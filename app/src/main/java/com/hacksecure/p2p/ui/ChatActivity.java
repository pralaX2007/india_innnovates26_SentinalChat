package com.hacksecure.p2p.ui;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hacksecure.p2p.Protocol.Ratchet.DoubleRatchet;
import com.hacksecure.p2p.Protocol.handshake.QRHandshake;
import com.hacksecure.p2p.R;
import com.hacksecure.p2p.crypto.dh.DiffieHellmanHandshake;
import com.hacksecure.p2p.identity.IdentityKeyManager;
import com.hacksecure.p2p.messaging.models.MessageMetadata;
import com.hacksecure.p2p.messaging.models.MessagePacket;
import com.hacksecure.p2p.messaging.models.RatchetHeader;
import com.hacksecure.p2p.network.transport.MessageReceiver;
import com.hacksecure.p2p.network.transport.MessageSender;
import com.hacksecure.p2p.network.wifidirect.ConnectionHandler;
import com.hacksecure.p2p.session.SessionManager;
import com.hacksecure.p2p.ui.connection.QRCodeGenerator;
import com.hacksecure.p2p.ui.connection.QRCodeScanner;
import com.hacksecure.p2p.utils.Logger;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// TODO [ARCH] Refactor into MVVM: extract ChatViewModel (ratchet + send/receive),
//  ConnectionRepository (wraps ConnectionHandler), and HandshakeManager (QR + DH flow).
//  This Activity currently handles 6+ responsibilities directly.
public class ChatActivity extends AppCompatActivity {

    private ConnectionHandler connectionHandler;
    private MessageSender messageSender;
    private MessageReceiver messageReceiver;  // Fix #15: single instance, not per-message

    private DoubleRatchet ratchet;   // null until handshake completes

    private MessageAdapter adapter;
    private QRCodeScanner qrCodeScanner;

    private EditText etMessage;
    private View btnSend;
    private ImageView ivQr;

    private final String selfId = "Device_" + android.os.Build.MODEL;
    private String peerId = "peer";

    private boolean handshakeDone = false;

    // Fix #4: Retain ephemeral key pair so private key is available for responder handshake
    private KeyPair localEphemeralKeyPair;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        etMessage = findViewById(R.id.etMessage);
        btnSend   = findViewById(R.id.btnSend);
        ivQr      = findViewById(R.id.ivQr);

        RecyclerView rvMessages = findViewById(R.id.rvMessages);
        rvMessages.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MessageAdapter(new ArrayList<>(), selfId);
        rvMessages.setAdapter(adapter);

        connectionHandler = new ConnectionHandler();

        // Step 1 — Show our QR code so the peer can scan us
        generateAndDisplayQR();

        // Step 2 — Start scanning the peer's QR code
        startQRScanning();

        messageSender = new MessageSender(connectionHandler);

        // Step 3 — Send button (only works after handshake)
        btnSend.setOnClickListener(v -> sendMessage());

        // Step 4 — Set up connection listener
        connectionHandler.setListener(new ConnectionHandler.MessageReceiveListener() {
            @Override
            public void onConnected() {}

            @Override
            public void onMessageReceived(byte[] rawData) {
                if (!handshakeDone || ratchet == null || messageReceiver == null) return;
                try {
                    String message = messageReceiver.receive(rawData);
                    if (message != null) {
                        runOnUiThread(() -> adapter.addMessage(message, false));
                    }
                } catch (Exception e) {
                    // Fix #2: No e.printStackTrace() — use Logger instead to avoid leaking crypto state
                    Logger.e("Message receive error: " + e.getClass().getSimpleName());
                }
            }

            @Override
            public void onConnectionLost() {
                // TODO [WIFI] Implement reconnection with exponential backoff.
                //  Re-use existing ratchet state (don't re-handshake).
                //  Attempt reconnect for up to 30 seconds before giving up.
                runOnUiThread(() ->
                        Toast.makeText(ChatActivity.this, "Connection lost", Toast.LENGTH_SHORT).show());
            }
        });

        boolean isHost = getIntent().getBooleanExtra("IS_HOST", false);
        String host    = getIntent().getStringExtra("GROUP_OWNER_ADDRESS");

        // Step 5 — Start TCP layer
        if (isHost) {
            connectionHandler.startServer();
        } else {
            connectionHandler.connectToHost(host);
        }
    }

    private void generateAndDisplayQR() {
        try {
            byte[] identityKey  = IdentityKeyManager.INSTANCE.getPublicKeyBytes();
            byte[] ephemeralKey = generateEphemeralKey();
            String payload = QRCodeGenerator.INSTANCE.createIdentityPayload(selfId, identityKey, ephemeralKey);
            Bitmap qrBitmap = QRCodeGenerator.INSTANCE.generateQRCode(payload);
            ivQr.setImageBitmap(qrBitmap);
        } catch (Exception e) {
            // Fix #2: No e.printStackTrace()
            Logger.e("QR generation failed: " + e.getClass().getSimpleName());
            Toast.makeText(this, "QR generation failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Start scanning the peer's QR code.
     * Once scanned, performs the DH handshake and initialises the ratchet.
     */
    private void startQRScanning() {
        // Only scan if we haven't completed a handshake yet
        if (handshakeDone) return;

        qrCodeScanner = new QRCodeScanner(this, payload -> {
            if (handshakeDone) return kotlin.Unit.INSTANCE;
            runOnUiThread(() -> {
                try {
                    QRHandshake.HandshakeResult result = QRHandshake.INSTANCE.performHandshake(payload);
                    peerId  = result.getRemoteUserId();
                    ratchet = result.getRatchet();

                    // Register session
                    SessionManager.INSTANCE.createSession(peerId, ratchet);

                    // Fix #15: Create single MessageReceiver instance after handshake
                    messageReceiver = new MessageReceiver(connectionHandler, ratchet, peerId);

                    handshakeDone = true;

                    // Hide QR display, show chat UI
                    ivQr.setVisibility(View.GONE);

                    qrCodeScanner.stopScanner();

                    Toast.makeText(this, "Secure session established!", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(this, "Handshake failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
            return kotlin.Unit.INSTANCE;
        });

        // Find a PreviewView in the layout (must exist — see activity_chat.xml)
        androidx.camera.view.PreviewView previewView = findViewById(R.id.previewView);
        if (previewView != null) {
            qrCodeScanner.startScanner(this, previewView);
        }
    }

    /**
     * Fix #4: Generate ephemeral key pair and RETAIN the private key for handshake.
     * Previously, only the public key was returned and the private key was discarded,
     * making the responder unable to compute the matching shared secret.
     */
    private byte[] generateEphemeralKey() {
        localEphemeralKeyPair = DiffieHellmanHandshake.INSTANCE.generateEphemeralKeyPair();
        return localEphemeralKeyPair.getPublic().getEncoded();
    }

    private void sendMessage() {
        if (!handshakeDone || ratchet == null) {
            Toast.makeText(this, "Scan peer's QR code first", Toast.LENGTH_SHORT).show();
            return;
        }

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

        MessagePacket packet = new MessagePacket(header, encrypted.getIv(), encrypted.getCiphertext(), metadata);

        messageSender.send(packet);

        adapter.addMessage(text, true);
        etMessage.setText("");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (qrCodeScanner != null) qrCodeScanner.stopScanner();
        connectionHandler.close();
        SessionManager.INSTANCE.destroySession(peerId);
    }

    // ──────────────────────────────────────────────────
    // MessageAdapter
    // ──────────────────────────────────────────────────

    static class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.ViewHolder> {

        static class ChatMessage {
            final String text;
            final boolean isSelf;
            ChatMessage(String text, boolean isSelf) {
                this.text   = text;
                this.isSelf = isSelf;
            }
        }

        private final List<ChatMessage> messages;
        private final String selfId;

        MessageAdapter(List<ChatMessage> messages, String selfId) {
            this.messages = messages;
            this.selfId   = selfId;
        }

        void addMessage(String text, boolean isSelf) {
            messages.add(new ChatMessage(text, isSelf));
            notifyItemInserted(messages.size() - 1);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_message, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ChatMessage msg = messages.get(position);
            holder.tvMessage.setText(msg.text);
            holder.tvMessage.setTextAlignment(
                    msg.isSelf ? View.TEXT_ALIGNMENT_VIEW_END : View.TEXT_ALIGNMENT_VIEW_START);
        }

        @Override
        public int getItemCount() { return messages.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvMessage;
            ViewHolder(View itemView) {
                super(itemView);
                tvMessage = itemView.findViewById(R.id.tvMessage);
            }
        }
    }
}
