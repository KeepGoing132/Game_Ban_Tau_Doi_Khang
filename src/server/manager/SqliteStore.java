package server.manager;

import common.protocol.JsonUtil;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;

final class SqliteStore {
    private static final String HISTORY_TIME = "CASE WHEN COALESCE(json_extract(payload,'$.endedAt'),0)>0 THEN json_extract(payload,'$.endedAt') ELSE COALESCE(json_extract(payload,'$.timestamp'),0) END";
    private final String url;
    SqliteStore(Path file) throws Exception {
        Class.forName("org.sqlite.JDBC");url="jdbc:sqlite:"+file.toAbsolutePath();
        try(Connection c=open();Statement s=c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS accounts (user_id INTEGER PRIMARY KEY, username TEXT NOT NULL UNIQUE, password_hash TEXT NOT NULL, total_points INTEGER NOT NULL CHECK(total_points>=0), total_wins INTEGER NOT NULL, total_games INTEGER NOT NULL, blocked_until INTEGER NOT NULL DEFAULT 0, deleted INTEGER NOT NULL DEFAULT 0)");
            s.execute("CREATE TABLE IF NOT EXISTS matches (match_id TEXT PRIMARY KEY, player1_id INTEGER NOT NULL, player2_id INTEGER NOT NULL, payload TEXT NOT NULL)");
            s.execute("CREATE INDEX IF NOT EXISTS match_history_p1 ON matches(player1_id,("+HISTORY_TIME+") DESC,match_id DESC)");
            s.execute("CREATE INDEX IF NOT EXISTS match_history_p2 ON matches(player2_id,("+HISTORY_TIME+") DESC,match_id DESC)");
            s.execute("CREATE TABLE IF NOT EXISTS chat_messages (message_id INTEGER PRIMARY KEY AUTOINCREMENT, sender_id INTEGER NOT NULL, recipient_id INTEGER NOT NULL, body TEXT NOT NULL CHECK(length(body) BETWEEN 1 AND 500), sent_at_ms INTEGER NOT NULL, client_message_id TEXT NOT NULL, pair_low INTEGER NOT NULL, pair_high INTEGER NOT NULL, UNIQUE(sender_id,client_message_id), CHECK(sender_id<>recipient_id))");
            s.execute("CREATE INDEX IF NOT EXISTS chat_pair_history ON chat_messages(pair_low,pair_high,message_id)");
            s.execute("CREATE INDEX IF NOT EXISTS chat_sender ON chat_messages(sender_id,message_id)");
            s.execute("CREATE INDEX IF NOT EXISTS chat_recipient ON chat_messages(recipient_id,message_id)");
        }
    }
    private static final String CHAT_SELECT="SELECT m.*, CASE WHEN a.deleted=1 THEN 'Tài khoản đã xóa' ELSE a.username END AS sender_name, CASE WHEN b.deleted=1 THEN 'Tài khoản đã xóa' ELSE b.username END AS recipient_name FROM chat_messages m JOIN accounts a ON a.user_id=m.sender_id JOIN accounts b ON b.user_id=m.recipient_id ";
    private Map<String,Object> chatRow(ResultSet r)throws SQLException {
        Map<String,Object> m=new LinkedHashMap<>();
        m.put("message_id",r.getLong("message_id"));m.put("sender_id",r.getInt("sender_id"));m.put("recipient_id",r.getInt("recipient_id"));
        m.put("sender_name",r.getString("sender_name"));m.put("recipient_name",r.getString("recipient_name"));
        m.put("body",r.getString("body"));m.put("sent_at_ms",r.getLong("sent_at_ms"));m.put("client_message_id",r.getString("client_message_id"));return m;
    }
    Map<String,Object> saveChat(int sender,int recipient,String body,String clientId,long time)throws SQLException {
        try(Connection c=open()) {
            c.setAutoCommit(false);
            try {
                boolean created;
                try(PreparedStatement p=c.prepareStatement("INSERT OR IGNORE INTO chat_messages(sender_id,recipient_id,body,sent_at_ms,client_message_id,pair_low,pair_high) VALUES(?,?,?,?,?,?,?)")) {
                    p.setInt(1,sender);p.setInt(2,recipient);p.setString(3,body);p.setLong(4,time);p.setString(5,clientId);p.setInt(6,Math.min(sender,recipient));p.setInt(7,Math.max(sender,recipient));created=p.executeUpdate()==1;
                }
                Map<String,Object> saved;
                try(PreparedStatement p=c.prepareStatement(CHAT_SELECT+"WHERE m.sender_id=? AND m.client_message_id=?")) {
                    p.setInt(1,sender);p.setString(2,clientId);
                    try(ResultSet r=p.executeQuery()){if(!r.next())throw new SQLException("Chat insert failed");saved=chatRow(r);}
                }
                if(((Number)saved.get("recipient_id")).intValue()!=recipient || !body.equals(saved.get("body")))throw new DomainException("INVALID_MESSAGE","Mã tin nhắn đã được sử dụng cho nội dung khác");
                c.commit();saved.put("_created",created);return saved;
            }catch(SQLException|RuntimeException e){c.rollback();throw e;}
        }
    }
    List<Map<String,Object>> chatHistory(int user,int peer,long before)throws SQLException {
        List<Map<String,Object>> rows=new ArrayList<>();
        try(Connection c=open();PreparedStatement p=c.prepareStatement(CHAT_SELECT+"WHERE m.pair_low=? AND m.pair_high=? AND m.message_id<? ORDER BY m.message_id DESC LIMIT 21")) {
            p.setInt(1,Math.min(user,peer));p.setInt(2,Math.max(user,peer));p.setLong(3,before==0?Long.MAX_VALUE:before);
            try(ResultSet r=p.executeQuery()){while(r.next())rows.add(chatRow(r));}
        }
        return rows;
    }
    List<Map<String,Object>> chatConversations(int user)throws SQLException {
        List<Map<String,Object>> rows=new ArrayList<>();
        String sql="SELECT t.peer_id,a.username,a.deleted,m.body,m.sent_at_ms,m.message_id FROM (SELECT CASE WHEN sender_id=? THEN recipient_id ELSE sender_id END AS peer_id,MAX(message_id) AS last_id FROM chat_messages WHERE sender_id=? OR recipient_id=? GROUP BY peer_id ORDER BY last_id DESC LIMIT 100) t JOIN accounts a ON a.user_id=t.peer_id JOIN chat_messages m ON m.message_id=t.last_id ORDER BY m.message_id DESC";
        try(Connection c=open();PreparedStatement p=c.prepareStatement(sql)) {
            p.setInt(1,user);p.setInt(2,user);p.setInt(3,user);
            try(ResultSet r=p.executeQuery()){while(r.next()) {
                Map<String,Object> m=new LinkedHashMap<>();boolean available=!r.getBoolean("deleted");m.put("user_id",r.getInt("peer_id"));m.put("username",available?r.getString("username"):"Tài khoản đã xóa");m.put("available",available);m.put("last_message_id",r.getLong("message_id"));m.put("sent_at_ms",r.getLong("sent_at_ms"));rows.add(m);
            }}
        }return rows;
    }
    private Connection open() throws SQLException {
        Connection c=DriverManager.getConnection(url);
        try(Statement s=c.createStatement()){s.execute("PRAGMA busy_timeout=5000");s.execute("PRAGMA synchronous=FULL");s.execute("PRAGMA secure_delete=ON");}
        return c;
    }
    Map<String,Object> history(int target, HistoryQuery query) throws SQLException {
        try (Connection c=open()) {
            long snapshot=query.snapshot;
            if (snapshot==0) try (Statement s=c.createStatement(); ResultSet r=s.executeQuery("SELECT COALESCE(MAX(rowid),0) FROM matches")) { if (r.next()) snapshot=r.getLong(1); }
            String sql="SELECT match_id,("+HISTORY_TIME+") AS ended FROM matches WHERE (player1_id=? OR player2_id=?) AND rowid<=? AND ("+HISTORY_TIME+")>=? AND ("+HISTORY_TIME+")<=?";
            if (!"ALL".equals(query.result)) sql+=" AND json_extract(payload,'$.winnerId')"+("WIN".equals(query.result)?"=":"<>")+"?";
            if (!query.opponent.isEmpty()) sql+=" AND instr(lower(CASE WHEN player1_id=? THEN json_extract(payload,'$.player2Name') ELSE json_extract(payload,'$.player1Name') END),?)>0";
            if (!query.beforeId.isEmpty()) sql+=" AND (("+HISTORY_TIME+")<? OR (("+HISTORY_TIME+")=? AND match_id<?))";
            sql+=" ORDER BY ended DESC,match_id DESC LIMIT ?";
            List<String> ids=new ArrayList<>(); long lastTime=0; String lastId="";
            try (PreparedStatement p=c.prepareStatement(sql)) {
                int i=1; p.setInt(i++,target); p.setInt(i++,target); p.setLong(i++,snapshot); p.setLong(i++,query.from); p.setLong(i++,query.to);
                if (!"ALL".equals(query.result)) p.setInt(i++,target);
                if (!query.opponent.isEmpty()) { p.setInt(i++,target); p.setString(i++,query.opponent); }
                if (!query.beforeId.isEmpty()) { p.setLong(i++,query.beforeTime); p.setLong(i++,query.beforeTime); p.setString(i++,query.beforeId); }
                p.setInt(i,query.limit+1);
                try (ResultSet r=p.executeQuery()) {
                    while (r.next()) {
                        ids.add(r.getString("match_id"));
                        if (ids.size()<=query.limit) { lastTime=r.getLong("ended"); lastId=r.getString("match_id"); }
                    }
                }
            }
            boolean more=ids.size()>query.limit;
            if (more) ids.remove(ids.size()-1);
            Map<String,Object> page=new LinkedHashMap<>(); page.put("ids",ids); page.put("has_more",more);
            page.put("next_cursor",more?query.cursor(snapshot,lastTime,lastId):""); return page;
        }
    }
    boolean initialized() throws SQLException {
        try(Connection c=open();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT value FROM metadata WHERE key='schema_version'")) {
            if(!r.next())return false;if(!"1".equals(r.getString(1)))throw new SQLException("Unsupported database version");return true;
        }
    }
    Map<String,Object> read() throws SQLException {
        List<Object> users=new ArrayList<>(),matches=new ArrayList<>();
        try(Connection c=open();Statement s=c.createStatement()) {
            try(ResultSet r=s.executeQuery("SELECT * FROM accounts ORDER BY user_id")) {while(r.next()) {
                Map<String,Object> m=new LinkedHashMap<>();m.put("userId",r.getInt("user_id"));m.put("username",r.getString("username"));m.put("passwordHash",r.getString("password_hash"));
                m.put("totalPoints",r.getInt("total_points"));m.put("totalWins",r.getInt("total_wins"));m.put("totalGames",r.getInt("total_games"));m.put("matchmakingBlockedUntil",r.getLong("blocked_until"));m.put("deleted",r.getBoolean("deleted"));users.add(m);
            }}
            try(ResultSet r=s.executeQuery("SELECT payload FROM matches ORDER BY rowid")){while(r.next())matches.add(JsonUtil.parseObject(r.getString(1)));}
        }
        Map<String,Object> root=new LinkedHashMap<>();root.put("users",users);root.put("matches",matches);return root;
    }
    void write(List<Map<String,Object>> users,List<Object> records) throws IOException {
        try(Connection c=open()) {
            c.setAutoCommit(false);
            try {
                try(PreparedStatement p=c.prepareStatement("INSERT INTO accounts VALUES (?,?,?,?,?,?,?,?) ON CONFLICT(user_id) DO UPDATE SET username=excluded.username,password_hash=excluded.password_hash,total_points=excluded.total_points,total_wins=excluded.total_wins,total_games=excluded.total_games,blocked_until=excluded.blocked_until,deleted=excluded.deleted")) {
                    for(Map<String,Object> u:users){p.setInt(1,((Number)u.get("userId")).intValue());p.setString(2,(String)u.get("username"));p.setString(3,(String)u.get("passwordHash"));p.setInt(4,((Number)u.get("totalPoints")).intValue());p.setInt(5,((Number)u.get("totalWins")).intValue());p.setInt(6,((Number)u.get("totalGames")).intValue());p.setLong(7,((Number)u.get("matchmakingBlockedUntil")).longValue());p.setBoolean(8,Boolean.TRUE.equals(u.get("deleted")));p.addBatch();}p.executeBatch();
                }
                try(PreparedStatement p=c.prepareStatement("INSERT INTO matches VALUES (?,?,?,?) ON CONFLICT(match_id) DO UPDATE SET player1_id=excluded.player1_id,player2_id=excluded.player2_id,payload=excluded.payload")) {
                    for(Object o:records){@SuppressWarnings("unchecked") Map<String,Object> m=(Map<String,Object>)o;p.setString(1,(String)m.get("matchId"));p.setInt(2,((Number)m.get("player1Id")).intValue());p.setInt(3,((Number)m.get("player2Id")).intValue());p.setString(4,JsonUtil.toJson(m));p.addBatch();}p.executeBatch();
                }
                try(Statement s=c.createStatement()){s.executeUpdate("INSERT OR REPLACE INTO metadata VALUES ('schema_version','1')");}
                c.commit();
            }catch(Exception e){c.rollback();throw e;}
        }catch(Exception e){throw new IOException("Database transaction failed: "+e.getMessage(),e);}
    }
}
