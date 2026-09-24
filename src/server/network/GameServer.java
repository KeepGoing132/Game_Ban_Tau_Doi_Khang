package server.network;

import common.config.GameConfig;
import server.manager.LobbyManager;
import server.manager.MatchManager;
import server.manager.StorageManager;
import server.manager.UserManager;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GameServer {
    private final int port;
    private ServerSocket serverSocket;
    private volatile boolean running = false;
    private final ExecutorService threadPool = Executors.newCachedThreadPool();

    private final StorageManager storageManager;
    private final UserManager userManager;
    private final MatchManager matchManager;
    private final LobbyManager lobbyManager;

    public GameServer(int port) {
        this.port = port;
        this.storageManager = new StorageManager();
        this.userManager = new UserManager(storageManager);
        this.matchManager = new MatchManager(userManager, storageManager);
        this.lobbyManager = new LobbyManager(userManager, matchManager);
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            running = true;
            System.out.println("=================================================");
            System.out.println("  BATTLESHIP GAME SERVER ĐANG CHẠY TRÊN CỔNG: " + port);
            System.out.println("  Giao thức: TCP Socket (Line-delimited JSON)");
            System.out.println("  Chế độ: Đa luồng (Multi-threaded Worker)");
            System.out.println("=================================================");

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("[Server] Có kết nối mới từ: " + clientSocket.getRemoteSocketAddress());
                    ClientHandler handler = new ClientHandler(clientSocket, userManager, lobbyManager, matchManager);
                    threadPool.execute(handler);
                } catch (IOException e) {
                    if (!running) break;
                    System.err.println("[Server] Lỗi chấp nhận kết nối: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("[Server] Không thể mở cổng " + port + ": " + e.getMessage());
        } finally {
            stop();
        }
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
        }
        threadPool.shutdownNow();
        System.out.println("[Server] Server đã dừng.");
    }
}
