package com.hacksecure.p2p.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.hacksecure.p2p.R;
import com.hacksecure.p2p.crypto.EncryptionManager;
import com.hacksecure.p2p.crypto.KeyManager;
import com.hacksecure.p2p.crypto.SecureMessage;
import com.hacksecure.p2p.models.Message;
import com.hacksecure.p2p.network.ConnectionHandler;
import com.hacksecure.p2p.network.MessageTransport;
import com.hacksecure.p2p.utils.Logger;
import com.hacksecure.p2p.utils.SessionManager;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;

public class ChatActivity extends AppCompatActivity implements MessageTransport.MessageTransportListener {
    private MessageTransport messageTransport;
    private ConnectionHandler connectionHandler;
    private EncryptionManager encryptionManager;
    private KeyManager keyManager;
    private MessageAdapter adapter;
    private EditText etMessage;
    private View btnSend;
    private int messageCounter = 0;
    private final String selfId = "Device_" + android.os.Build.MODEL;
    private boolean isKeyExchanged = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        boolean isHost = getIntent().getBooleanExtra("IS_HOST", false);

        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        RecyclerView rvMessages = findViewById(R.id.rvMessages);
        rvMessages.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MessageAdapter(new ArrayList<>(), selfId);
        rvMessages.setAdapter(adapter);

        connectionHandler = new ConnectionHandler();
        messageTransport = new MessageTransport(connectionHandler);
        messageTransport.setListener(this);

        keyManager = new KeyManager();
        try {
            keyManager.generateKeyPair();
            SessionManager.getInstance().setKeyManager(keyManager);
        } catch (Exception e) {
            Logger.e("Failed to generate keys", e);
            Toast.makeText(this, "Security initialization failed", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Disable UI until key exchange is complete
        btnSend.setEnabled(false);
        etMessage.setHint("Securing connection...");

        if (isHost) {
            connectionHandler.startServer(new HandshakeListener(true));
        } else {
            connectionHandler.connectToHost(new HandshakeListener(false));
        }

        btnSend.setOnClickListener(v -> sendMessage());
    }

    private class HandshakeListener implements ConnectionHandler.MessageReceiveListener {
        private final boolean isHost;
        private boolean publicKeySent = false;

        HandshakeListener(boolean isHost) {
            this.isHost = isHost;
        }

        @Override
        public void onConnected() {
            Logger.d("Connected, sending public key...");
            sendPublicKey();
        }

        @Override
        public void onMessageReceived(byte[] rawData) {
            if (!isKeyExchanged) {
                try {
                    String peerKeyBase64 = new String(rawData, StandardCharsets.UTF_8);
                    // Basic validation to ensure it's likely a key and not a message
                    if (peerKeyBase64.length() > 50 && !peerKeyBase64.contains("{")) {
                        PublicKey peerPublicKey = KeyManager.publicKeyFromBase64(peerKeyBase64);
                        byte[] sharedSecret = keyManager.computeSharedSecret(peerPublicKey);
                        encryptionManager = new EncryptionManager(sharedSecret);
                        SessionManager.getInstance().setEncryptionManager(encryptionManager);
                        
                        isKeyExchanged = true;
                        runOnUiThread(() -> {
                            btnSend.setEnabled(true);
                            etMessage.setHint("Type a secure message");
                            Toast.makeText(ChatActivity.this, "Connection Secured", Toast.LENGTH_SHORT).show();
                        });

                        // Switch to normal message transport
                        connectionHandler.setListener(messageTransport);

                        // If we haven't sent our key yet, send it now.
                        if (!publicKeySent) {
                            sendPublicKey();
                        }
                    }
                } catch (Exception e) {
                    Logger.e("Handshake failed", e);
                }
            }
        }

        @Override
        public void onConnectionLost() {
            messageTransport.onConnectionLost();
        }

        void sendPublicKey() {
            if (publicKeySent) return;
            String myKey = keyManager.publicKeyToBase64();
            if (myKey != null) {
                connectionHandler.sendRawBytes(myKey.getBytes(StandardCharsets.UTF_8));
                publicKeySent = true;
            }
        }
    }

    private void sendMessage() {
        String text = etMessage.getText().toString().trim();
        if (text.isEmpty() || encryptionManager == null) return;

        Message message = SecureMessage.createSecureMessage(text, selfId, messageCounter++, encryptionManager);
        if (message != null) {
            messageTransport.sendMessage(message);
            runOnUiThread(() -> {
                adapter.addMessage(message, text);
                etMessage.setText("");
            });
        }
    }

    @Override
    public void onMessageReceived(Message message) {
        String decryptedText = SecureMessage.decryptMessage(message, encryptionManager);
        if (decryptedText != null) {
            runOnUiThread(() -> adapter.addMessage(message, decryptedText));
        }
    }

    @Override
    public void onConnectionLost() {
        runOnUiThread(() -> {
            Toast.makeText(this, "Connection lost", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        connectionHandler.close();
        SessionManager.getInstance().endSession();
    }

    private static class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.ViewHolder> {
        private final List<ChatMessage> messages;
        private final String selfId;

        static class ChatMessage {
            Message msg;
            String plainText;
            ChatMessage(Message m, String p) { this.msg = m; this.plainText = p; }
        }

        MessageAdapter(List<ChatMessage> messages, String selfId) {
            this.messages = messages;
            this.selfId = selfId;
        }

        void addMessage(Message m, String p) {
            messages.add(new ChatMessage(m, p));
            notifyItemInserted(messages.size() - 1);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ChatMessage chatMsg = messages.get(position);
            if (chatMsg.msg.getSenderId().equals(selfId)) {
                holder.tvRight.setText(chatMsg.plainText);
                holder.tvRight.setVisibility(View.VISIBLE);
                holder.tvLeft.setVisibility(View.GONE);
            } else {
                holder.tvLeft.setText(chatMsg.plainText);
                holder.tvLeft.setVisibility(View.VISIBLE);
                holder.tvRight.setVisibility(View.GONE);
            }
        }

        @Override
        public int getItemCount() { return messages.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvLeft, tvRight;
            ViewHolder(View itemView) {
                super(itemView);
                tvLeft = itemView.findViewById(R.id.tvMessageLeft);
                tvRight = itemView.findViewById(R.id.tvMessageRight);
            }
        }
    }
}
