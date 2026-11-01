package client.network;

import common.config.GameConfig;
import common.protocol.*;
import java.io.*;
import java.net.*;
import java.util.UUID;
import java.util.concurrent.*;
import javax.swing.SwingUtilities;

/**
 * Owns the socket, the reader thread, the heartbeat and the connection generation.
 *
 * <p>Every callback is routed through {@link #dispatch}, which drops anything produced by a
 * previous connection: a reconnect therefore never resurrects callbacks of the session it
 * replaced. The heartbeat also measures the round-trip time the UI shows as the link state.</p>
 */
public class SocketClient {
    private final ServerListener listener;
    private volatile Session current;
    private volatile long generation;

    /** One live connection: its own generation token, wire, heartbeat clock and last measurement. */
    private static final class Session {
        final long generation;
        final LineConnection wire;
        final ScheduledExecutorService heartbeat;
        volatile String heartbeatId;
        volatile long heartbeatSentNanos;

        Session(long generation, LineConnection wire) {
            this.generation = generation;
            this.wire = wire;
            this.heartbeat = Executors.newSingleThreadScheduledExecutor(task -> {
                Thread thread = new Thread(task, "client-heartbeat");
                thread.setDaemon(true);
                return thread;
            });
        }

        void close() {
            heartbeat.shutdownNow();
            wire.close();
        }
    }

    public SocketClient(ServerListener listener) {
        this.listener = listener;
    }

    /** Opens a connection; false means the server could not be reached. */
    public boolean connect(String host, int port) {
        final long token;
        final Session previous;
        synchronized (this) {
            token = ++generation;
            previous = current;
            current = null;
        }
        if (previous != null) {
            previous.close();
        }
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host, port), 5000);
            Session session = new Session(token, new LineConnection(socket));
            synchronized (this) {
                if (generation != token) {
                    // a newer connection was requested while this one was still dialling
                    session.close();
                    return false;
                }
                current = session;
            }
            dispatch(session, () -> listener.onConnected());
            session.heartbeat.scheduleAtFixedRate(() -> {
                String id = UUID.randomUUID().toString();
                session.heartbeatSentNanos = System.nanoTime();
                session.heartbeatId = id;
                session.wire.send(new Message(MessageType.HEARTBEAT_REQ).requestId(id));
            }, 0, GameConfig.HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);
            Thread reader = new Thread(() -> listen(session), "client-reader-" + token);
            reader.setDaemon(true);
            reader.start();
            return true;
        } catch (IOException e) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // the socket never opened
            }
            return false;
        }
    }

    /** Delivers a callback only while the session it belongs to is still the current one. */
    private void dispatch(Session session, Runnable action) {
        SwingUtilities.invokeLater(() -> {
            if (current == session && generation == session.generation) {
                action.run();
            }
        });
    }

    private void listen(Session session) {
        try {
            String line;
            while ((line = session.wire.readLine()) != null) {
                Message message = Message.fromJson(line);
                if (message == null) {
                    continue;
                }
                session.wire.touch();
                if (message.getType() != MessageType.HEARTBEAT_RESP) {
                    dispatch(session, () -> listener.onMessageReceived(message));
                    continue;
                }
                // only the echo of the last heartbeat counts, so a late answer cannot skew the figure
                if (message.getRequestId() != null && message.getRequestId().equals(session.heartbeatId)) {
                    long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - session.heartbeatSentNanos);
                    session.heartbeatId = null;
                    dispatch(session, () -> listener.onLatencyMeasured(millis));
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            // malformed frames and EOF both end this connection; the UI is told below
        } finally {
            lost(session, "Mất kết nối với máy chủ");
        }
    }

    private void lost(Session session, String reason) {
        session.close();
        final long token;
        synchronized (this) {
            if (current != session) {
                return;
            }
            current = null;
            token = generation;
        }
        SwingUtilities.invokeLater(() -> {
            if (generation == token && current == null) {
                listener.onDisconnected(reason);
            }
        });
    }

    public void send(Message message) {
        Session session = current;
        if (session == null || message == null) {
            return;
        }
        if (message.getRequestId() == null) {
            message.requestId(UUID.randomUUID().toString());
        }
        session.wire.send(message);
    }

    public void disconnect(String reason) {
        final Session session;
        synchronized (this) {
            ++generation;
            session = current;
            current = null;
        }
        if (session != null) {
            Thread close = new Thread(session::close, "client-close");
            close.setDaemon(true);
            close.start();
        }
    }

    public boolean isConnected() {
        Session session = current;
        return session != null && session.wire.isOpen();
    }
}
