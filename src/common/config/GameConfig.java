package common.config;

public class GameConfig {
    public static final int DEFAULT_PORT = 8888;
    public static final String DEFAULT_HOST = "localhost";
    
    public static final int BOARD_SIZE = 10;
    public static final int TURN_TIMEOUT_SECONDS = 15;
    
    public static final int WIN_POINTS = 3;
    public static final int LOSE_POINTS = 0;
    
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
