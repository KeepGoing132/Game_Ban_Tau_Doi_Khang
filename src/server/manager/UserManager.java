package server.manager;

import common.model.User;
import common.model.UserStatus;
import common.protocol.Message;
import common.protocol.MessageType;
import server.network.ClientHandler;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class UserManager {
    private final StorageManager storageManager;
    private final Map<Integer, ClientHandler> activeClients = new ConcurrentHashMap<>();

    public UserManager(StorageManager storageManager) {
        this.storageManager = storageManager;
    }

    public synchronized User login(String username, String password, ClientHandler handler) {
        if (username == null || username.trim().isEmpty()) return null;
        username = username.trim();

        User user = storageManager.findByUsername(username);
        if (user == null) {
            // Tự động tạo tài khoản mới nếu chưa tồn tại
            user = storageManager.registerUser(username, password);
        } else {
            // Kiểm tra mật khẩu (nếu có)
            if (password != null && !password.isEmpty() && !password.equals(user.getPassword())) {
                return null;
            }
        }

        // Kiểm tra xem tài khoản này đã đăng nhập ở client khác chưa
        ClientHandler oldHandler = activeClients.get(user.getUserId());
        if (oldHandler != null && oldHandler != handler) {
            oldHandler.sendMessage(Message.error(MessageType.ERROR_NOTIFY, "Tài khoản của bạn đã được đăng nhập từ nơi khác."));
            oldHandler.close();
        }

        user.setStatus(UserStatus.IDLE);
        activeClients.put(user.getUserId(), handler);
        handler.setUser(user);

        // Broadcast trạng thái mới của user tới tất cả client đang online
        broadcastUserStatus(user);

        return user;
    }

    public synchronized void logout(ClientHandler handler) {
        if (handler == null || handler.getUser() == null) return;
        User user = handler.getUser();
        user.setStatus(UserStatus.OFFLINE);
        activeClients.remove(user.getUserId());

        broadcastUserStatus(user);
        storageManager.saveUsers();
    }

    public void updateUserStatus(User user, UserStatus newStatus) {
        if (user == null) return;
        user.setStatus(newStatus);
        broadcastUserStatus(user);
    }

    public void broadcastUserStatus(User user) {
        Message msg = new Message(MessageType.USER_STATUS_BROADCAST);
        msg.put("user_id", user.getUserId());
        msg.put("username", user.getUsername());
        msg.put("status", user.getStatus().name());
        broadcast(msg, null);
    }

    public void broadcast(Message msg, ClientHandler exclude) {
        for (ClientHandler client : activeClients.values()) {
            if (exclude != null && client.equals(exclude)) continue;
            client.sendMessage(msg);
        }
    }

    public List<Map<String, Object>> getOnlineUsersList() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (ClientHandler ch : activeClients.values()) {
            User u = ch.getUser();
            if (u != null) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("user_id", u.getUserId());
                map.put("username", u.getUsername());
                map.put("total_points", u.getTotalPoints());
                map.put("total_wins", u.getTotalWins());
                map.put("total_games", u.getTotalGames());
                map.put("win_rate", u.getWinRate());
                map.put("status", u.getStatus().name());
                list.add(map);
            }
        }
        return list;
    }

    /**
     * Bảng xếp hạng sắp xếp theo 3 tiêu chí:
     * 1. Tổng số điểm giảm dần
     * 2. Tổng số trận thắng giảm dần
     * 3. Tỷ lệ thắng (Win Rate) giảm dần
     */
    public List<Map<String, Object>> getLeaderboard() {
        List<User> allUsers = storageManager.getAllUsers();
        allUsers.sort((a, b) -> {
            // Tiêu chí 1: Điểm giảm dần
            if (b.getTotalPoints() != a.getTotalPoints()) {
                return Integer.compare(b.getTotalPoints(), a.getTotalPoints());
            }
            // Tiêu chí 2: Số trận thắng giảm dần
            if (b.getTotalWins() != a.getTotalWins()) {
                return Integer.compare(b.getTotalWins(), a.getTotalWins());
            }
            // Tiêu chí 3: Tỷ lệ thắng giảm dần
            return Double.compare(b.getWinRate(), a.getWinRate());
        });

        List<Map<String, Object>> result = new ArrayList<>();
        int rank = 1;
        for (User u : allUsers) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("rank", rank++);
            map.put("user_id", u.getUserId());
            map.put("username", u.getUsername());
            map.put("total_points", u.getTotalPoints());
            map.put("total_wins", u.getTotalWins());
            map.put("total_games", u.getTotalGames());
            map.put("win_rate", u.getWinRate());
            map.put("status", u.getStatus().name());
            result.add(map);
        }
        return result;
    }

    public ClientHandler getClientById(int userId) {
        return activeClients.get(userId);
    }
}
