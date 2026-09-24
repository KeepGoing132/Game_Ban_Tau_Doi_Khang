package client.network;

import common.protocol.Message;

public interface ServerListener {
    void onConnected();
    void onDisconnected(String reason);
    void onMessageReceived(Message message);
}
