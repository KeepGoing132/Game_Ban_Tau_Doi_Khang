package server.manager;

import common.model.UserStatus;
import common.protocol.Message;
import common.protocol.MessageType;
import server.model.Room;
import server.network.ClientHandler;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class LobbyManager {
    private final UserManager userManager;
    private final MatchManager matchManager;

    private final Map<String, Room> activeRooms = new ConcurrentHashMap<>();
    private final Map<ClientHandler, Room> clientRoomMap = new ConcurrentHashMap<>();
    private final Queue<ClientHandler> matchmakingQueue = new ConcurrentLinkedQueue<>();

    public LobbyManager(UserManager userManager, MatchManager matchManager) {
        this.userManager = userManager;
        this.matchManager = matchManager;
    }

    // ==========================================
    // ROOM MANAGEMENT
    // ==========================================

    public synchronized String createRoom(ClientHandler host) {
        leaveCurrentRoom(host);
        removeFromQueue(host);

        String roomCode = generateRoomCode();
        Room room = new Room(roomCode, host);
        activeRooms.put(roomCode, room);
        clientRoomMap.put(host, room);

        userManager.updateUserStatus(host.getUser(), UserStatus.BUSY);
        return roomCode;
    }

    public synchronized boolean joinRoom(String roomCode, ClientHandler guest) {
        if (roomCode == null) return false;
        roomCode = roomCode.trim().toUpperCase();

        Room room = activeRooms.get(roomCode);
        if (room == null || room.isFull() || room.getStatus() != Room.RoomStatus.WAITING) {
            return false;
        }

        leaveCurrentRoom(guest);
        removeFromQueue(guest);

        room.setGuest(guest);
        room.setStatus(Room.RoomStatus.IN_GAME);
        clientRoomMap.put(guest, room);

        userManager.updateUserStatus(guest.getUser(), UserStatus.BUSY);

        // Thông báo cho cả 2 người trong phòng
        Message joinedMsg = new Message(MessageType.ROOM_USER_JOINED);
        joinedMsg.put("room_code", roomCode);
        joinedMsg.put("host_id", room.getHost().getUser().getUserId());
        joinedMsg.put("host_name", room.getHost().getUser().getUsername());
        joinedMsg.put("guest_id", guest.getUser().getUserId());
        joinedMsg.put("guest_name", guest.getUser().getUsername());

        room.getHost().sendMessage(joinedMsg);
        guest.sendMessage(joinedMsg);

        // Bắt đầu phiên chuẩn bị đặt tàu
        matchManager.createSession(room.getHost(), guest);

        return true;
    }

    public synchronized void leaveCurrentRoom(ClientHandler client) {
        if (client == null) return;
        Room room = clientRoomMap.remove(client);
        if (room != null) {
            ClientHandler opponent = room.getOpponent(client);
            activeRooms.remove(room.getRoomCode());

            if (opponent != null) {
                clientRoomMap.remove(opponent);
                userManager.updateUserStatus(opponent.getUser(), UserStatus.IDLE);
                opponent.sendMessage(new Message(MessageType.ROOM_USER_LEFT, "SUCCESS", "Đối thủ đã rời khỏi phòng"));
            }

            userManager.updateUserStatus(client.getUser(), UserStatus.IDLE);
        }
    }

    private String generateRoomCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        Random random = new Random();
        String code;
        do {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                sb.append(chars.charAt(random.nextInt(chars.length())));
            }
            code = sb.toString();
        } while (activeRooms.containsKey(code));
        return code;
    }

    // ==========================================
    // AUTO MATCHMAKING (QUICK MATCH)
    // ==========================================

    public synchronized void joinMatchmaking(ClientHandler client) {
        leaveCurrentRoom(client);
        if (!matchmakingQueue.contains(client)) {
            matchmakingQueue.add(client);
        }

        client.sendMessage(new Message(MessageType.QUEUE_JOIN_REQ, "SUCCESS", "Đã vào hàng chờ ghép trận..."));

        // Nếu hàng chờ có từ 2 người trở lên, ghép cặp ngay
        checkMatchmakingQueue();
    }

    public synchronized void removeFromQueue(ClientHandler client) {
        matchmakingQueue.remove(client);
    }

    private synchronized void checkMatchmakingQueue() {
        while (matchmakingQueue.size() >= 2) {
            ClientHandler p1 = matchmakingQueue.poll();
            ClientHandler p2 = matchmakingQueue.poll();

            if (p1 == null || p2 == null || !p1.isConnected() || !p2.isConnected()) {
                continue;
            }

            userManager.updateUserStatus(p1.getUser(), UserStatus.BUSY);
            userManager.updateUserStatus(p2.getUser(), UserStatus.BUSY);

            Message matchFoundP1 = new Message(MessageType.MATCH_FOUND);
            matchFoundP1.put("opponent_id", p2.getUser().getUserId());
            matchFoundP1.put("opponent_name", p2.getUser().getUsername());

            Message matchFoundP2 = new Message(MessageType.MATCH_FOUND);
            matchFoundP2.put("opponent_id", p1.getUser().getUserId());
            matchFoundP2.put("opponent_name", p1.getUser().getUsername());

            p1.sendMessage(matchFoundP1);
            p2.sendMessage(matchFoundP2);

            matchManager.createSession(p1, p2);
        }
    }

    // ==========================================
    // DIRECT INVITATION
    // ==========================================

    public void handleInvite(ClientHandler inviter, int targetUserId) {
        ClientHandler target = userManager.getClientById(targetUserId);
        if (target == null) {
            inviter.sendMessage(Message.error(MessageType.INVITE_RESULT, "Người chơi không online hoặc không tồn tại."));
            return;
        }

        if (target.getUser().getStatus() != UserStatus.IDLE) {
            inviter.sendMessage(Message.error(MessageType.INVITE_RESULT, "Người chơi này đang bận thi đấu hoặc ở phòng khác!"));
            return;
        }

        Message notify = new Message(MessageType.INVITE_NOTIFY);
        notify.put("inviter_id", inviter.getUser().getUserId());
        notify.put("inviter_name", inviter.getUser().getUsername());
        notify.put("inviter_points", inviter.getUser().getTotalPoints());
        target.sendMessage(notify);
    }

    public void handleInviteResponse(ClientHandler responder, int inviterId, boolean accept) {
        ClientHandler inviter = userManager.getClientById(inviterId);
        if (inviter == null || !inviter.isConnected()) {
            responder.sendMessage(Message.error(MessageType.ERROR_NOTIFY, "Người mời đã thoát game."));
            return;
        }

        if (!accept) {
            Message resMsg = Message.error(MessageType.INVITE_RESULT, "Người chơi " + responder.getUser().getUsername() + " đã từ chối lời mời.");
            inviter.sendMessage(resMsg);
            return;
        }

        // Người nhận chấp nhận (Accept) -> Tạo trận giữa 2 người
        leaveCurrentRoom(inviter);
        leaveCurrentRoom(responder);
        removeFromQueue(inviter);
        removeFromQueue(responder);

        userManager.updateUserStatus(inviter.getUser(), UserStatus.BUSY);
        userManager.updateUserStatus(responder.getUser(), UserStatus.BUSY);

        Message okMsg1 = new Message(MessageType.INVITE_RESULT, "SUCCESS", "Bắt đầu trận đấu!");
        okMsg1.put("opponent_id", responder.getUser().getUserId());
        okMsg1.put("opponent_name", responder.getUser().getUsername());

        Message okMsg2 = new Message(MessageType.INVITE_RESULT, "SUCCESS", "Bắt đầu trận đấu!");
        okMsg2.put("opponent_id", inviter.getUser().getUserId());
        okMsg2.put("opponent_name", inviter.getUser().getUsername());

        inviter.sendMessage(okMsg1);
        responder.sendMessage(okMsg2);

        matchManager.createSession(inviter, responder);
    }

    public Room getRoomByClient(ClientHandler client) {
        return clientRoomMap.get(client);
    }
}
