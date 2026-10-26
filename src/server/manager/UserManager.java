package server.manager;

import common.model.MatchRecord;
import common.model.User;
import common.model.UserStatus;
import common.protocol.Message;
import common.protocol.MessageType;
import server.network.ClientHandler;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class UserManager {
    private final StorageManager storageManager;
    private final ChatManager chat;
    public ChatManager getChat(){return chat;}
    private final Map<Integer, ClientHandler> activeClients = new ConcurrentHashMap<>();

    public UserManager(StorageManager storageManager) {
        this.storageManager = storageManager;
        this.chat = new ChatManager(this,storageManager);
    }

    private final Object registryLock = new Object();
    public Object getRegistryLock() { return registryLock; }

    public synchronized User login(String username, String password, ClientHandler handler) {
        if (handler.getUser() != null) throw new DomainException("INVALID_STATE", "Socket đã đăng nhập");
        if (username == null || !username.trim().matches("[A-Za-z0-9_]{1,32}") || password == null || password.trim().isEmpty() || password.length() > 128)
            throw new DomainException("AUTH_FAILED", "Tên 1–32 ký tự chữ/số/gạch dưới; mật khẩu không được rỗng");
        username = username.trim().toLowerCase(Locale.ROOT);
        User user = storageManager.findByUsername(username);
        if (user != null && !PasswordHasher.verify(password, user.getPassword())) throw new DomainException("AUTH_FAILED", "Sai mật khẩu");
        if (user != null && activeClients.containsKey(user.getUserId())) throw new DomainException("ALREADY_LOGGED_IN", "Tài khoản đang được sử dụng");
        if (user == null) user = storageManager.registerUser(username, password);
        boolean blocked=isMatchmakingBlocked(user);
        user.setStatus(blocked?UserStatus.BUSY:UserStatus.IDLE); user.setPhase(blocked?"COOLDOWN":"IDLE");
        activeClients.put(user.getUserId(), handler); handler.setUser(user);
        return user;
    }

    public void changePassword(ClientHandler handler, String oldPassword, String newPassword) {
        synchronized (registryLock) {
            User user = handler.getUser();
            if (user == null) throw new DomainException("UNAUTHENTICATED");
            if (!"IDLE".equals(user.getPhase()) && !"COOLDOWN".equals(user.getPhase()))
                throw new DomainException("INVALID_STATE", "Về sảnh và hủy chờ trước khi đổi mật khẩu");
            storageManager.changePassword(user, oldPassword, newPassword);
        }
    }

    public void deleteAccount(ClientHandler handler,String password) {
        User user=handler.getUser();if(user==null)throw new DomainException("UNAUTHENTICATED");
        if(!"IDLE".equals(user.getPhase()) && !"COOLDOWN".equals(user.getPhase()))throw new DomainException("INVALID_STATE","Về sảnh trước khi xóa tài khoản");
        storageManager.deleteAccount(user,password);
    }
    public synchronized void logout(ClientHandler handler) {
        User user = handler.getUser();
        if (user == null || !activeClients.remove(user.getUserId(), handler)) return;
        user.setStatus(UserStatus.OFFLINE); user.setPhase("OFFLINE");
        handler.setUser(null); broadcastUserStatus(user);
    }
    public void updatePhase(ClientHandler handler, String phase) {
        if (handler.getUser() == null) return;
        User u = handler.getUser();
        if("IDLE".equals(phase) && isMatchmakingBlocked(u))phase="COOLDOWN";
        u.setPhase(phase);
        u.setStatus("IDLE".equals(phase) ? UserStatus.IDLE : UserStatus.BUSY);
        broadcastUserStatus(u);
    }
    public void updateUserStatus(User user, UserStatus status) {
        if (user == null) return;
        user.setStatus(status); user.setPhase(status.name()); broadcastUserStatus(user);
    }
    public Map<String, Object> publicUser(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("user_id",u.getUserId());m.put("username",u.getUsername());
        m.put("total_points",u.getTotalPoints());m.put("total_wins",u.getTotalWins());
        m.put("total_games",u.getTotalGames());m.put("win_rate",u.getWinRate());
        m.put("status",u.getStatus().name());m.put("phase",u.getPhase());
        m.putAll(storageManager.penaltyInfo(u));return m;
    }

    public boolean isMatchmakingBlocked(User user) {
        Map<String,Object> info=storageManager.penaltyInfo(user);
        return ((Number)info.get("matchmaking_wait_ms")).longValue()>0 || Boolean.TRUE.equals(info.get("penalty_pending"));
    }

    public void requireCanMatch(ClientHandler handler) {
        if(handler==null || handler.getUser()==null)throw new DomainException("INVALID_STATE","Người chơi không trực tuyến");
        User user=handler.getUser();Map<String,Object> info=storageManager.penaltyInfo(user);
        long remaining=((Number)info.get("matchmaking_wait_ms")).longValue();
        if(remaining>0 || Boolean.TRUE.equals(info.get("penalty_pending"))) {
            info.put("user_id",user.getUserId());
            String detail=remaining>0?"Chỉ huy "+user.getUsername()+" phải chờ "+((remaining+999)/1000)+" giây do rời trận."
                    :"Kết quả rời trận đang chờ lưu. Chưa thể ghép trận mới.";
            throw new DomainException("MATCHMAKING_COOLDOWN",detail).withData(info);
        }
        if("COOLDOWN".equals(user.getPhase()))updatePhase(handler,"IDLE");
    }

    public void refreshExpiredPenalties() {
        synchronized(registryLock) {
            for(ClientHandler handler:activeClients.values()) {
                User user=handler.getUser();
                if(user!=null && "COOLDOWN".equals(user.getPhase()) && !isMatchmakingBlocked(user))updatePhase(handler,"IDLE");
            }
        }
    }

    public void broadcastUserStatus(User user) {
        if(user.getUserId()<0)return;
        Message msg = new Message(MessageType.USER_STATUS_BROADCAST);
        msg.setData(publicUser(user));
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
                list.add(publicUser(u));
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
            int rate = Double.compare(b.getTotalGames()==0?0:(double)b.getTotalWins()/b.getTotalGames(), a.getTotalGames()==0?0:(double)a.getTotalWins()/a.getTotalGames());
            if (rate != 0) return rate;
            int name = a.getUsername().compareTo(b.getUsername());
            return name != 0 ? name : Integer.compare(a.getUserId(), b.getUserId());
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

    /**
     * Match history of one account, newest first. Only public result data leaves the server:
     * who played, who won, the reason, shots, duration and the points each side got.
     */
    public Map<String, Object> matchHistory(ClientHandler requester, int targetUserId, int limit) {
        Map<String,Object> data=new LinkedHashMap<>(); data.put("target_user_id",targetUserId); data.put("limit",limit);
        return matchHistory(requester,data);
    }
    public Map<String, Object> matchHistory(ClientHandler requester, Map<String,Object> data) {
        synchronized (storageManager) {
        int targetUserId=common.protocol.JsonUtil.getInt(data,"target_user_id",0);
        User viewer = requester == null ? null : requester.getUser();
        if (viewer == null) throw new DomainException("UNAUTHENTICATED", "Hãy đăng nhập lại");
        User target = targetUserId <= 0 ? viewer : storageManager.findById(targetUserId);
        if (target == null) throw new DomainException("USER_NOT_FOUND", "Không tìm thấy người chơi");
        HistoryQuery query = new HistoryQuery(target.getUserId(),data);
        Map<String,Object> page=storageManager.historyPage(target.getUserId(),query);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object id : (List<?>)page.get("ids")) {
            MatchRecord r=storageManager.findMatch((String)id);
            if (r==null) continue;
            boolean first = r.getPlayer1Id() == target.getUserId();
            int myPoints = first ? r.getPlayer1Points() : r.getPlayer2Points();
            int myShots = first ? r.getPlayer1Shots() : r.getPlayer2Shots();
            String opponentName = first ? r.getPlayer2Name() : r.getPlayer1Name();
            int opponentId = first ? r.getPlayer2Id() : r.getPlayer1Id();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("match_id", r.getMatchId());
            row.put("opponent_id", opponentId);
            row.put("opponent_name", opponentName == null || opponentName.isEmpty() ? "Không rõ" : opponentName);
            row.put("won", r.getWinnerId() == target.getUserId());
            row.put("points", myPoints);
            row.put("shots", myShots);
            row.put("duration_seconds", r.getDurationSeconds());
            row.put("reason", r.getReason() == null ? "" : r.getReason());
            row.put("ended_at_ms", r.getEndedAt() > 0 ? r.getEndedAt() : r.getTimestamp());
            row.put("legacy", r.getLegacy());
            row.put("replay", r.hasReplay());
            rows.add(row);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("user_id", target.getUserId());
        result.put("username", target.isDeleted() ? "Tài khoản đã xóa" : target.getUsername());
        result.put("total_games", target.getTotalGames());
        result.put("total_wins", target.getTotalWins());
        result.put("total_points", target.getTotalPoints());
        result.put("matches", rows);
        result.put("has_more", page.get("has_more"));
        result.put("next_cursor", page.get("next_cursor"));
        return result;
        }
    }

    /**
     * One match of the requester's own history with both sealed fleets and the ordered shot log,
     * so the client can rebuild the two boards exactly as the match ended. Fleets are released
     * only to the two players of that match, and only after the match is over.
     */
    public Map<String, Object> matchDetail(ClientHandler requester, String matchId) {
        User viewer = requester == null ? null : requester.getUser();
        if (viewer == null) throw new DomainException("UNAUTHENTICATED", "Hãy đăng nhập lại");
        if (matchId == null || matchId.isEmpty() || matchId.length() > 128) throw new DomainException("INVALID_MESSAGE", "Thiếu mã trận");
        MatchRecord r = storageManager.findMatch(matchId);
        if (r == null) throw new DomainException("MATCH_NOT_FOUND", "Không tìm thấy trận này");
        boolean first = r.getPlayer1Id() == viewer.getUserId();
        if (!first && r.getPlayer2Id() != viewer.getUserId())
            throw new DomainException("FORBIDDEN", "Chỉ hai đấu thủ của trận mới xem được hải phận");
        if (!r.hasReplay())
            throw new DomainException("REPLAY_UNAVAILABLE", "Trận này được ghi trước khi có tính năng xem lại");
        int myShots = first ? r.getPlayer1Shots() : r.getPlayer2Shots();
        int opponentShots = first ? r.getPlayer2Shots() : r.getPlayer1Shots();
        int opponentId = first ? r.getPlayer2Id() : r.getPlayer1Id();
        String opponentName = first ? r.getPlayer2Name() : r.getPlayer1Name();
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("match_id", r.getMatchId());
        detail.put("opponent_id", opponentId);
        detail.put("opponent_name", opponentName == null || opponentName.isEmpty() ? "Không rõ" : opponentName);
        detail.put("won", r.getWinnerId() == viewer.getUserId());
        detail.put("points", first ? r.getPlayer1Points() : r.getPlayer2Points());
        detail.put("reason", r.getReason() == null ? "" : r.getReason());
        detail.put("duration_seconds", r.getDurationSeconds());
        detail.put("ended_at_ms", r.getEndedAt() > 0 ? r.getEndedAt() : r.getTimestamp());
        detail.put("my_shots", myShots);
        detail.put("opponent_shots", opponentShots);
        detail.put("my_ships", first ? r.getPlayer1Ships() : r.getPlayer2Ships());
        detail.put("opponent_ships", first ? r.getPlayer2Ships() : r.getPlayer1Ships());
        detail.put("shot_log", r.getShotLog());
        detail.put("replay_available", true);
        return detail;
    }
}
