package server;

import common.config.GameConfig;
import server.network.GameServer;

public class ServerMain {
    public static void main(String[] args) {
        int port = GameConfig.DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
            }
        }

        GameServer server = new GameServer(port);

        // Đăng ký shutdown hook để dọn dẹp khi tắt server
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));

        server.start();
    }
}
