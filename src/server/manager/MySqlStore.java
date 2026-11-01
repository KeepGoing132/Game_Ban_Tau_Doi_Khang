package server.manager;

import common.protocol.JsonUtil;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;

/**
 * MySQL access layer: short-lived connections, one transaction per commit of accounts and results.
 *
 * <p>Every finished match keeps its full JSON in {@code matches.payload} (result, sealed fleets,
 * shot log) while the columns used for listing - who played, when it ended, who won - are real
 * columns with indexes, so history filtering and keyset paging stay in SQL instead of scanning the
 * table.</p>
 */
final class MySqlStore {
    private static final String ENGINE = " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
    private static final String CHAT_SELECT =
            "SELECT m.*, CASE WHEN a.deleted=1 THEN 'Tài khoản đã xóa' ELSE a.username END AS sender_name,"
                    + " CASE WHEN b.deleted=1 THEN 'Tài khoản đã xóa' ELSE b.username END AS recipient_name"
                    + " FROM chat_messages m JOIN accounts a ON a.user_id=m.sender_id"
                    + " JOIN accounts b ON b.user_id=m.recipient_id ";
    private static final String ACCOUNT_UPSERT =
            "INSERT INTO accounts (user_id,username,password_hash,total_points,total_wins,total_games,blocked_until,deleted)"
                    + " VALUES (?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE username=VALUES(username),"
                    + " password_hash=VALUES(password_hash), total_points=VALUES(total_points), total_wins=VALUES(total_wins),"
                    + " total_games=VALUES(total_games), blocked_until=VALUES(blocked_until), deleted=VALUES(deleted)";
    private static final String MATCH_UPSERT =
            "INSERT INTO matches (match_id,player1_id,player2_id,ended_at,winner_id,player1_name,player2_name,payload)"
                    + " VALUES (?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE player1_id=VALUES(player1_id),"
                    + " player2_id=VALUES(player2_id), ended_at=VALUES(ended_at), winner_id=VALUES(winner_id),"
                    + " player1_name=VALUES(player1_name), player2_name=VALUES(player2_name), payload=VALUES(payload)";

    private final Path directory;

