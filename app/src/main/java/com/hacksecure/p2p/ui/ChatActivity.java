package com.hacksecure.p2p.ui;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.hacksecure.p2p.R;
import com.hacksecure.p2p.SentinelChatApp;

import java.util.ArrayList;
import java.util.List;

public class ChatActivity extends AppCompatActivity {

    private ChatViewModel chatViewModel;
    private MessageAdapter adapter;
    private long currentTtlSeconds = 0L;

    private TextView tvConnectionStatus;
    private EditText etMessage;
    private Button btnSend;
    private ImageButton btnTtl;
    private TextView tvToolbarTitle;
    private View btnSessionDetail;

    private String peerId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        peerId = getIntent().getStringExtra("PEER_ID");
        boolean isHost = getIntent().getBooleanExtra("IS_HOST", false);
        String groupOwnerAddress = getIntent().getStringExtra("GROUP_OWNER_ADDRESS");

        tvConnectionStatus = findViewById(R.id.tvConnectionStatus);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        btnTtl = findViewById(R.id.btnTtl);
        tvToolbarTitle = findViewById(R.id.tvToolbarTitle);
        btnSessionDetail = findViewById(R.id.btnSessionDetail);

        RecyclerView rvMessages = findViewById(R.id.rvMessages);
        rvMessages.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MessageAdapter(new ArrayList<>());
        rvMessages.setAdapter(adapter);

        ChatViewModelFactory factory = new ChatViewModelFactory(
                SentinelChatApp.connectionRepository,
                SentinelChatApp.sessionRepository,
                peerId
        );
        chatViewModel = new ViewModelProvider(this, factory).get(ChatViewModel.class);

        // Fetch fingerprint for toolbar
        String peerIdentityKey = "Unknown";
        try {
            com.hacksecure.p2p.storage.SessionDatabase.StoredSession s = SentinelChatApp.sessionRepository.loadSession(peerId);
            if (s != null) {
                peerIdentityKey = com.hacksecure.p2p.identity.KeyFingerprint.INSTANCE.generate(s.getIdentityKey());
                tvToolbarTitle.setText("← " + peerIdentityKey.substring(0, 9) + "...");
            } else {
                tvToolbarTitle.setText("← Unknown Peer...");
            }
        } catch (Exception e) {}

        btnSessionDetail.setOnClickListener(v -> {
            Intent intent = new Intent(this, SessionDetailActivity.class);
            intent.putExtra("PEER_ID", peerId);
            com.hacksecure.p2p.storage.SessionDatabase.StoredSession s = SentinelChatApp.sessionRepository.loadSession(peerId);
            if (s != null) {
                intent.putExtra("SESSION_START_TIME", s.getSessionStartTime());
            }
            startActivityForResult(intent, 1001);
        });

        chatViewModel.getMessages().observe(this, messages -> {
            adapter.setMessages(messages);
            if (!messages.isEmpty()) {
                rvMessages.smoothScrollToPosition(messages.size() - 1);
            }
        });

        chatViewModel.getConnectionState().observe(this, state -> {
            switch (state) {
                case IDLE:
                case CONNECTED:
                    tvConnectionStatus.setText("🔒 Connected · Double Ratchet · E2E");
                    tvConnectionStatus.setBackgroundColor(Color.parseColor("#00AA00"));
                    break;
                case CONNECTING:
                case RECONNECTING:
                    tvConnectionStatus.setText("⚠ Reconnecting...");
                    tvConnectionStatus.setBackgroundColor(Color.parseColor("#FFA500"));
                    break;
                case DISCONNECTED:
                case FAILED:
                    tvConnectionStatus.setText("✕ Connection lost");
                    tvConnectionStatus.setBackgroundColor(Color.parseColor("#AA0000"));
                    break;
            }
        });

        btnSend.setOnClickListener(v -> {
            String text = etMessage.getText().toString().trim();
            if (!text.isEmpty()) {
                chatViewModel.send(text, currentTtlSeconds);
                etMessage.setText("");
            }
        });

        btnTtl.setOnClickListener(v -> showTtlSelector());

        if (isHost) {
            SentinelChatApp.connectionRepository.startServer();
        } else {
            SentinelChatApp.connectionRepository.connectToHost(groupOwnerAddress);
        }
    }

    private void showTtlSelector() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_ttl, null);
        dialog.setContentView(view);

        view.findViewById(R.id.ttl_none).setOnClickListener(v -> { currentTtlSeconds = 0; dialog.dismiss(); });
        view.findViewById(R.id.ttl_5m).setOnClickListener(v -> { currentTtlSeconds = 300; dialog.dismiss(); });
        view.findViewById(R.id.ttl_1h).setOnClickListener(v -> { currentTtlSeconds = 3600; dialog.dismiss(); });
        view.findViewById(R.id.ttl_24h).setOnClickListener(v -> { currentTtlSeconds = 86400; dialog.dismiss(); });
        view.findViewById(R.id.ttl_read).setOnClickListener(v -> { currentTtlSeconds = -1; dialog.dismiss(); });

        dialog.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001 && resultCode == RESULT_OK) {
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing() && peerId != null) {
            chatViewModel.destroySession();
        }
    }

    // ──────────────────────────────────────────────────
    // MessageAdapter
    // ──────────────────────────────────────────────────

    static class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.ViewHolder> {
        private List<ChatViewModel.ChatMessage> messages;

        MessageAdapter(List<ChatViewModel.ChatMessage> messages) {
            this.messages = messages;
        }

        void setMessages(List<ChatViewModel.ChatMessage> messages) {
            this.messages = messages;
            notifyDataSetChanged();
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
            ChatViewModel.ChatMessage msg = messages.get(position);
            holder.tvMessage.setText(msg.getText());

            LinearLayout container = (LinearLayout) holder.tvMessage.getParent();
            LinearLayout parentLayout = (LinearLayout) container.getParent();
            if (msg.isSelf()) {
                parentLayout.setGravity(Gravity.END);
                container.setBackgroundResource(R.drawable.bg_message_sent);
            } else {
                parentLayout.setGravity(Gravity.START);
                container.setBackgroundResource(R.drawable.bg_message_received);
            }

            if (msg.getTtlSeconds() > 0) {
                holder.tvTtl.setVisibility(View.VISIBLE);
                long expiresInSecs = ((msg.getTimestamp() + msg.getTtlSeconds() * 1000) - System.currentTimeMillis()) / 1000;
                if (expiresInSecs > 0) {
                    holder.tvTtl.setText("⏱ Expires in " + expiresInSecs + "s");
                } else {
                    holder.tvTtl.setText("⏱ Expired");
                }
            } else if (msg.getTtlSeconds() == -1) {
                holder.tvTtl.setVisibility(View.VISIBLE);
                holder.tvTtl.setText("⏱ On read");
            } else {
                holder.tvTtl.setVisibility(View.GONE);
            }
        }

        @Override
        public int getItemCount() { return messages.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvMessage;
            TextView tvTtl;
            ViewHolder(View itemView) {
                super(itemView);
                tvMessage = itemView.findViewById(R.id.tvMessage);
                tvTtl = itemView.findViewById(R.id.tvTtl);
            }
        }
    }
}
