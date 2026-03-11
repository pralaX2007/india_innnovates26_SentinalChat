package com.hacksecure.p2p.network;

import com.google.gson.Gson;
import com.hacksecure.p2p.models.Message;
import com.hacksecure.p2p.utils.Logger;

import java.nio.charset.StandardCharsets;

public class MessageTransport implements ConnectionHandler.MessageReceiveListener {
    private final ConnectionHandler connectionHandler;
    private final Gson gson = new Gson();
    private MessageTransportListener listener;

    public interface MessageTransportListener {
        void onMessageReceived(Message message);
        void onConnectionLost();
    }

    public MessageTransport(ConnectionHandler connectionHandler) {
        this.connectionHandler = connectionHandler;
    }

    public void setListener(MessageTransportListener listener) {
        this.listener = listener;
    }

    public void sendMessage(Message message) {
        String json = gson.toJson(message);
        connectionHandler.sendRawBytes(json.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void onMessageReceived(byte[] rawData) {
        try {
            String json = new String(rawData, StandardCharsets.UTF_8);
            Message message = gson.fromJson(json, Message.class);
            if (listener != null) {
                listener.onMessageReceived(message);
            }
        } catch (Exception e) {
            Logger.e("Error deserializing message: " + e.getMessage());
        }
    }

    @Override
    public void onConnectionLost() {
        if (listener != null) listener.onConnectionLost();
    }
}
