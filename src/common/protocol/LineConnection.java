package common.protocol;

import common.config.GameConfig;
import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Bounded UTF-8 line framing and asynchronous, ordered socket writes. */
public final class LineConnection implements AutoCloseable {
    private final Socket socket;
    private final InputStream input;
    private final BlockingQueue<byte[]> outgoing = new ArrayBlockingQueue<>(GameConfig.MAX_PENDING_MESSAGES);
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile long lastValidNanos = System.nanoTime();
    private volatile long writeStarted;
    private final ScheduledExecutorService watchdog;
    private final Thread writer;

    public LineConnection(Socket socket) throws IOException {
        this.socket = socket;
        socket.setTcpNoDelay(true);
        input = new BufferedInputStream(socket.getInputStream());
        writer = new Thread(this::writeLoop, "TCP-writer-" + socket.getPort());
        writer.setDaemon(true);
        watchdog = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "TCP-watchdog-" + socket.getPort()); t.setDaemon(true); return t;
        });
        watchdog.scheduleAtFixedRate(() -> {
            long now = System.nanoTime();
            long limit = TimeUnit.MILLISECONDS.toNanos(GameConfig.HEARTBEAT_TIMEOUT_MS);
            if (now - lastValidNanos >= limit || (writeStarted != 0 && now - writeStarted >= limit)) close();
        }, 100, 100, TimeUnit.MILLISECONDS);
        writer.start();
    }
    public void touch() { lastValidNanos = System.nanoTime(); }
    public String readLine() throws IOException {
        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        while (!closed.get()) {
            int c = input.read();
            if (c == -1) {
                if (frame.size() != 0) throw new EOFException("Incomplete frame");
                return null;
            }
            if (c == '\n') {
                CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
                return decoder.decode(ByteBuffer.wrap(frame.toByteArray())).toString();
            }
            if (frame.size() >= GameConfig.MAX_FRAME_BYTES) throw new IOException("Frame exceeds 64 KiB");
            frame.write(c);
        }
        return null;
    }
    public boolean send(Message message) {
        if (closed.get()) return false;
        byte[] bytes = (message.toJson() + "\n").getBytes(StandardCharsets.UTF_8);
        if (bytes.length > GameConfig.MAX_FRAME_BYTES + 1 || !outgoing.offer(bytes)) { close(); return false; }
        return true;
    }
    private void writeLoop() {
        try {
            OutputStream output = socket.getOutputStream();
            while (!closed.get()) {
                byte[] bytes = outgoing.take();
                writeStarted = System.nanoTime();
                output.write(bytes); output.flush();
                writeStarted = 0;
            }
        } catch (IOException | InterruptedException e) { close(); }
    }
    public boolean isOpen() { return !closed.get(); }
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        try { socket.close(); } catch (IOException ignored) { }
        writer.interrupt();
        if (watchdog != null) watchdog.shutdownNow();
        outgoing.clear();
    }
}
