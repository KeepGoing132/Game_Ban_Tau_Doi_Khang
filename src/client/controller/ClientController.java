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
import java.awt.Window;
import java.util.*;

public class ClientController implements ServerListener, BattlePanel.BattleActionListener {
    private final SocketClient socketClient;
    private String endpoint = "localhost:8888";
    private String connectionStatus = "Đang đo kết nối...";
    private enum Screen {
        LOGIN, LOBBY, PLACEMENT, BATTLE, RESULT
    }
    private Screen screen = Screen.LOGIN;
    private String matchId, roomId;
    private boolean practiceMatch;

    public void startBot(String difficulty) {
        send(new Message(MessageType.BOT_MATCH_REQ).put("difficulty",difficulty));
    }

    public void deleteAccount(String password) {
        send(new Message(MessageType.DELETE_ACCOUNT_REQ).put("password",password));
    }
    private int turnId;
    private String pendingPlacement;
    private boolean awaitingLeave;
    private boolean requestedRematch;
    private final Map<String,JDialog> invitationDialogs=new HashMap<>();
    private final Map<String,javax.swing.Timer> invitationTimers=new HashMap<>();

    private void closeInvitation(String id) {
        javax.swing.Timer timer=invitationTimers.remove(id);
        if(timer!=null)timer.stop();
        JDialog dialog=invitationDialogs.remove(id);
        if(dialog!=null)dialog.dispose();
    }

    private void closeInvitations() {
        for(String id:new ArrayList<>(invitationDialogs.keySet()))closeInvitation(id);
    }
    private final Set<String> endedMatches = new LinkedHashSet<>();
    private final Set<String> processedResponses = new LinkedHashSet<>();
    private User currentUser;
    private PasswordDialog passwordDialog;

    public void openPasswordDialog() {
        if (passwordDialog != null) passwordDialog.dispose();
        passwordDialog = new PasswordDialog(dialogParent(), (oldPassword, newPassword) ->
        send(new Message(MessageType.CHANGE_PASSWORD_REQ).put("old_password", oldPassword).put("new_password", newPassword)));
        passwordDialog.setVisible(true);
    }
    private ChatController chat;

    public void openChat(int userId,String username) {
        if(chat!=null)chat.open(userId,username);
    }

    private void closeChat() {
        if(chat!=null) {
            chat.dispose();
            chat=null;
        }
    }

    private void send(Message msg) {
        switch(msg.getType()) {
            case PLACE_SHIPS_REQ: case PLAYER_READY_REQ: case FIRE_REQ: case SURRENDER_REQ:
            case REMATCH_REQ: case REMATCH_CONFIRM_REQ: case LEAVE_ROOM_REQ:
            if(matchId!=null)msg.put("match_id",matchId);
            if(roomId!=null)msg.put("room_id",roomId);
            break;
            default: break;
        }
        if(msg.getType()==MessageType.FIRE_REQ)msg.put("turn_id",turnId);
        socketClient.send(msg);
    }

    public void leaveRoom() {
        if(awaitingLeave)return;
        awaitingLeave=true;
        send(new Message(MessageType.LEAVE_ROOM_REQ));
    }

    public void cancelReady() {
        send(new Message(MessageType.PLAYER_READY_REQ).put("ready",false));
    }

    private void clearGame() {
        closeInvitations();
        if (placementPanel != null) placementPanel.disposePanel();
        if(battlePanel!=null)battlePanel.disposePanel();
        battlePanel=null;
        placementPanel=null;
        myShips=new ArrayList<>();
        pendingPlacement=null;
        matchId=null;
        roomId=null;
        turnId=0;
        if(gameWindow!=null) {
            gameWindow.dispose();
            gameWindow=null;
        }
    }
    private int opponentId = -1;
    private String opponentName = "";
    private List<Ship> myShips = new ArrayList<>();

    /** History and replay screens of the lobby, kept so they are closed with the session. */
    private MatchHistoryDialog historyDialog;
    private MatchReplayDialog replayDialog;
    private String pendingHistoryRequest;
    private boolean appendHistoryPage;

