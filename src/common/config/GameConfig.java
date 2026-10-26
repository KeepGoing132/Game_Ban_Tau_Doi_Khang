package common.config;

public class GameConfig {
    public static final int DEFAULT_PORT = 8888;
    public static final String DEFAULT_HOST = "localhost";
    
    public static final int BOARD_SIZE = 10;
    public static final int TURN_TIMEOUT_SECONDS = 15;
    public static final long TURN_TIMEOUT_MS = Long.getLong("battleship.turn_ms", 15000L);
    public static final long PLACEMENT_TIMEOUT_MS = Long.getLong("battleship.placement_ms", 120000L);
    public static final long SLOW_CONNECTION_MS = Long.getLong("battleship.slow_connection_ms", 250L);
    public static final long HEARTBEAT_INTERVAL_MS = Long.getLong("battleship.heartbeat_ms", 5000L);
    public static final long HEARTBEAT_TIMEOUT_MS = Long.getLong("battleship.idle_ms", 20000L);
    public static final long INVITE_TIMEOUT_MS = Long.getLong("battleship.invite_ms", 30000L);
    public static final long REMATCH_TIMEOUT_MS = Long.getLong("battleship.rematch_ms", 30000L);
    public static final int MAX_FRAME_BYTES = 65536;
    public static final int MAX_CONNECTIONS = 128;
    public static final int MAX_PENDING_MESSAGES = 256;
    static {
        if (TURN_TIMEOUT_MS<=0 || PLACEMENT_TIMEOUT_MS<=0 || SLOW_CONNECTION_MS<=0 || HEARTBEAT_INTERVAL_MS<=0 || HEARTBEAT_TIMEOUT_MS<=0 || INVITE_TIMEOUT_MS<=0 || REMATCH_TIMEOUT_MS<=0)
            throw new IllegalArgumentException("Battleship timing properties must be positive");
    }
    
    public static final int WIN_POINTS = 3;
    public static final int LOSE_POINTS = 0;
    public static final int FORFEIT_PENALTY_POINTS = 3;
    public static final long MATCHMAKING_PENALTY_MS = 60_000L;
    
    // Tổng cộng 5 tàu, 17 ô
    // 1 tàu 5 ô, 1 tàu 4 ô, 2 tàu 3 ô, 1 tàu 2 ô
    public static final int[] SHIP_SIZES = {5, 4, 3, 3, 2};
    public static final String[] SHIP_NAMES = {
        "Carrier (5 ô)",
        "Battleship (4 ô)",
        "Cruiser 1 (3 ô)",
        "Cruiser 2 (3 ô)",
        "Destroyer (2 ô)"
    };
    public static final int TOTAL_SHIP_CELLS = 17;
}
