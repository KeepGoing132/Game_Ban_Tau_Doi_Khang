package server.manager;

import common.model.MatchRecord;
import common.model.User;
import common.model.UserStatus;
import common.protocol.JsonUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class StorageManager {
    private static final String DATA_DIR = "data";
    private static final String USERS_FILE = "data/users.json";
    private static final String MATCHES_FILE = "data/matches.json";

    private final Map<String, User> userByUsername = new HashMap<>();
    private final Map<Integer, User> userById = new HashMap<>();
    private final List<MatchRecord> matchRecords = new ArrayList<>();
    private int nextUserId = 1;

    public StorageManager() {
        initDataDir();
        loadUsers();
        loadMatches();
    }

    private void initDataDir() {
        File dir = new File(DATA_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    private synchronized void loadUsers() {
        File file = new File(USERS_FILE);
        if (!file.exists()) {
            initSampleUsers();
            saveUsers();
            return;
        }

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] bytes = fis.readAllBytes();
            String json = new String(bytes, StandardCharsets.UTF_8);
            List<Object> list = JsonUtil.parseList(json);
            for (Object obj : list) {
                if (obj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) obj;
                    User u = new User();
                    u.setUserId(JsonUtil.getInt(map, "userId", nextUserId));
                    u.setUsername(JsonUtil.getString(map, "username", ""));
                    u.setPassword(JsonUtil.getString(map, "password", "123456"));
                    u.setTotalPoints(JsonUtil.getInt(map, "totalPoints", 0));
                    u.setTotalWins(JsonUtil.getInt(map, "totalWins", 0));
                    u.setTotalGames(JsonUtil.getInt(map, "totalGames", 0));
                    u.setStatus(UserStatus.OFFLINE);

                    userByUsername.put(u.getUsername().toLowerCase(), u);
                    userById.put(u.getUserId(), u);
                    if (u.getUserId() >= nextUserId) {
                        nextUserId = u.getUserId() + 1;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[StorageManager] Lỗi đọc users.json: " + e.getMessage());
            initSampleUsers();
        }
    }

    private void initSampleUsers() {
        createSampleUser("tuan_duong", "123456", 18, 6, 9);
        createSampleUser("ngoc_bao", "123456", 21, 7, 9);
        createSampleUser("thanh_hai", "123456", 9, 3, 8);
        createSampleUser("dang_khoa", "123456", 15, 5, 8);
        createSampleUser("player1", "123456", 6, 2, 4);
        createSampleUser("player2", "123456", 0, 0, 2);
    }

    private void createSampleUser(String username, String pass, int pts, int wins, int games) {
        User u = new User(nextUserId++, username, pass);
        u.setTotalPoints(pts);
        u.setTotalWins(wins);
        u.setTotalGames(games);
        userByUsername.put(username.toLowerCase(), u);
        userById.put(u.getUserId(), u);
    }

    public synchronized void saveUsers() {
        try (FileOutputStream fos = new FileOutputStream(USERS_FILE)) {
            List<Map<String, Object>> list = new ArrayList<>();
            for (User u : userById.values()) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("userId", u.getUserId());
                map.put("username", u.getUsername());
                map.put("password", u.getPassword());
                map.put("totalPoints", u.getTotalPoints());
                map.put("totalWins", u.getTotalWins());
                map.put("totalGames", u.getTotalGames());
                list.add(map);
            }
            String json = JsonUtil.toJson(list);
            fos.write(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            System.err.println("[StorageManager] Lỗi lưu users.json: " + e.getMessage());
        }
    }

    private synchronized void loadMatches() {
        File file = new File(MATCHES_FILE);
        if (!file.exists()) return;
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] bytes = fis.readAllBytes();
            String json = new String(bytes, StandardCharsets.UTF_8);
            List<Object> list = JsonUtil.parseList(json);
            for (Object obj : list) {
                if (obj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) obj;
                    MatchRecord r = new MatchRecord();
                    r.setMatchId(JsonUtil.getString(map, "matchId", ""));
                    r.setPlayer1Id(JsonUtil.getInt(map, "player1Id", 0));
                    r.setPlayer1Name(JsonUtil.getString(map, "player1Name", ""));
                    r.setPlayer2Id(JsonUtil.getInt(map, "player2Id", 0));
                    r.setPlayer2Name(JsonUtil.getString(map, "player2Name", ""));
                    r.setWinnerId(JsonUtil.getInt(map, "winnerId", 0));
                    r.setWinnerName(JsonUtil.getString(map, "winnerName", ""));
                    r.setDurationSeconds(JsonUtil.getInt(map, "durationSeconds", 0));
                    r.setPlayer1Shots(JsonUtil.getInt(map, "player1Shots", 0));
                    r.setPlayer2Shots(JsonUtil.getInt(map, "player2Shots", 0));
                    matchRecords.add(r);
                }
            }
        } catch (Exception e) {
            System.err.println("[StorageManager] Lỗi đọc matches.json: " + e.getMessage());
        }
    }

    public synchronized void saveMatch(MatchRecord record) {
        matchRecords.add(record);
        try (FileOutputStream fos = new FileOutputStream(MATCHES_FILE)) {
            List<Map<String, Object>> list = new ArrayList<>();
            for (MatchRecord r : matchRecords) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("matchId", r.getMatchId());
                map.put("player1Id", r.getPlayer1Id());
                map.put("player1Name", r.getPlayer1Name());
                map.put("player2Id", r.getPlayer2Id());
                map.put("player2Name", r.getPlayer2Name());
                map.put("winnerId", r.getWinnerId());
                map.put("winnerName", r.getWinnerName());
                map.put("durationSeconds", r.getDurationSeconds());
                map.put("player1Shots", r.getPlayer1Shots());
                map.put("player2Shots", r.getPlayer2Shots());
                map.put("timestamp", r.getTimestamp());
                list.add(map);
            }
            String json = JsonUtil.toJson(list);
            fos.write(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            System.err.println("[StorageManager] Lỗi lưu matches.json: " + e.getMessage());
        }
    }

    public synchronized User findByUsername(String username) {
        if (username == null) return null;
        return userByUsername.get(username.trim().toLowerCase());
    }

    public synchronized User findById(int id) {
        return userById.get(id);
    }

    public synchronized User registerUser(String username, String password) {
        User u = new User(nextUserId++, username, password != null ? password : "123");
        userByUsername.put(username.toLowerCase(), u);
        userById.put(u.getUserId(), u);
        saveUsers();
        return u;
    }

    public synchronized List<User> getAllUsers() {
        return new ArrayList<>(userById.values());
    }
}
