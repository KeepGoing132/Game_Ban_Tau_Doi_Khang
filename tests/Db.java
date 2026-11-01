package tests;

import server.manager.Database;
import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * MySQL helpers for the tests. Every test data directory owns its own schema (the same rule the
 * server uses), so a test can create, inspect and drop its database without touching the real data.
 */
public final class Db {
    private static final String TEST_SCHEMA_PREFIX = "battleship_battleship_";

    private Db() {
    }

    public static Connection open(Path directory) throws SQLException {
        return Database.open(directory);
    }

    public static void dropSchema(Path directory) throws SQLException {
        Database.dropSchema(directory);
    }

    /** Password hash currently stored for one account, or null when the row is gone. */
    public static String passwordHash(Path directory, int userId) throws SQLException {
        try (Connection connection = open(directory);
             PreparedStatement select = connection.prepareStatement("SELECT password_hash FROM accounts WHERE user_id=?")) {
            select.setInt(1, userId);
            try (ResultSet rows = select.executeQuery()) {
                return rows.next() ? rows.getString(1) : null;
            }
        }
    }

    /** True when the given text appears in any stored account or match payload. */
    public static boolean storedTextContains(Path directory, String text) throws SQLException {
        try (Connection connection = open(directory)) {
            try (PreparedStatement select = connection.prepareStatement("SELECT COUNT(*) FROM accounts WHERE password_hash LIKE ?")) {
                select.setString(1, "%" + text + "%");
                try (ResultSet rows = select.executeQuery()) {
                    if (rows.next() && rows.getInt(1) > 0) {
                        return true;
                    }
                }
            }
            try (PreparedStatement select = connection.prepareStatement("SELECT COUNT(*) FROM matches WHERE payload LIKE ?")) {
                select.setString(1, "%" + text + "%");
                try (ResultSet rows = select.executeQuery()) {
                    return rows.next() && rows.getInt(1) > 0;
                }
            }
        }
    }

    /** Table names of the schema this directory maps to. */
    public static List<String> tables(Path directory) throws SQLException {
        List<String> names = new ArrayList<>();
        try (Connection connection = open(directory);
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() ORDER BY table_name")) {
            while (rows.next()) {
                names.add(rows.getString(1).toLowerCase(java.util.Locale.ROOT));
            }
        }
        return names;
    }

    /** Fails a chat insert, standing in for a broken disk; the trigger is dropped by the caller. */
    public static void breakChatWrites(Path directory, String triggerName) throws SQLException {
        try (Connection connection = open(directory); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TRIGGER " + triggerName + " BEFORE INSERT ON chat_messages FOR EACH ROW"
                    + " SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'test write failure'");
        }
    }

    public static void restoreChatWrites(Path directory, String triggerName) throws SQLException {
        try (Connection connection = open(directory); Statement statement = connection.createStatement()) {
            statement.execute("DROP TRIGGER IF EXISTS " + triggerName);
        }
    }

    /**
     * Drops the schemas left behind by earlier test runs (they are all named after their temporary
     * directory). Never touches the production schema.
     */
    public static void cleanupTestSchemas() {
        int dropped = 0;
        try (Connection server = Database.openServer(); Statement statement = server.createStatement()) {
            List<String> stale = new ArrayList<>();
            try (ResultSet rows = statement.executeQuery("SHOW DATABASES")) {
                while (rows.next()) {
                    String name = rows.getString(1);
                    if (name.startsWith(TEST_SCHEMA_PREFIX)) {
                        stale.add(name);
                    }
                }
            }
            for (String name : stale) {
                statement.executeUpdate("DROP DATABASE IF EXISTS `" + name + "`");
                dropped++;
            }
        } catch (SQLException e) {
            throw new AssertionError("MySQL không sẵn sàng cho kiểm thử: " + e.getMessage()
                    + "\nHãy chạy MySQL và khai báo kết nối trong db.properties (xem db.properties.example).", e);
        }
        if (dropped > 0) {
            System.out.println("MySQL: đã dọn " + dropped + " schema kiểm thử còn lại");
        }
    }
}
