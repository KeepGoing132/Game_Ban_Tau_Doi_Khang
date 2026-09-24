package client.controller;

import client.network.ServerListener;
import client.network.SocketClient;
import client.ui.*;
import common.model.Coordinate;
import common.model.Ship;
import common.model.ShotResult;
import common.model.User;
import common.protocol.JsonUtil;
import common.protocol.Message;
import common.protocol.MessageType;

import javax.swing.*;
import java.util.*;

public class ClientController implements ServerListener, BattlePanel.BattleActionListener {
    private final SocketClient socketClient;

    private User currentUser;
    private int opponentId = -1;
    private String opponentName = "";
    private List<Ship> myShips = new ArrayList<>();

    private LoginFrame loginFrame;
    private LobbyFrame lobbyFrame;
    private JFrame gameWindow;
    private PlacementPanel placementPanel;
    private BattlePanel battlePanel;

    public ClientController() {
        this.socketClient = new SocketClient(this);
    }

    public void start() {
        SwingUtilities.invokeLater(() -> {
            loginFrame = new LoginFrame(this);
            loginFrame.setVisible(true);
        });
    }

    public void connectAndLogin(String host, int port, String username, String password) {
        new Thread(() -> {
            boolean connected = socketClient.connect(host, port);
            if (!connected) {
                SwingUtilities.invokeLater(() -> loginFrame.showLoginError("Không thể kết nối đến máy chủ " + host + ":" + port));
                return;
            }

            Message loginReq = new Message(MessageType.LOGIN_REQ);
            loginReq.put("username", username);
            loginReq.put("password", password);
            socketClient.send(loginReq);
        }).start();
    }

    public void logout() {
        socketClient.send(new Message(MessageType.LOGOUT_REQ));
        socketClient.disconnect("Người dùng đăng xuất");
        if (lobbyFrame != null) lobbyFrame.dispose();
        if (gameWindow != null) gameWindow.dispose();
        loginFrame.reset();
        loginFrame.setVisible(true);
    }

    public void refreshOnlineUsers() {
        socketClient.send(new Message(MessageType.GET_ONLINE_USERS_REQ));
    }

    public void requestLeaderboard() {
        socketClient.send(new Message(MessageType.GET_LEADERBOARD_REQ));
    }

    public void createRoom() {
        socketClient.send(new Message(MessageType.CREATE_ROOM_REQ));
    }

    public void joinRoom(String roomCode) {
        Message msg = new Message(MessageType.JOIN_ROOM_REQ);
        msg.put("room_code", roomCode);
        socketClient.send(msg);
    }

    public void joinQueue() {
        socketClient.send(new Message(MessageType.QUEUE_JOIN_REQ));
    }

    public void leaveQueue() {
        socketClient.send(new Message(MessageType.QUEUE_LEAVE_REQ));
    }

    public void sendInvite(int targetUserId) {
        Message msg = new Message(MessageType.INVITE_REQ);
        msg.put("target_user_id", targetUserId);
        socketClient.send(msg);
    }

    public void sendReady(List<Ship> ships) {
        this.myShips = ships;

        // Gửi danh sách 5 tàu lên Server thẩm định
        Message placeMsg = new Message(MessageType.PLACE_SHIPS_REQ);
        List<Map<String, Object>> shipList = new ArrayList<>();
        for (Ship s : ships) {
            Map<String, Object> smap = new LinkedHashMap<>();
            smap.put("id", s.getId());
            smap.put("size", s.getSize());
            List<Map<String, Object>> coords = new ArrayList<>();
            for (Coordinate c : s.getCoordinates()) {
                Map<String, Object> cmap = new LinkedHashMap<>();
                cmap.put("x", c.getX());
                cmap.put("y", c.getY());
                coords.add(cmap);
            }
            smap.put("coordinates", coords);
            shipList.add(smap);
        }
        placeMsg.put("ships", shipList);
        socketClient.send(placeMsg);

        // Gửi thông báo Ready
        socketClient.send(new Message(MessageType.PLAYER_READY_REQ));
    }

    // ==========================================
    // SERVER LISTENER CALLBACKS
    // ==========================================

    @Override
    public void onConnected() {
    }

