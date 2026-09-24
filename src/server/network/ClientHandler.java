package server.network;

import common.model.User;
import common.protocol.JsonUtil;
import common.protocol.Message;
import common.protocol.MessageType;
import server.manager.LobbyManager;
import server.manager.MatchManager;
import server.manager.UserManager;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final UserManager userManager;
    private final LobbyManager lobbyManager;
    private final MatchManager matchManager;

    private BufferedReader reader;
    private PrintWriter writer;
    private User user;
    private volatile boolean connected = true;

    public ClientHandler(Socket socket, UserManager userManager, LobbyManager lobbyManager, MatchManager matchManager) {
        this.socket = socket;
        this.userManager = userManager;
        this.lobbyManager = lobbyManager;
        this.matchManager = matchManager;
    }

    @Override
    public void run() {
        try {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

            String line;
            while (connected && (line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                try {
                    Message msg = Message.fromJson(line);
                    if (msg != null && msg.getType() != null) {
                        handleMessage(msg);
                    }
                } catch (Exception ex) {
                    System.err.println("[ClientHandler] Lỗi parse message từ client: " + ex.getMessage());
                }
            }
        } catch (IOException e) {
            // Client ngắt kết nối
        } finally {
            close();
        }
    }

    private void handleMessage(Message msg) {
        MessageType type = msg.getType();

        switch (type) {
            case LOGIN_REQ: {
                String username = JsonUtil.getString(msg.getData(), "username", "");
                String password = JsonUtil.getString(msg.getData(), "password", "");
                User u = userManager.login(username, password, this);
                if (u != null) {
                    Message resp = new Message(MessageType.LOGIN_RESP, "SUCCESS", "Đăng nhập thành công!");
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("user_id", u.getUserId());
                    data.put("username", u.getUsername());
                    data.put("total_points", u.getTotalPoints());
                    data.put("total_wins", u.getTotalWins());
                    data.put("total_games", u.getTotalGames());
                    data.put("win_rate", u.getWinRate());
                    data.put("status", u.getStatus().name());
                    resp.setData(data);
                    sendMessage(resp);
                } else {
                    sendMessage(Message.error(MessageType.LOGIN_RESP, "Đăng nhập thất bại! Sai tên tài khoản hoặc mật khẩu."));
                }
                break;
            }

            case LOGOUT_REQ: {
                userManager.logout(this);
                sendMessage(new Message(MessageType.LOGOUT_REQ, "SUCCESS", "Đã đăng xuất"));
                break;
            }

            case GET_ONLINE_USERS_REQ: {
                Message resp = new Message(MessageType.ONLINE_USERS_RESP, "SUCCESS", "");
                resp.put("users", userManager.getOnlineUsersList());
                sendMessage(resp);
                break;
            }

            case GET_LEADERBOARD_REQ: {
                Message resp = new Message(MessageType.LEADERBOARD_RESP, "SUCCESS", "");
                resp.put("leaderboard", userManager.getLeaderboard());
                sendMessage(resp);
                break;
            }

            case CREATE_ROOM_REQ: {
                String roomCode = lobbyManager.createRoom(this);
                Message resp = new Message(MessageType.CREATE_ROOM_RESP, "SUCCESS", "Tạo phòng thành công!");
                resp.put("room_code", roomCode);
                sendMessage(resp);
                break;
            }

            case JOIN_ROOM_REQ: {
                String roomCode = JsonUtil.getString(msg.getData(), "room_code", "");
                boolean joined = lobbyManager.joinRoom(roomCode, this);
                if (!joined) {
                    sendMessage(Message.error(MessageType.JOIN_ROOM_RESP, "Mã phòng không tồn tại hoặc phòng đã đầy!"));
                }
                break;
            }

            case LEAVE_ROOM_REQ: {
                lobbyManager.leaveCurrentRoom(this);
                break;
            }

            case QUEUE_JOIN_REQ: {
                lobbyManager.joinMatchmaking(this);
                break;
            }

            case QUEUE_LEAVE_REQ: {
                lobbyManager.removeFromQueue(this);
                sendMessage(new Message(MessageType.QUEUE_LEAVE_REQ, "SUCCESS", "Đã rời hàng chờ"));
                break;
            }

            case INVITE_REQ: {
                int targetId = JsonUtil.getInt(msg.getData(), "target_user_id", -1);
                lobbyManager.handleInvite(this, targetId);
                break;
            }

            case INVITE_RESPOND_REQ: {
                int inviterId = JsonUtil.getInt(msg.getData(), "inviter_id", -1);
                boolean accept = JsonUtil.getBoolean(msg.getData(), "accept", false);
                lobbyManager.handleInviteResponse(this, inviterId, accept);
                break;
            }

            case PLACE_SHIPS_REQ: {
                matchManager.handlePlaceShips(this, msg);
                break;
            }

            case PLAYER_READY_REQ: {
                matchManager.handleReady(this);
                break;
            }

            case FIRE_REQ: {
                matchManager.handleFire(this, msg);
                break;
            }

            case SURRENDER_REQ: {
                matchManager.handleSurrender(this);
                break;
            }

            case REMATCH_REQ: {
                matchManager.handleRematchReq(this);
                break;
            }

            case REMATCH_CONFIRM_REQ: {
                matchManager.handleRematchConfirm(this, msg);
                break;
            }

            default:
                break;
        }
    }

    public synchronized void sendMessage(Message msg) {
        if (!connected || writer == null || msg == null) return;
        try {
            String json = msg.toJson();
            writer.println(json);
            writer.flush();
        } catch (Exception e) {
            System.err.println("[ClientHandler] Lỗi gửi tin tới " + (user != null ? user.getUsername() : "Client") + ": " + e.getMessage());
        }
    }

    public synchronized void close() {
        if (!connected) return;
        connected = false;

        matchManager.handleClientDisconnect(this);
        lobbyManager.leaveCurrentRoom(this);
        lobbyManager.removeFromQueue(this);
        userManager.logout(this);

        try {
            if (reader != null) reader.close();
            if (writer != null) writer.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {
        }
        System.out.println("[ClientHandler] Đã đóng kết nối với " + (user != null ? user.getUsername() : socket.getRemoteSocketAddress()));
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public boolean isConnected() {
        return connected && socket != null && !socket.isClosed();
    }
}
