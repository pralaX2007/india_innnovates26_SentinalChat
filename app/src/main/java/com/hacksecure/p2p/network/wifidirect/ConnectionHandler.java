package com.hacksecure.p2p.network.wifidirect;

import com.hacksecure.p2p.utils.Logger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handles TCP socket connection for P2P messaging.
 *
 * Framing: every message is prefixed with a 4-byte int indicating payload length.
 * This prevents TCP stream fragmentation / concatenation issues.
 *
 * TODO [SECURITY] Issue #11: TCP layer is plaintext — packet sizes and timing are visible
 *  to any observer on the WiFi Direct group. For production: wrap Socket with SSLSocket
 *  or add a Noise protocol layer for transport-level metadata hiding.
 */
public class ConnectionHandler {
    public static final int PORT = 8888;

    private ServerSocket serverSocket;
    private Socket socket;
    private DataOutputStream outputStream;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private MessageReceiveListener listener;
    private final AtomicBoolean closed = new AtomicBoolean(false);  // Fix #17: track close state

    public interface MessageReceiveListener {
        void onConnected();
        void onMessageReceived(byte[] rawData);
        void onConnectionLost();
    }

    public void setListener(MessageReceiveListener listener) {
        this.listener = listener;
    }

    /** Start as server (group owner / host). Listener must be set before calling this. */
    public void startServer() {
        executor.execute(() -> {
            try {
                // Fix #26: Set SO_REUSEADDR to prevent BindException on rapid reconnect
                ServerSocket ss = new ServerSocket();
                ss.setReuseAddress(true);
                ss.bind(new InetSocketAddress(PORT));
                serverSocket = ss;
                Logger.d("Server started on port " + PORT);
                socket = serverSocket.accept();
                outputStream = new DataOutputStream(socket.getOutputStream());
                if (listener != null) listener.onConnected();
                handleConnection();
            } catch (IOException e) {
                if (!closed.get()) {
                    Logger.e("Server error: " + e.getMessage());
                    if (listener != null) listener.onConnectionLost();
                }
            }
        });
    }

    /** Connect to host at given IP address. Listener must be set before calling this. */
    public void connectToHost(String hostIp) {
        executor.execute(() -> {
            try {
                socket = new Socket(hostIp, PORT);
                outputStream = new DataOutputStream(socket.getOutputStream());
                Logger.d("Connected to host " + hostIp);
                if (listener != null) listener.onConnected();
                handleConnection();
            } catch (IOException e) {
                if (!closed.get()) {
                    Logger.e("Client error: " + e.getMessage());
                    if (listener != null) listener.onConnectionLost();
                }
            }
        });
    }

    /**
     * Length-prefixed framing reader.
     * Each message: [4-byte int length][payload bytes]
     */
    private void handleConnection() {
        executor.execute(() -> {
            try {
                DataInputStream inputStream = new DataInputStream(socket.getInputStream());
                while (!closed.get()) {
                    int length = inputStream.readInt();   // blocks until 4 bytes arrive
                    if (length <= 0 || length > 256 * 1024) {
                        Logger.e("Invalid frame length: " + length);
                        break;
                    }
                    byte[] data = new byte[length];
                    inputStream.readFully(data);          // reads exactly 'length' bytes
                    if (listener != null) listener.onMessageReceived(data);
                }
            } catch (IOException e) {
                if (!closed.get()) {
                    Logger.e("Connection lost: " + e.getMessage());
                }
            } finally {
                if (!closed.get()) {
                    if (listener != null) listener.onConnectionLost();
                }
                close();
            }
        });
    }

    /**
     * Send bytes with length prefix so the receiver can frame correctly.
     */
    public void sendRawBytes(byte[] data) {
        executor.execute(() -> {
            DataOutputStream os = outputStream;  // local ref for thread safety
            Socket s = socket;
            if (s != null && s.isConnected() && os != null && !closed.get()) {
                try {
                    synchronized (os) {
                        os.writeInt(data.length);
                        os.write(data);
                        os.flush();
                    }
                } catch (IOException e) {
                    Logger.e("Error sending bytes: " + e.getMessage());
                }
            }
        });
    }

    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;  // Already closed
        }
        try {
            // Fix #16: Shutdown executor to prevent thread leaks
            executor.shutdownNow();
            if (outputStream != null) {
                try { outputStream.close(); } catch (IOException ignored) {}
                outputStream = null;
            }
            if (socket != null) {
                socket.close();
                socket = null;
            }
            if (serverSocket != null) {
                serverSocket.close();
                serverSocket = null;
            }
        } catch (IOException e) {
            Logger.e("Error closing connections: " + e.getMessage());
        }
    }
}