    @Override
    public void onDisconnected(String reason) {
        SwingUtilities.invokeLater(() -> {
            if (gameWindow != null) gameWindow.dispose();
            if (lobbyFrame != null) lobbyFrame.dispose();
            if (loginFrame != null) {
                loginFrame.showLoginError("Mất kết nối: " + reason);
                loginFrame.setVisible(true);
            }
        });
    }

    @Override
    public void onMessageReceived(Message msg) {
        SwingUtilities.invokeLater(() -> handleMessage(msg));
    }

    private void handleMessage(Message msg) {
        MessageType type = msg.getType();
        if (type == null) return;

        switch (type) {
            case LOGIN_RESP: {
                if ("SUCCESS".equals(msg.getStatus())) {
                    currentUser = new User();
                    currentUser.setUserId(JsonUtil.getInt(msg.getData(), "user_id", 0));
                    currentUser.setUsername(JsonUtil.getString(msg.getData(), "username", ""));
                    currentUser.setTotalPoints(JsonUtil.getInt(msg.getData(), "total_points", 0));
                    currentUser.setTotalWins(JsonUtil.getInt(msg.getData(), "total_wins", 0));
                    currentUser.setTotalGames(JsonUtil.getInt(msg.getData(), "total_games", 0));

                    loginFrame.setVisible(false);
                    lobbyFrame = new LobbyFrame(this);
                    lobbyFrame.updateUserInfo(currentUser);
                    lobbyFrame.setVisible(true);
                    refreshOnlineUsers();
                } else {
                    loginFrame.showLoginError(msg.getMessage());
                }
                break;
            }

            case ONLINE_USERS_RESP: {
                List<Object> users = JsonUtil.getList(msg.getData(), "users");
                if (lobbyFrame != null) {
                    lobbyFrame.updateOnlineUsers(users);
                }
                break;
            }

            case USER_STATUS_BROADCAST: {
                // Tự động làm mới danh sách online khi có thay đổi
                refreshOnlineUsers();
                break;
            }

            case LEADERBOARD_RESP: {
                List<Object> list = JsonUtil.getList(msg.getData(), "leaderboard");
                LeaderboardDialog dialog = new LeaderboardDialog(lobbyFrame, list);
                dialog.setVisible(true);
                break;
            }

            case CREATE_ROOM_RESP: {
                String code = JsonUtil.getString(msg.getData(), "room_code", "");
                if (lobbyFrame != null) {
                    lobbyFrame.setCreatedRoomCode(code);
                }
                break;
            }

            case JOIN_ROOM_RESP: {
                if (!"SUCCESS".equals(msg.getStatus())) {
                    JOptionPane.showMessageDialog(lobbyFrame, msg.getMessage(), "Lỗi vào phòng", JOptionPane.ERROR_MESSAGE);
                }
                break;
            }

            case ROOM_USER_JOINED: {
                int hostId = JsonUtil.getInt(msg.getData(), "host_id", 0);
                String hostName = JsonUtil.getString(msg.getData(), "host_name", "");
                int guestId = JsonUtil.getInt(msg.getData(), "guest_id", 0);
                String guestName = JsonUtil.getString(msg.getData(), "guest_name", "");

                if (currentUser.getUserId() == hostId) {
                    opponentId = guestId;
                    opponentName = guestName;
                } else {
                    opponentId = hostId;
                    opponentName = hostName;
                }
                startShipPlacementPhase();
                break;
            }

            case ROOM_USER_LEFT: {
                JOptionPane.showMessageDialog(gameWindow != null ? gameWindow : lobbyFrame, msg.getMessage(), "Thông báo", JOptionPane.INFORMATION_MESSAGE);
                exitToLobby();
                break;
            }

            case MATCH_FOUND: {
                opponentId = JsonUtil.getInt(msg.getData(), "opponent_id", 0);
                opponentName = JsonUtil.getString(msg.getData(), "opponent_name", "");
                if (lobbyFrame != null) lobbyFrame.resetQueueButton();
                startShipPlacementPhase();
                break;
            }

            case INVITE_NOTIFY: {
                int inviterId = JsonUtil.getInt(msg.getData(), "inviter_id", 0);
                String inviterName = JsonUtil.getString(msg.getData(), "inviter_name", "");
                int inviterPts = JsonUtil.getInt(msg.getData(), "inviter_points", 0);

                int choice = JOptionPane.showConfirmDialog(
                        lobbyFrame,
                        "Người chơi " + inviterName + " (" + inviterPts + " điểm) thách đấu bạn!\nBạn có chấp nhận tham gia không?",
                        "Lời mời thách đấu",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE
                );

                Message resp = new Message(MessageType.INVITE_RESPOND_REQ);
                resp.put("inviter_id", inviterId);
                resp.put("accept", choice == JOptionPane.YES_OPTION);
                socketClient.send(resp);
                break;
            }

            case INVITE_RESULT: {
                if ("SUCCESS".equals(msg.getStatus())) {
                    opponentId = JsonUtil.getInt(msg.getData(), "opponent_id", 0);
                    opponentName = JsonUtil.getString(msg.getData(), "opponent_name", "");
                    startShipPlacementPhase();
                } else {
                    JOptionPane.showMessageDialog(lobbyFrame, msg.getMessage(), "Kết quả lời mời", JOptionPane.WARNING_MESSAGE);
                }
                break;
            }

            case MATCH_START: {
                int firstTurnId = JsonUtil.getInt(msg.getData(), "first_turn_player_id", -1);
                int timeLimit = JsonUtil.getInt(msg.getData(), "turn_time_limit_sec", 15);
                startBattlePhase(firstTurnId, timeLimit);
                break;
            }

            case FIRE_RESULT: {
                if (battlePanel == null) return;
                int shooterId = JsonUtil.getInt(msg.getData(), "shooter_id", -1);
                boolean iAmShooter = (shooterId == currentUser.getUserId());

                Map<String, Object> coordMap = JsonUtil.getMap(msg.getData(), "coordinate");
                int x = JsonUtil.getInt(coordMap, "x", 0);
                int y = JsonUtil.getInt(coordMap, "y", 0);
                Coordinate c = new Coordinate(x, y);

                String resStr = JsonUtil.getString(msg.getData(), "result", "MISS");
                ShotResult res = ShotResult.valueOf(resStr);

                int oppShipsLeft = JsonUtil.getInt(msg.getData(), "remaining_ships_target", 5);
                int nextTurnId = JsonUtil.getInt(msg.getData(), "next_turn_player_id", -1);

                List<Coordinate> sunkCoords = null;
                List<Object> sunkRaw = JsonUtil.getList(msg.getData(), "sunk_ship_coords");
                if (sunkRaw != null) {
                    sunkCoords = new ArrayList<>();
                    for (Object so : sunkRaw) {
                        if (so instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> sm = (Map<String, Object>) so;
                            sunkCoords.add(new Coordinate(JsonUtil.getInt(sm, "x", 0), JsonUtil.getInt(sm, "y", 0)));
                        }
                    }
                }

                battlePanel.handleFireResult(iAmShooter, c, res, sunkCoords, oppShipsLeft, nextTurnId == shooterId);
                break;
            }

            case TURN_CHANGE: {
                if (battlePanel == null) return;
                int turnPlayerId = JsonUtil.getInt(msg.getData(), "current_turn_player_id", -1);
                int timeLimit = JsonUtil.getInt(msg.getData(), "time_limit_sec", 15);
                battlePanel.setTurn(turnPlayerId == currentUser.getUserId(), timeLimit);
                break;
            }

            case TIMEOUT_NOTIFY: {
                int pid = JsonUtil.getInt(msg.getData(), "player_id", -1);
                int ax = JsonUtil.getInt(msg.getData(), "auto_x", -1);
                int ay = JsonUtil.getInt(msg.getData(), "auto_y", -1);
                String msgText = msg.getMessage();
                // Hiển thị thông báo nhẹ hoặc để FIRE_RESULT cập nhật
                break;
            }

            case MATCH_END: {
                if (battlePanel == null) return;
                int winnerId = JsonUtil.getInt(msg.getData(), "winner_id", -1);
                boolean iWon = (winnerId == currentUser.getUserId());
                String reason = JsonUtil.getString(msg.getData(), "reason", "");

                // Cập nhật lại điểm của currentUser
                Map<String, Object> scores = JsonUtil.getMap(msg.getData(), "scores");
                int awardedPts = iWon ? 3 : 0;
                if (scores != null) {
                    Map<String, Object> myScore = JsonUtil.getMap(scores, String.valueOf(currentUser.getUserId()));
                    if (myScore != null) {
                        awardedPts = JsonUtil.getInt(myScore, "points_awarded", awardedPts);
                        currentUser.setTotalPoints(JsonUtil.getInt(myScore, "total_points", currentUser.getTotalPoints()));
                        currentUser.setTotalWins(JsonUtil.getInt(myScore, "total_wins", currentUser.getTotalWins()));
                    }
                }
                currentUser.setTotalGames(currentUser.getTotalGames() + 1);

                battlePanel.showGameOver(iWon, awardedPts, reason);
                break;
            }

            case REMATCH_NOTIFY: {
                int choice = JOptionPane.showConfirmDialog(
                        gameWindow,
                        "Đối thủ " + opponentName + " muốn chơi lại ván mới!\nBạn có đồng ý không?",
                        "Yêu cầu chơi lại",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE
                );
                Message cfm = new Message(MessageType.REMATCH_CONFIRM_REQ);
                cfm.put("accept", choice == JOptionPane.YES_OPTION);
                socketClient.send(cfm);
                break;
            }

            case REMATCH_START: {
                startShipPlacementPhase();
                break;
            }

            case ERROR_NOTIFY: {
                JOptionPane.showMessageDialog(gameWindow != null ? gameWindow : lobbyFrame, msg.getMessage(), "Thông báo lỗi", JOptionPane.WARNING_MESSAGE);
                break;
            }

            default:
                break;
        }
    }

