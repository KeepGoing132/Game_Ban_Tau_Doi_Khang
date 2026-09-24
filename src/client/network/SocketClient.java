package client.network;

import common.protocol.Message;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class SocketClient {
    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private Thread listenerThread;
    private volatile boolean connected = false;
    private ServerListener listener;

    public SocketClient(ServerListener listener) {
        this.listener = listener;
    }

    public synchronized boolean connect(String host, int port) {
        try {
            socket = new Socket(host, port);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            connected = true;

            if (listener != null) {
                listener.onConnected();
            }

            listenerThread = new Thread(this::listenLoop, "SocketClient-Listener");
            listenerThread.setDaemon(true);
            listenerThread.start();
            return true;
        } catch (IOException e) {
            connected = false;
            if (listener != null) {
                listener.onDisconnected("Không thể kết nối tới Server: " + e.getMessage());
            }
            return false;
        }
    }

    private void listenLoop() {
        try {
            String line;
            while (connected && (line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                try {
                    Message msg = Message.fromJson(line);
                    if (msg != null && listener != null) {
                        listener.onMessageReceived(msg);
                    }
                } catch (Exception ex) {
                    System.err.println("[SocketClient] Lỗi parse message: " + ex.getMessage());
                }
            }
        } catch (IOException e) {
            // Ngắt kết nối
        } finally {
            disconnect("Mất kết nối với Server.");
        }
    }

    public synchronized void send(Message msg) {
        if (!connected || writer == null || msg == null) return;
        try {
            writer.println(msg.toJson());
            writer.flush();
        } catch (Exception e) {
            System.err.println("[SocketClient] Lỗi gửi tin: " + e.getMessage());
        }
    }

    public synchronized void disconnect(String reason) {
        if (!connected) return;
        connected = false;
        try {
            if (reader != null) reader.close();
            if (writer != null) writer.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {
        }
        if (listener != null) {
            listener.onDisconnected(reason);
        }
    }

    public boolean isConnected() {
        return connected && socket != null && !socket.isClosed();
    }
}