    MySqlStore(Path directory) throws Exception {
        this.directory = directory;
        Database.ensureSchema(directory);
        try (Connection connection = open(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS metadata ("
                    + "meta_key VARCHAR(64) NOT NULL PRIMARY KEY, meta_value VARCHAR(255) NOT NULL)" + ENGINE);
            statement.execute("CREATE TABLE IF NOT EXISTS accounts ("
                    + "user_id INT NOT NULL PRIMARY KEY,"
                    + "username VARCHAR(32) NOT NULL UNIQUE,"
                    + "password_hash VARCHAR(160) NOT NULL,"
                    + "total_points INT NOT NULL,"
                    + "total_wins INT NOT NULL,"
                    + "total_games INT NOT NULL,"
                    + "blocked_until BIGINT NOT NULL DEFAULT 0,"
                    + "deleted TINYINT(1) NOT NULL DEFAULT 0,"
                    + "CONSTRAINT accounts_points CHECK (total_points >= 0))" + ENGINE);
            statement.execute("CREATE TABLE IF NOT EXISTS matches ("
                    + "seq BIGINT NOT NULL AUTO_INCREMENT,"          // insertion order, the paging watermark
                    + "match_id VARCHAR(128) NOT NULL,"
                    + "player1_id INT NOT NULL,"
                    + "player2_id INT NOT NULL,"
                    + "ended_at BIGINT NOT NULL DEFAULT 0,"
                    + "winner_id INT NOT NULL,"
                    + "player1_name VARCHAR(64) NOT NULL DEFAULT '',"
                    + "player2_name VARCHAR(64) NOT NULL DEFAULT '',"
                    + "payload LONGTEXT NOT NULL,"
                    + "PRIMARY KEY (match_id),"
                    + "UNIQUE KEY match_seq (seq),"
                    + "KEY match_history_p1 (player1_id, ended_at, match_id),"
                    + "KEY match_history_p2 (player2_id, ended_at, match_id))" + ENGINE);
            statement.execute("CREATE TABLE IF NOT EXISTS chat_messages ("
                    + "message_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,"
                    + "sender_id INT NOT NULL,"
                    + "recipient_id INT NOT NULL,"
                    + "body VARCHAR(500) NOT NULL,"
                    + "sent_at_ms BIGINT NOT NULL,"
                    + "client_message_id VARCHAR(64) NOT NULL,"
                    + "pair_low INT NOT NULL,"
                    + "pair_high INT NOT NULL,"
                    + "UNIQUE KEY chat_client (sender_id, client_message_id),"
                    + "KEY chat_pair_history (pair_low, pair_high, message_id),"
                    + "KEY chat_sender (sender_id, message_id),"
                    + "KEY chat_recipient (recipient_id, message_id),"
                    + "CONSTRAINT chat_distinct CHECK (sender_id <> recipient_id),"
                    + "CONSTRAINT chat_body CHECK (CHAR_LENGTH(body) BETWEEN 1 AND 500))" + ENGINE);
        }
    }

    private Connection open() throws SQLException {
        return Database.open(directory);
    }

    /** True when this schema was already initialised by a previous run. */
    boolean initialized() throws SQLException {
        try (Connection connection = open();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT meta_value FROM metadata WHERE meta_key='schema_version'")) {
            if (!rows.next()) {
                return false;
            }
            if (!"1".equals(rows.getString(1))) {
                throw new SQLException("Unsupported database version");
            }
            return true;
        }
    }

    private Map<String, Object> chatRow(ResultSet row) throws SQLException {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("message_id", row.getLong("message_id"));
        message.put("sender_id", row.getInt("sender_id"));
        message.put("recipient_id", row.getInt("recipient_id"));
        message.put("sender_name", row.getString("sender_name"));
        message.put("recipient_name", row.getString("recipient_name"));
        message.put("body", row.getString("body"));
        message.put("sent_at_ms", row.getLong("sent_at_ms"));
        message.put("client_message_id", row.getString("client_message_id"));
        return message;
    }

    /**
     * Stores one chat message. The unique key (sender, client_message_id) makes a retry idempotent:
     * the duplicate branch changes nothing, so {@code _created} stays false for a resend.
     */
    Map<String, Object> saveChat(int sender, int recipient, String body, String clientId, long time) throws SQLException {
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try {
                boolean created;
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO chat_messages (sender_id,recipient_id,body,sent_at_ms,client_message_id,pair_low,pair_high)"
                                + " VALUES (?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE message_id=message_id")) {
                    insert.setInt(1, sender);
                    insert.setInt(2, recipient);
                    insert.setString(3, body);
                    insert.setLong(4, time);
                    insert.setString(5, clientId);
                    insert.setInt(6, Math.min(sender, recipient));
                    insert.setInt(7, Math.max(sender, recipient));
                    created = insert.executeUpdate() == 1;
                }
                Map<String, Object> saved;
                try (PreparedStatement select = connection.prepareStatement(CHAT_SELECT + "WHERE m.sender_id=? AND m.client_message_id=?")) {
                    select.setInt(1, sender);
                    select.setString(2, clientId);
                    try (ResultSet rows = select.executeQuery()) {
                        if (!rows.next()) {
                            throw new SQLException("Chat insert failed");
                        }
                        saved = chatRow(rows);
                    }
                }
                if (((Number) saved.get("recipient_id")).intValue() != recipient || !body.equals(saved.get("body"))) {
                    throw new DomainException("INVALID_MESSAGE", "Mã tin nhắn đã được sử dụng cho nội dung khác");
                }
                connection.commit();
                saved.put("_created", created);
                return saved;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    List<Map<String, Object>> chatHistory(int user, int peer, long before) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection connection = open();
             PreparedStatement select = connection.prepareStatement(
                     CHAT_SELECT + "WHERE m.pair_low=? AND m.pair_high=? AND m.message_id<? ORDER BY m.message_id DESC LIMIT 21")) {
            select.setInt(1, Math.min(user, peer));
            select.setInt(2, Math.max(user, peer));
            select.setLong(3, before == 0 ? Long.MAX_VALUE : before);
            try (ResultSet result = select.executeQuery()) {
                while (result.next()) {
                    rows.add(chatRow(result));
                }
            }
        }
        return rows;
    }

    List<Map<String, Object>> chatConversations(int user) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        String sql = "SELECT t.peer_id,a.username,a.deleted,m.body,m.sent_at_ms,m.message_id"
                + " FROM (SELECT CASE WHEN sender_id=? THEN recipient_id ELSE sender_id END AS peer_id,"
                + " MAX(message_id) AS last_id FROM chat_messages WHERE sender_id=? OR recipient_id=?"
                + " GROUP BY peer_id ORDER BY last_id DESC LIMIT 100) t"
                + " JOIN accounts a ON a.user_id=t.peer_id"
                + " JOIN chat_messages m ON m.message_id=t.last_id"
                + " ORDER BY m.message_id DESC";
        try (Connection connection = open(); PreparedStatement select = connection.prepareStatement(sql)) {
            select.setInt(1, user);
            select.setInt(2, user);
            select.setInt(3, user);
            try (ResultSet result = select.executeQuery()) {
                while (result.next()) {
                    Map<String, Object> conversation = new LinkedHashMap<>();
                    boolean available = !result.getBoolean("deleted");
                    conversation.put("user_id", result.getInt("peer_id"));
                    conversation.put("username", available ? result.getString("username") : "Tài khoản đã xóa");
                    conversation.put("available", available);
                    conversation.put("last_message_id", result.getLong("message_id"));
                    conversation.put("sent_at_ms", result.getLong("sent_at_ms"));
                    rows.add(conversation);
                }
            }
        }
        return rows;
    }

    /**
     * One page of history: filters and keyset paging run in SQL, and the sequence watermark keeps
     * matches finished after the first page from shifting the window.
     */
    Map<String, Object> history(int target, HistoryQuery query) throws SQLException {
        try (Connection connection = open()) {
            long snapshot = query.snapshot;
            if (snapshot == 0) {
                try (Statement statement = connection.createStatement();
                     ResultSet rows = statement.executeQuery("SELECT COALESCE(MAX(seq),0) FROM matches")) {
                    if (rows.next()) {
                        snapshot = rows.getLong(1);
                    }
                }
            }
            StringBuilder sql = new StringBuilder("SELECT match_id,ended_at FROM matches"
                    + " WHERE (player1_id=? OR player2_id=?) AND seq<=? AND ended_at>=? AND ended_at<=?");
            if (!"ALL".equals(query.result)) {
                sql.append(" AND winner_id").append("WIN".equals(query.result) ? "=" : "<>").append("?");
            }
            if (!query.opponent.isEmpty()) {
                sql.append(" AND INSTR(LOWER(CASE WHEN player1_id=? THEN player2_name ELSE player1_name END),?)>0");
            }
            if (!query.beforeId.isEmpty()) {
                sql.append(" AND (ended_at<? OR (ended_at=? AND match_id<?))");
            }
            sql.append(" ORDER BY ended_at DESC,match_id DESC LIMIT ?");

            List<String> ids = new ArrayList<>();
            long lastTime = 0;
            String lastId = "";
            try (PreparedStatement select = connection.prepareStatement(sql.toString())) {
                int index = 1;
                select.setInt(index++, target);
                select.setInt(index++, target);
                select.setLong(index++, snapshot);
                select.setLong(index++, query.from);
                select.setLong(index++, query.to);
                if (!"ALL".equals(query.result)) {
                    select.setInt(index++, target);
                }
                if (!query.opponent.isEmpty()) {
                    select.setInt(index++, target);
                    select.setString(index++, query.opponent);
                }
                if (!query.beforeId.isEmpty()) {
                    select.setLong(index++, query.beforeTime);
                    select.setLong(index++, query.beforeTime);
                    select.setString(index++, query.beforeId);
                }
                select.setInt(index, query.limit + 1);
                try (ResultSet rows = select.executeQuery()) {
                    while (rows.next()) {
                        ids.add(rows.getString("match_id"));
                        if (ids.size() <= query.limit) {
                            lastTime = rows.getLong("ended_at");
                            lastId = rows.getString("match_id");
                        }
                    }
                }
            }
            boolean more = ids.size() > query.limit;
            if (more) {
                ids.remove(ids.size() - 1);
            }
            Map<String, Object> page = new LinkedHashMap<>();
            page.put("ids", ids);
            page.put("has_more", more);
            page.put("next_cursor", more ? query.cursor(snapshot, lastTime, lastId) : "");
            return page;
        }
    }

    /** Loads every account and finished match of this schema. */
    Map<String, Object> read() throws SQLException {
        List<Object> users = new ArrayList<>();
        List<Object> matches = new ArrayList<>();
        try (Connection connection = open(); Statement statement = connection.createStatement()) {
            try (ResultSet rows = statement.executeQuery("SELECT * FROM accounts ORDER BY user_id")) {
                while (rows.next()) {
                    Map<String, Object> user = new LinkedHashMap<>();
                    user.put("userId", rows.getInt("user_id"));
                    user.put("username", rows.getString("username"));
                    user.put("passwordHash", rows.getString("password_hash"));
                    user.put("totalPoints", rows.getInt("total_points"));
                    user.put("totalWins", rows.getInt("total_wins"));
                    user.put("totalGames", rows.getInt("total_games"));
                    user.put("matchmakingBlockedUntil", rows.getLong("blocked_until"));
                    user.put("deleted", rows.getBoolean("deleted"));
                    users.add(user);
                }
            }
            try (ResultSet rows = statement.executeQuery("SELECT payload FROM matches ORDER BY seq")) {
                while (rows.next()) {
                    matches.add(JsonUtil.parseObject(rows.getString(1)));
                }
            }
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("users", users);
        root.put("matches", matches);
        return root;
    }

    /** Accounts and results commit together, so a failure never leaves half-applied statistics. */
    void write(List<Map<String, Object>> users, List<Object> records) throws IOException {
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement upsert = connection.prepareStatement(ACCOUNT_UPSERT)) {
                    for (Map<String, Object> user : users) {
                        upsert.setInt(1, ((Number) user.get("userId")).intValue());
                        upsert.setString(2, (String) user.get("username"));
                        upsert.setString(3, (String) user.get("passwordHash"));
                        upsert.setInt(4, ((Number) user.get("totalPoints")).intValue());
                        upsert.setInt(5, ((Number) user.get("totalWins")).intValue());
                        upsert.setInt(6, ((Number) user.get("totalGames")).intValue());
                        upsert.setLong(7, ((Number) user.get("matchmakingBlockedUntil")).longValue());
                        upsert.setBoolean(8, Boolean.TRUE.equals(user.get("deleted")));
                        upsert.addBatch();
                    }
                    upsert.executeBatch();
                }
                try (PreparedStatement upsert = connection.prepareStatement(MATCH_UPSERT)) {
                    for (Object item : records) {
                        @SuppressWarnings("unchecked") Map<String, Object> record = (Map<String, Object>) item;
                        long ended = number(record, "endedAt");
                        upsert.setString(1, (String) record.get("matchId"));
                        upsert.setInt(2, ((Number) record.get("player1Id")).intValue());
                        upsert.setInt(3, ((Number) record.get("player2Id")).intValue());
                        upsert.setLong(4, ended > 0 ? ended : number(record, "timestamp"));
                        upsert.setInt(5, ((Number) record.get("winnerId")).intValue());
                        upsert.setString(6, text(record, "player1Name"));
                        upsert.setString(7, text(record, "player2Name"));
                        upsert.setString(8, JsonUtil.toJson(record));
                        upsert.addBatch();
                    }
                    upsert.executeBatch();
                }
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("INSERT INTO metadata (meta_key,meta_value) VALUES ('schema_version','1')"
                            + " ON DUPLICATE KEY UPDATE meta_value=VALUES(meta_value)");
                }
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        } catch (Exception e) {
            throw new IOException("Database transaction failed: " + e.getMessage(), e);
        }
    }

    private static long number(Map<String, Object> record, String key) {
        Object value = record.get(key);
        return value instanceof Number ? ((Number) value).longValue() : 0;
    }

    private static String text(Map<String, Object> record, String key) {
        Object value = record.get(key);
        String text = value == null ? "" : String.valueOf(value);
        return text.length() > 64 ? text.substring(0, 64) : text;
    }
}
