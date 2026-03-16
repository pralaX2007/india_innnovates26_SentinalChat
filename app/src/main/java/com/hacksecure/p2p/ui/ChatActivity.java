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

import com.hacksecure.p2p.R;
import com.sentinel.chat.Protocol.Ratchet.DoubleRatchet;
import com.sentinel.chat.messaging.model.MessageMetadata;
import com.sentinel.chat.messaging.model.MessagePacket;
import com.sentinel.chat.messaging.transport.MessageReceiver;
import com.sentinel.chat.messaging.transport.MessageSender;
import com.sentinel.chat.network.ConnectionHandler;
import com.sentinel.chat.session.SessionManager;
import com.sentinel.chat.utils.SerializationUtils;

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

    private final String selfId = "Device_" + android.os.Build.MODEL;
    private String peerId = "peer";

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);

        RecyclerView rvMessages = findViewById(R.id.rvMessages);
        rvMessages.setLayoutManager(new LinearLayoutManager(this));

        adapter = new MessageAdapter(new ArrayList<>(), selfId);
        rvMessages.setAdapter(adapter);

        connectionHandler = new ConnectionHandler();

        sessionManager = new SessionManager();

        // DoubleRatchet must already be created via QRHandshake
        ratchet = sessionManager.getSession(peerId).getRatchet();

        messageSender = new MessageSender(connectionHandler);
        messageReceiver = new MessageReceiver(connectionHandler, ratchet);

        btnSend.setOnClickListener(v -> sendMessage());

        startReceiverLoop();
    }

    private void sendMessage() {

        String text = etMessage.getText().toString().trim();

        if (text.isEmpty()) return;

        byte[] plaintext = text.getBytes();

        DoubleRatchet.EncryptedMessage encrypted = ratchet.encrypt(plaintext);

        MessagePacket packet = new MessagePacket(
                encrypted.header,
                encrypted.iv,
                encrypted.ciphertext,
                new MessageMetadata(
                        selfId,
                        UUID.randomUUID().toString(),
                        System.currentTimeMillis(),
                        0
                )
        );

        messageSender.send(packet);

        adapter.addMessage(text, true);

        etMessage.setText("");
    }

    private void startReceiverLoop() {

        new Thread(() -> {

            while (true) {

                try {

                    byte[] raw = connectionHandler.receiveRawBytes();

                    if (raw == null) continue;

                    MessagePacket packet = SerializationUtils.deserialize(raw);

                    DoubleRatchet.EncryptedMessage encrypted =
                            new DoubleRatchet.EncryptedMessage(
                                    packet.getHeader(),
                                    packet.getIv(),
                                    packet.getCiphertext()
                            );

                    byte[] plaintext = ratchet.decrypt(encrypted);

                    String message = new String(plaintext);

                    runOnUiThread(() -> adapter.addMessage(message, false));

                } catch (Exception e) {

                    runOnUiThread(() ->
                            Toast.makeText(this, "Message error", Toast.LENGTH_SHORT).show()
                    );
                }
            }

        }).start();
    }

    @Override
    protected void onDestroy() {

        super.onDestroy();

        connectionHandler.close();

        sessionManager.destroySession(peerId);
    }

    private static class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.ViewHolder> {

        private final List<ChatMessage> messages;
        private final String selfId;

        static class ChatMessage {
            String text;
            boolean isSelf;
            ChatMessage(String t, boolean s) { text = t; isSelf = s; }
        }

        MessageAdapter(List<ChatMessage> messages, String selfId) {
            this.messages = messages;
            this.selfId = selfId;
        }

        void addMessage(String text, boolean isSelf) {

            messages.add(new ChatMessage(text, isSelf));

            notifyItemInserted(messages.size() - 1);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_message, parent, false);

            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {

            ChatMessage msg = messages.get(position);

            if (msg.isSelf) {

                holder.tvRight.setText(msg.text);
                holder.tvRight.setVisibility(View.VISIBLE);
                holder.tvLeft.setVisibility(View.GONE);

            } else {

                holder.tvLeft.setText(msg.text);
                holder.tvLeft.setVisibility(View.VISIBLE);
                holder.tvRight.setVisibility(View.GONE);
            }
        }

        @Override
        public int getItemCount() {
            return messages.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {

            TextView tvLeft;
            TextView tvRight;

            ViewHolder(View itemView) {

                super(itemView);

                tvLeft = itemView.findViewById(R.id.tvMessageLeft);
                tvRight = itemView.findViewById(R.id.tvMessageRight);
            }
        }
    }
}