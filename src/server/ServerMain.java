package server;

import common.config.GameConfig;
import server.network.GameServer;

public class ServerMain {
    public static void main(String[] args) {
        int port = GameConfig.DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
                if(port<0 || port>65535)throw new NumberFormatException();
            } catch (NumberFormatException invalid) {
                System.err.println("Usage: server.ServerMain [port 0..65535]");
                System.exit(1);
                return;
            }
        }

        GameServer server = new GameServer(port);

        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));

        server.start();
    }
}