    private void closeHistoryScreens() {
        pendingHistoryRequest = null;
        if (passwordDialog != null) {
            passwordDialog.dispose();
            passwordDialog = null;
        }
        if (replayDialog != null) {
            replayDialog.dispose();
            replayDialog = null;
        }
        if (historyDialog != null) {
            historyDialog.dispose();
            historyDialog = null;
        }
    }
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
        endpoint = host.trim().toLowerCase(Locale.ROOT) + ":" + port;
        connectionStatus = "Đang đo kết nối...";
        processedResponses.clear();
        new Thread(() -> {
            boolean connected = socketClient.connect(host, port);
            if (!connected) {
                SwingUtilities.invokeLater(() -> loginFrame.showLoginError("Không thể kết nối đến máy chủ " + host + ":" + port));
                return;
            }
            Message loginReq = new Message(MessageType.LOGIN_REQ);
            loginReq.put("username", username);
            loginReq.put("password", password);
            send(loginReq);
        }).start();
    }

    public void logout() {
        closeChat();
        closeHistoryScreens();
        send(new Message(MessageType.LOGOUT_REQ));
        socketClient.disconnect("Người dùng đăng xuất");
        if (lobbyFrame != null) lobbyFrame.dispose();
        clearGame();
        currentUser=null;
        screen=Screen.LOGIN;
        loginFrame.reset();
        loginFrame.setVisible(true);
    }

    public void refreshOnlineUsers() {
        send(new Message(MessageType.GET_ONLINE_USERS_REQ));
    }

    public void requestLeaderboard() {
        send(new Message(MessageType.GET_LEADERBOARD_REQ));
    }

    /** targetUserId 0 asks for the logged-in player's own history. */
    public void requestMatchHistory(int targetUserId) {
        if (currentUser == null) return;
        if (historyDialog != null) historyDialog.dispose();
        Map<String,Object> placeholder = new LinkedHashMap<>();
        placeholder.put("username", targetUserId==0?currentUser.getUsername():"Lịch sử đấu");
        historyDialog = new MatchHistoryDialog(dialogParent(), placeholder, this::requestMatchDetail);
        final int target = Math.max(0,targetUserId);
        historyDialog.setQueryListener(filters -> requestHistoryPage(target,filters));
        historyDialog.setVisible(true);
        requestHistoryPage(target,new LinkedHashMap<>());
    }

    private void requestHistoryPage(int target, Map<String,Object> filters) {
        Message request = new Message(MessageType.GET_MATCH_HISTORY_REQ).requestId(UUID.randomUUID().toString());
        request.getData().putAll(filters);
        request.put("target_user_id",target).put("limit",20);
        pendingHistoryRequest=request.getRequestId();
        appendHistoryPage=filters.containsKey("cursor");
        historyDialog.setLoading(true);
        send(request);
    }

    /** Full replay of one match of the player's own history: both fleets plus the shot log. */
    public void requestMatchDetail(String matchId) {
        Message msg = new Message(MessageType.GET_MATCH_DETAIL_REQ);
        msg.put("match_id", matchId);
        send(msg);
    }

    /** The window a lobby-owned dialog should be attached to. */
    private Window dialogParent() {
        if (lobbyFrame != null && lobbyFrame.isVisible()) return lobbyFrame;
        return gameWindow;
    }

    public void createRoom() {
        send(new Message(MessageType.CREATE_ROOM_REQ));
    }

    public void joinRoom(String roomCode) {
        Message msg = new Message(MessageType.JOIN_ROOM_REQ);
        msg.put("room_code", roomCode);
        send(msg);
    }

    public void joinQueue() {
        send(new Message(MessageType.QUEUE_JOIN_REQ));
    }

    public void leaveQueue() {
        send(new Message(MessageType.QUEUE_LEAVE_REQ));
    }

    public void sendInvite(int targetUserId) {
        Message msg = new Message(MessageType.INVITE_REQ);
        msg.put("target_user_id", targetUserId);
        send(msg);
    }

    public void sendReady(List<Ship> ships) {
        this.myShips = ships;
        // Gửi danh sách 5 tàu lên Server thẩm định
        Message placeMsg = new Message(MessageType.PLACE_SHIPS_REQ);
        placeMsg.put("ships", common.model.FleetLayout.encode(ships));
        pendingPlacement=UUID.randomUUID().toString();
        placeMsg.requestId(pendingPlacement);
        send(placeMsg);
        // Ready follows the server acknowledgment of this submission.
    }
    // ==========================================
    // SERVER LISTENER CALLBACKS
    // ==========================================
    @Override
    public void onConnected() {
    }
    @Override
    public void onDisconnected(String reason) {
        screen = Screen.LOGIN;
        connectionStatus = "Mất kết nối";
        closeChat();
        closeHistoryScreens();
        clearGame();
        currentUser=null;
        awaitingLeave=false;
        if(lobbyFrame!=null)lobbyFrame.dispose();
        if(loginFrame!=null) {
            loginFrame.showLoginError("Mất kết nối: "+reason);
            loginFrame.setVisible(true);
        }
    }
    @Override
    public void onMessageReceived(Message msg) {
        if(SwingUtilities.isEventDispatchThread())handleMessage(msg);
        else SwingUtilities.invokeLater(() -> handleMessage(msg));
    }
    @Override public void onLatencyMeasured(long milliseconds) {
        connectionStatus = (milliseconds >= common.config.GameConfig.SLOW_CONNECTION_MS ? "Kết nối chậm: " : "Kết nối tốt: ") + milliseconds + " ms";
        updateConnectionStatus();
    }

    private void updateConnectionStatus() {
        if (lobbyFrame != null) lobbyFrame.setConnectionStatus(connectionStatus);
        if (placementPanel != null) placementPanel.setConnectionStatus(connectionStatus);
        if (battlePanel != null) battlePanel.setConnectionStatus(connectionStatus);
    }

    private void applyPenalty(Map<String,Object> data) {
        if(currentUser==null || JsonUtil.getInt(data,"user_id",-1)!=currentUser.getUserId()
            || !data.containsKey("matchmaking_wait_ms"))return;
        currentUser.setMatchmakingBlockedUntil(JsonUtil.getLong(data,"matchmaking_blocked_until",0));
        if(lobbyFrame!=null)lobbyFrame.setPenalty(JsonUtil.getLong(data,"matchmaking_wait_ms",0),JsonUtil.getBoolean(data,"penalty_pending",false));
    }

    private void handleMessage(Message msg) {
        MessageType type = msg.getType();
        if (type == null) return;
        if(msg.getRequestId()!=null) {
            if(!processedResponses.add(msg.getRequestId()))return;
            while(processedResponses.size()>512)processedResponses.remove(processedResponses.iterator().next());
        }
        if(chat!=null && chat.handle(msg))return;
        if(type==MessageType.INVITE_RESULT)closeInvitation(JsonUtil.getString(msg.getData(),"invitation_id",""));
        String incoming=JsonUtil.getString(msg.getData(),"match_id",null);
        if(incoming!=null && type!=MessageType.ROOM_USER_JOINED && type!=MessageType.MATCH_FOUND && type!=MessageType.REMATCH_START && type!=MessageType.MATCH_DETAIL_RESP && !(type==MessageType.INVITE_RESULT && msg.getData().containsKey("opponent_id")) && !incoming.equals(matchId))return;
        if(type==MessageType.REMATCH_START && !Objects.equals(msg.getData().get("previous_match_id"),matchId))return;
        applyPenalty(msg.getData());
        if("ERROR".equals(msg.getStatus())) {
            if (type == MessageType.MATCH_HISTORY_RESP) {
                if (Objects.equals(pendingHistoryRequest,msg.getRequestId()) && historyDialog != null)
                    historyDialog.showQueryError(msg.getMessage());
                return;
            }
            if (type == MessageType.CHANGE_PASSWORD_RESP) {
                if (passwordDialog != null) passwordDialog.showResult(msg.getMessage(), false);
                return;
            }
            if(type==MessageType.LOGIN_RESP) {
                loginFrame.showLoginError(msg.getMessage());
                return;
            }
            if(lobbyFrame!=null)lobbyFrame.completeAction();
            if(type==MessageType.PLACE_SHIPS_RESP || type==MessageType.PLAYER_READY_RESP) {
                if(placementPanel!=null)placementPanel.showPlacementError(msg.getMessage());
                pendingPlacement=null;
            }
            if(type==MessageType.FIRE_RESULT && battlePanel!=null)battlePanel.shotRejected();
            if(type==MessageType.REMATCH_RESP || type==MessageType.REMATCH_CONFIRM_RESP) {
                requestedRematch=false;
                if(battlePanel!=null)battlePanel.rematchRejected();
            }
            if(type==MessageType.QUEUE_JOIN_RESP || type==MessageType.QUEUE_LEAVE_RESP)if(lobbyFrame!=null)lobbyFrame.resetQueueButton();
            if(type==MessageType.LEAVE_ROOM_RESP || type==MessageType.SURRENDER_RESP) {
                awaitingLeave=false;
                if(placementPanel!=null)placementPanel.showLeaveError();
                if(battlePanel!=null)battlePanel.leaveRejected();
            }
            if(type==MessageType.MATCH_DETAIL_RESP) {
                if(historyDialog!=null)historyDialog.showStatus(msg.getMessage());
                return;
            }
            if(type!=MessageType.INVITE_RESULT || !"INVITE_EXPIRED".equals(msg.getErrorCode())) {
                if(battlePanel!=null)battlePanel.showNotice(msg.getMessage());
                else if(placementPanel!=null)placementPanel.showNotice(msg.getMessage());
                else if(lobbyFrame!=null)lobbyFrame.showStatus(msg.getMessage(),true);
            }
            return;
        }
        switch (type) {
            case CHANGE_PASSWORD_RESP:
            case LOGIN_RESP:
            case DELETE_ACCOUNT_RESP:
            case LOGOUT_RESP:
            handleAccountMessage(msg);
            break;
            case BOT_MATCH_RESP:
            case QUEUE_JOIN_RESP:
            case QUEUE_LEAVE_RESP:
            case LEAVE_ROOM_RESP:
            case SURRENDER_RESP:
            case ONLINE_USERS_RESP:
            case USER_STATUS_BROADCAST:
            case CREATE_ROOM_RESP:
            case JOIN_ROOM_RESP:
            case ROOM_USER_JOINED:
            case ROOM_USER_LEFT:
            case MATCH_FOUND:
            case INVITE_NOTIFY:
            case INVITE_RESULT:
            handleLobbyMessage(msg);
            break;
            case PLACE_SHIPS_RESP:
            case PLAYER_READY_RESP:
            case READY_STATE_NOTIFY:
            handlePlacementMessage(msg);
            break;
            case LEADERBOARD_RESP:
            case MATCH_HISTORY_RESP:
            case MATCH_DETAIL_RESP:
            handleHistoryMessage(msg);
            break;
            case RESULT_SAVED_NOTIFY:
            case MATCH_START:
            case FIRE_RESULT:
            case TURN_CHANGE:
            case TIMEOUT_NOTIFY:
            case MATCH_END:
            case REMATCH_NOTIFY:
            case REMATCH_START:
            case ERROR_NOTIFY:
            handleBattleMessage(msg);
            break;
            default: break;
        }
        if (placementPanel != null && msg.getData().containsKey("placement_deadline_ms"))
            placementPanel.setPlacementDeadline(JsonUtil.getLong(msg.getData(),"placement_deadline_ms",0),msg.getServerTimeMs());
        updateConnectionStatus();
    }

    /** Account events, after session/request validation in handleMessage. */
    private void handleAccountMessage(Message msg) {
        String incoming = JsonUtil.getString(msg.getData(), "match_id", null);
        switch (msg.getType()) {
            case CHANGE_PASSWORD_RESP:
            if (passwordDialog != null) passwordDialog.showResult("Đã đổi mật khẩu. Lần đăng nhập tiếp theo dùng mật khẩu mới.", true);
            break;
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
                    screen = Screen.LOBBY;
                    lobbyFrame.updateUserInfo(currentUser);
                    applyPenalty(msg.getData());
                    lobbyFrame.setLifecycle(JsonUtil.getString(msg.getData(),"phase","IDLE"));
                    lobbyFrame.setVisible(true);
                    closeChat();
                    chat=new ChatController(currentUser.getUserId(),this::send);
                    chat.attach(lobbyFrame);
                    chat.load();
                    refreshOnlineUsers();
                }
                else {
                    loginFrame.showLoginError(msg.getMessage());
                }
                break;
            }
            case DELETE_ACCOUNT_RESP:
            closeChat();
            closeHistoryScreens();
            clearGame();
            currentUser=null;
            socketClient.disconnect("Account deleted");
            if(lobbyFrame!=null) {
                lobbyFrame.dispose();
                lobbyFrame=null;
            }
            loginFrame.reset();
            loginFrame.setVisible(true);
            loginFrame.showLoginError("Đã xóa tài khoản và mật khẩu.");
            break;
            case LOGOUT_RESP: break;
            default: break;
        }
    }

    /** Lobby events, after session/request validation in handleMessage. */
    private void handleLobbyMessage(Message msg) {
        String incoming = JsonUtil.getString(msg.getData(), "match_id", null);
        switch (msg.getType()) {
            case BOT_MATCH_RESP:if(lobbyFrame!=null)lobbyFrame.completeAction();
            break;
            case QUEUE_JOIN_RESP: {
                if(lobbyFrame!=null)lobbyFrame.setQueueState(true);
                break;
            }
            case QUEUE_LEAVE_RESP: {
                if(lobbyFrame!=null)lobbyFrame.resetQueueButton();
                break;
            }
            case LEAVE_ROOM_RESP: case SURRENDER_RESP: {
                awaitingLeave=false;
                exitToLobby();
                break;
            }
            case ONLINE_USERS_RESP: {
                List<Object> users = JsonUtil.getList(msg.getData(), "users");
                if(chat!=null)chat.online(users);
                if (lobbyFrame != null) {
                    if(users!=null)for(Object item:users)if(item instanceof Map) {
                        @SuppressWarnings("unchecked") Map<String,Object> user=(Map<String,Object>)item;
                        applyPenalty(user);
                    }
                    lobbyFrame.updateOnlineUsers(users);
                }
                break;
            }
            case USER_STATUS_BROADCAST: {
                if(currentUser==null)break;
                if(JsonUtil.getInt(msg.getData(),"user_id",-1)==currentUser.getUserId()) {
                    currentUser.setTotalPoints(JsonUtil.getInt(msg.getData(),"total_points",currentUser.getTotalPoints()));
                    currentUser.setTotalWins(JsonUtil.getInt(msg.getData(),"total_wins",currentUser.getTotalWins()));
                    currentUser.setTotalGames(JsonUtil.getInt(msg.getData(),"total_games",currentUser.getTotalGames()));
                    if(lobbyFrame!=null) {
                        lobbyFrame.updateUserInfo(currentUser);
                        lobbyFrame.setLifecycle(JsonUtil.getString(msg.getData(),"phase","IDLE"));
                    }
                }
                refreshOnlineUsers();
                break;
            }
            case CREATE_ROOM_RESP: {
                String code = JsonUtil.getString(msg.getData(), "room_code", "");
                roomId=JsonUtil.getString(msg.getData(),"room_id",code);
                if (lobbyFrame != null) {
                    lobbyFrame.setCreatedRoomCode(code);
                }
                break;
            }
            case JOIN_ROOM_RESP: {
                if(lobbyFrame!=null)lobbyFrame.completeAction();
                if (!"SUCCESS".equals(msg.getStatus())) {
                    JOptionPane.showMessageDialog(lobbyFrame, msg.getMessage(), "Lỗi vào phòng", JOptionPane.ERROR_MESSAGE);
                }
                break;
            }
            case ROOM_USER_JOINED: {
                practiceMatch=false;
                matchId=incoming;
                roomId=JsonUtil.getString(msg.getData(),"room_id",null);
                awaitingLeave=false;
                int hostId = JsonUtil.getInt(msg.getData(), "host_id", 0);
                String hostName = JsonUtil.getString(msg.getData(), "host_name", "");
                int guestId = JsonUtil.getInt(msg.getData(), "guest_id", 0);
                String guestName = JsonUtil.getString(msg.getData(), "guest_name", "");
                if (currentUser.getUserId() == hostId) {
                    opponentId = guestId;
                    opponentName = guestName;
                }
                else {
                    opponentId = hostId;
                    opponentName = hostName;
                }
                startShipPlacementPhase();
                break;
            }
            case ROOM_USER_LEFT: {
                exitToLobby();
                if(lobbyFrame!=null)lobbyFrame.showStatus(msg.getMessage(),false);
                break;
            }
            case MATCH_FOUND: {
                practiceMatch=JsonUtil.getBoolean(msg.getData(),"practice",false);
                matchId=incoming;
                roomId=JsonUtil.getString(msg.getData(),"room_id",null);
                awaitingLeave=false;
                opponentId = JsonUtil.getInt(msg.getData(), "opponent_id", 0);
                opponentName = JsonUtil.getString(msg.getData(), "opponent_name", "");
                if (lobbyFrame != null) lobbyFrame.resetQueueButton();
                startShipPlacementPhase();
                break;
            }
            case INVITE_NOTIFY: {
                if(currentUser==null || matchId!=null)break;
                String invitation=JsonUtil.getString(msg.getData(),"invitation_id","");
                String inviterName=JsonUtil.getString(msg.getData(),"inviter_name","");
                long expires=((Number)msg.getData().get("expires_at_ms")).longValue();
                if(expires<=msg.getServerTimeMs())break;
                JOptionPane pane=new JOptionPane("Người chơi "+inviterName+" thách đấu bạn. Chấp nhận?",JOptionPane.QUESTION_MESSAGE,JOptionPane.YES_NO_OPTION);
                JDialog dialog=pane.createDialog(lobbyFrame,"Lời mời thách đấu");
                dialog.setModal(false);
                invitationDialogs.put(invitation,dialog);
                Runnable reject=()-> {
                    if(invitationDialogs.containsKey(invitation)) {
                        closeInvitation(invitation);
                        send(new Message(MessageType.INVITE_RESPOND_REQ).put("invitation_id",invitation).put("accept",false));
                    }
                };
                pane.addPropertyChangeListener(e-> {
                    if(!JOptionPane.VALUE_PROPERTY.equals(e.getPropertyName()) || !(pane.getValue() instanceof Integer) || !invitationDialogs.containsKey(invitation))return;
                    boolean accepted=((Integer)pane.getValue())==JOptionPane.YES_OPTION;
                    closeInvitation(invitation);send(new Message(MessageType.INVITE_RESPOND_REQ).put("invitation_id",invitation).put("accept",accepted));
                });
                dialog.addWindowListener(new java.awt.event.WindowAdapter() {

                    public void windowClosing(java.awt.event.WindowEvent e) {
                        reject.run();
                    }
                });
                javax.swing.Timer expiry=new javax.swing.Timer((int)Math.min(Integer.MAX_VALUE,Math.max(1,expires-msg.getServerTimeMs())),e->closeInvitation(invitation));
                expiry.setRepeats(false);
                invitationTimers.put(invitation,expiry);
                expiry.start();
                dialog.setVisible(true);
                break;
            }
            case INVITE_RESULT: {
                practiceMatch=false;
                if (msg.getData().containsKey("opponent_id") && incoming!=null) {
                    matchId=incoming;
                    roomId=JsonUtil.getString(msg.getData(),"room_id",null);
                    awaitingLeave=false;
                    opponentId = JsonUtil.getInt(msg.getData(), "opponent_id", 0);
                    opponentName = JsonUtil.getString(msg.getData(), "opponent_name", "");
                    startShipPlacementPhase();
                }
                else if(Boolean.FALSE.equals(msg.getData().get("accepted")) && msg.getRequestId()==null && lobbyFrame!=null) {
                    lobbyFrame.showStatus(msg.getMessage(),false);
                }
                break;
            }
            default: break;
        }
    }

    /** Placement events, after session/request validation in handleMessage. */
    private void handlePlacementMessage(Message msg) {
        String incoming = JsonUtil.getString(msg.getData(), "match_id", null);
        switch (msg.getType()) {
            case PLACE_SHIPS_RESP: {
                if(pendingPlacement!=null && pendingPlacement.equals(msg.getRequestId())) {
                    pendingPlacement=null;
                    send(new Message(MessageType.PLAYER_READY_REQ).put("ready",true));
                }
                break;
            }
            case PLAYER_READY_RESP: {
                if(placementPanel!=null)placementPanel.setServerReady(JsonUtil.getBoolean(msg.getData(),"ready",false));
                break;
            }
            case READY_STATE_NOTIFY: {
                if(placementPanel!=null && currentUser!=null)placementPanel.showReadyState(JsonUtil.getBoolean(msg.getData(),currentUser.getUserId()==JsonUtil.getInt(msg.getData(),"player1_id",-1)?"player2_ready":"player1_ready",false));
                break;
            }
            default: break;
        }
    }

    /** History events, after session/request validation in handleMessage. */
    private void handleHistoryMessage(Message msg) {
        String incoming = JsonUtil.getString(msg.getData(), "match_id", null);
        switch (msg.getType()) {
            case LEADERBOARD_RESP: {
                List<Object> list = JsonUtil.getList(msg.getData(), "leaderboard");
                LeaderboardDialog dialog = new LeaderboardDialog(lobbyFrame, list);
                dialog.setVisible(true);
                break;
            }
            case MATCH_HISTORY_RESP: {
                if (Objects.equals(pendingHistoryRequest,msg.getRequestId()) && historyDialog != null && historyDialog.isDisplayable())
                    historyDialog.acceptPage(msg.getData(),appendHistoryPage);
                break;
            }
            case MATCH_DETAIL_RESP: {
                if(currentUser==null)break;
                if(replayDialog!=null) {
                    replayDialog.dispose();
                    replayDialog=null;
                }
                MatchReplayDialog dialog = new MatchReplayDialog(dialogParent(), currentUser.getUserId(), msg.getData());
                replayDialog = dialog;
                dialog.setVisible(true);
                break;
            }
            default: break;
        }
    }

    /** Battle events, after session/request validation in handleMessage. */
    private void handleBattleMessage(Message msg) {
        String incoming = JsonUtil.getString(msg.getData(), "match_id", null);
        switch (msg.getType()) {
            case RESULT_SAVED_NOTIFY:
            if (battlePanel != null) {
                Map<String,Object> scores=JsonUtil.getMap(msg.getData(),"scores");
                if (scores!=null && currentUser!=null) {
                    Map<String,Object> own=JsonUtil.getMap(scores,String.valueOf(currentUser.getUserId()));
                    if (own!=null) battlePanel.setSavedAward(JsonUtil.getInt(own,"points_awarded",0));
                }
                battlePanel.setResultStorage(true);
            }
            break;
            case MATCH_START: {
                if(battlePanel!=null)return;
                turnId=JsonUtil.getInt(msg.getData(),"turn_id",0);
                int firstTurnId = JsonUtil.getInt(msg.getData(), "first_turn_player_id", -1);
                int timeLimit = JsonUtil.getInt(msg.getData(), "turn_time_limit_sec", 15);
                startBattlePhase(firstTurnId, timeLimit);
                battlePanel.setServerTurn(firstTurnId==currentUser.getUserId(),((Number)msg.getData().get("deadline_ms")).longValue(),msg.getServerTimeMs());
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
                int nextTurn=JsonUtil.getInt(msg.getData(),"turn_id",0);
                if(nextTurn<=turnId)return;
                turnId=nextTurn;
                int turnPlayerId = JsonUtil.getInt(msg.getData(), "current_turn_player_id", -1);
                int timeLimit = JsonUtil.getInt(msg.getData(), "time_limit_sec", 15);
                battlePanel.setServerTurn(turnPlayerId==currentUser.getUserId(),((Number)msg.getData().get("deadline_ms")).longValue(),msg.getServerTimeMs());
                break;
            }
            case TIMEOUT_NOTIFY: {
                if(battlePanel!=null)battlePanel.showNotice(JsonUtil.getInt(msg.getData(),"player_id",-1)==currentUser.getUserId()
                    ?"Bạn đã hết thời gian. Chuyển lượt cho đối thủ.":"Đối thủ hết thời gian. Đến lượt của bạn.");
                break;
            }
            case MATCH_END: {
                if (battlePanel == null || !endedMatches.add(matchId)) return;
                while(endedMatches.size()>128)endedMatches.remove(endedMatches.iterator().next());
                int winnerId = JsonUtil.getInt(msg.getData(), "winner_id", -1);
                boolean iWon = (winnerId == currentUser.getUserId());
                screen = Screen.RESULT;
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
                        currentUser.setTotalGames(JsonUtil.getInt(myScore,"total_games",currentUser.getTotalGames()));
                        applyPenalty(myScore);
                    }
                }
                battlePanel.setResultStorage(JsonUtil.getBoolean(msg.getData(), "storage_saved", false));
                battlePanel.showGameOver(iWon, awardedPts, reason, parseFleet(JsonUtil.getList(msg.getData(), "opponent_ships")),
                JsonUtil.getInt(msg.getData(), "opponent_shots", 0), JsonUtil.getLong(msg.getData(), "duration_seconds", 0));
                break;
            }
            case REMATCH_NOTIFY: {
                if(requestedRematch)break;
                final String invitedMatch=matchId;
                int choice = JOptionPane.showConfirmDialog(
                gameWindow,
                "Đối thủ " + opponentName + " muốn chơi lại ván mới!\nBạn có đồng ý không?",
                "Yêu cầu chơi lại",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
                );
                if(!Objects.equals(invitedMatch,matchId))break;
                Message cfm = new Message(MessageType.REMATCH_CONFIRM_REQ);
                cfm.put("accept", choice == JOptionPane.YES_OPTION);
                send(cfm);
                break;
            }
            case REMATCH_START: {
                matchId=incoming;
                roomId=JsonUtil.getString(msg.getData(),"room_id",roomId);
                startShipPlacementPhase();
                break;
            }
            case ERROR_NOTIFY: {
                if(battlePanel!=null)battlePanel.showNotice(msg.getMessage());
                else if(placementPanel!=null)placementPanel.showNotice(msg.getMessage());
                else if(lobbyFrame!=null)lobbyFrame.showStatus(msg.getMessage(),true);
                break;
            }
            default: break;
        }
    }

    /**
    * Reads the fleet layout used by PLACE_SHIPS_REQ and by MATCH_END once the match is over:
    * {@code [{id, size, coordinates:[{x,y}]}]}. Shared with the replay screen of the history.
    */
    public static List<Ship> parseFleet(List<Object> raw) {
        if (raw == null) return null;
        List<Ship> fleet = new ArrayList<>();
        for (Object item : raw) {
            if (!(item instanceof Map)) continue;
            @SuppressWarnings("unchecked") Map<String, Object> entry = (Map<String, Object>) item;
            String id = JsonUtil.getString(entry, "id", "");
            int size = JsonUtil.getInt(entry, "size", 0);
            List<Coordinate> cells = new ArrayList<>();
            List<Object> coords = JsonUtil.getList(entry, "coordinates");
            if (coords != null) for (Object cell : coords) {
                if (!(cell instanceof Map)) continue;
                @SuppressWarnings("unchecked") Map<String, Object> c = (Map<String, Object>) cell;
                cells.add(new Coordinate(JsonUtil.getInt(c, "x", -1), JsonUtil.getInt(c, "y", -1)));
            }
            if (id.isEmpty() || size <= 0 || cells.size() != size) continue;
            for (Coordinate c : cells) if (c.getX() < 0 || c.getX() > 9 || c.getY() < 0 || c.getY() > 9) {
                cells = null;
                break;
            }
            if (cells == null) continue;
            fleet.add(new Ship(id, size, cells));
        }
        return fleet.isEmpty() ? null : fleet;
    }

    private void startShipPlacementPhase() {
        screen = Screen.PLACEMENT;
        closeHistoryScreens();
        if (placementPanel != null) placementPanel.disposePanel();
        closeInvitations();
        requestedRematch=false;
        if (lobbyFrame != null) lobbyFrame.setVisible(false);
        if(battlePanel!=null)battlePanel.disposePanel();
        battlePanel=null;
        myShips=new ArrayList<>();
        turnId=0;
        pendingPlacement=null;
        if(gameWindow==null) {
            gameWindow = new JFrame();
            Theme.configureWindow(gameWindow,1440,940,1120,800);
            gameWindow.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            gameWindow.addWindowListener(new java.awt.event.WindowAdapter() {

                public void windowClosing(java.awt.event.WindowEvent e) {
                    if(battlePanel!=null)battlePanel.confirmLeave();else leaveRoom();
                }
            });
        }
        gameWindow.setTitle("Battleship • Triển khai hạm đội • " + opponentName);
        placementPanel = new PlacementPanel(this::sendReady);
        placementPanel.setPresets(new client.model.FleetPresets(endpoint, currentUser.getUserId()));
        placementPanel.setCancelReadyListener(this::cancelReady);
        placementPanel.setMatchInfo(opponentName,this::leaveRoom);
        gameWindow.setContentPane(placementPanel);
        gameWindow.revalidate();
        gameWindow.repaint();
        gameWindow.setVisible(true);
        if(chat!=null) {
            chat.attach(gameWindow);
            if(!practiceMatch)chat.opponent(opponentId,opponentName);
        }
    }

    private void startBattlePhase(int firstTurnPlayerId, int timeLimitSec) {
        if (gameWindow == null) return;
        screen = Screen.BATTLE;
        if (placementPanel != null) placementPanel.disposePanel();
        gameWindow.setTitle("Battleship • " + currentUser.getUsername() + " vs " + opponentName);
        battlePanel = new BattlePanel(currentUser.getUsername(), opponentName, myShips, this);
        battlePanel.setPractice(practiceMatch);
        gameWindow.setContentPane(battlePanel);
        placementPanel = null;
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
        send(msg);
    }
    @Override
    public void onSurrender() {
        if(awaitingLeave)return;
        awaitingLeave=true;
        send(new Message(MessageType.SURRENDER_REQ));
    }
    @Override
    public void onRematch() {
        if(requestedRematch || screen != Screen.RESULT)return;
        requestedRematch=true;
        send(new Message(MessageType.REMATCH_REQ));
        if(gameWindow!=null)gameWindow.setTitle("Đã yêu cầu chơi lại – chờ đối thủ");
    }
    @Override
    public void onExitToLobby() {
        leaveRoom();
    }

    private void exitToLobby() {
        screen = Screen.LOBBY;
        clearGame();
        awaitingLeave=false;
        if(lobbyFrame!=null && currentUser!=null) {
            lobbyFrame.resetQueueButton();
            lobbyFrame.clearRoom();
            lobbyFrame.updateUserInfo(currentUser);
            lobbyFrame.setVisible(true);
            if(chat!=null)chat.attach(lobbyFrame);
            refreshOnlineUsers();
        }
    }
}