    private void startShipPlacementPhase() {
        if (lobbyFrame != null) lobbyFrame.setVisible(false);
        if (gameWindow != null) gameWindow.dispose();

        gameWindow = new JFrame("CHUẨN BỊ CHIẾN ĐẤU - ĐỐI THỦ: " + opponentName);
        gameWindow.setSize(1000, 680);
        gameWindow.setLocationRelativeTo(null);
        gameWindow.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        gameWindow.getContentPane().setBackground(Theme.BG_DARK);

        placementPanel = new PlacementPanel(this::sendReady);
        gameWindow.setContentPane(placementPanel);
        gameWindow.setVisible(true);
    }

    private void startBattlePhase(int firstTurnPlayerId, int timeLimitSec) {
        if (gameWindow == null) return;
        gameWindow.setTitle("HẢI CHIẾN ĐANG DIỄN RA: " + currentUser.getUsername() + " VS " + opponentName);

        battlePanel = new BattlePanel(currentUser.getUsername(), opponentName, myShips, this);
        gameWindow.setContentPane(battlePanel);
        gameWindow.revalidate();
        gameWindow.repaint();

        battlePanel.setTurn(firstTurnPlayerId == currentUser.getUserId(), timeLimitSec);
    }

    // ==========================================
    // BATTLE ACTION LISTENER IMPLEMENTATION
    // ==========================================

    @Override
    public void onFire(int x, int y) {
        Message msg = new Message(MessageType.FIRE_REQ);
        msg.put("x", x);
        msg.put("y", y);
        socketClient.send(msg);
    }

    @Override
    public void onSurrender() {
        socketClient.send(new Message(MessageType.SURRENDER_REQ));
        exitToLobby();
    }

    @Override
    public void onRematch() {
        socketClient.send(new Message(MessageType.REMATCH_REQ));
        JOptionPane.showMessageDialog(gameWindow, "Đã gửi lời mời chơi lại tới đối thủ. Vui lòng chờ phản hồi...", "Chơi lại", JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public void onExitToLobby() {
        exitToLobby();
    }

    private void exitToLobby() {
        if (gameWindow != null) {
            gameWindow.dispose();
            gameWindow = null;
        }
        if (lobbyFrame != null) {
            lobbyFrame.updateUserInfo(currentUser);
            lobbyFrame.setVisible(true);
            refreshOnlineUsers();
        }
    }
}
