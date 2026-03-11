package com.hacksecure.p2p.network;

import com.hacksecure.p2p.utils.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ConnectionHandler {
    public static final int PORT = 8888;
    public static final String GROUP_OWNER_IP = "192.168.49.1";

    private ServerSocket serverSocket;
    private Socket socket;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private MessageReceiveListener listener;

    public interface MessageReceiveListener {
        void onMessageReceived(byte[] rawData);
        void onConnectionLost();
    }

    public void startServer(MessageReceiveListener listener) {
        this.listener = listener;
        executor.execute(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                Logger.d("Server started on port " + PORT);
                socket = serverSocket.accept();
                handleConnection();
            } catch (IOException e) {
                Logger.e("Server error: " + e.getMessage());
                if (listener != null) listener.onConnectionLost();
            }
        });
    }

    public void connectToHost(MessageReceiveListener listener) {
        this.listener = listener;
        executor.execute(() -> {
            try {
                socket = new Socket(GROUP_OWNER_IP, PORT);
                Logger.d("Connected to host " + GROUP_OWNER_IP);
                handleConnection();
            } catch (IOException e) {
                Logger.e("Client error: " + e.getMessage());
                if (listener != null) listener.onConnectionLost();
            }
        });
    }

    private void handleConnection() {
        executor.execute(() -> {
            try (InputStream inputStream = socket.getInputStream()) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    byte[] data = new byte[bytesRead];
                    System.arraycopy(buffer, 0, data, 0, bytesRead);
                    if (listener != null) listener.onMessageReceived(data);
                }
            } catch (IOException e) {
                Logger.e("Connection lost: " + e.getMessage());
            } finally {
                if (listener != null) listener.onConnectionLost();
                close();
            }
        });
    }

    public void sendRawBytes(byte[] data) {
        executor.execute(() -> {
            if (socket != null && socket.isConnected()) {
                try {
                    OutputStream outputStream = socket.getOutputStream();
                    outputStream.write(data);
                    outputStream.flush();
                } catch (IOException e) {
                    Logger.e("Error sending bytes: " + e.getMessage());
                }
            }
        });
    }

    public void close() {
        try {
            if (socket != null) socket.close();
            if (serverSocket != null) serverSocket.close();
        } catch (IOException e) {
            Logger.e("Error closing connections: " + e.getMessage());
        }
    }
}
