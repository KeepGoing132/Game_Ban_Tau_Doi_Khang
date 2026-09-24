package server.manager;

import common.model.Coordinate;
import common.model.Ship;
import common.model.UserStatus;
import common.protocol.JsonUtil;
import common.protocol.Message;
import common.protocol.MessageType;
import server.model.MatchSession;
import server.network.ClientHandler;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MatchManager {
    private final UserManager userManager;
    private final StorageManager storageManager;
    private final Map<String, MatchSession> activeSessions = new ConcurrentHashMap<>();
    private final Map<ClientHandler, MatchSession> clientSessionMap = new ConcurrentHashMap<>();

    public MatchManager(UserManager userManager, StorageManager storageManager) {
        this.userManager = userManager;
        this.storageManager = storageManager;
    }

    public synchronized MatchSession createSession(ClientHandler p1, ClientHandler p2) {
        String matchId = "M_" + System.currentTimeMillis() + "_" + p1.getUser().getUserId() + "_" + p2.getUser().getUserId();
        MatchSession session = new MatchSession(matchId, p1, p2, userManager, storageManager);

        activeSessions.put(matchId, session);
        clientSessionMap.put(p1, session);
        clientSessionMap.put(p2, session);

        return session;
    }

    public MatchSession getSession(ClientHandler client) {
        return clientSessionMap.get(client);
    }

    public void handlePlaceShips(ClientHandler client, Message msg) {
        MatchSession session = getSession(client);
        if (session == null) {
            client.sendMessage(Message.error(MessageType.ERROR_NOTIFY, "Bạn hiện không ở trong phiên thi đấu nào!"));
            return;
        }

        List<Object> shipListRaw = JsonUtil.getList(msg.getData(), "ships");
        if (shipListRaw == null) {
            client.sendMessage(Message.error(MessageType.ERROR_NOTIFY, "Dữ liệu tàu không hợp lệ!"));
            return;
        }

        List<Ship> ships = new ArrayList<>();
        for (Object item : shipListRaw) {
            if (item instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> smap = (Map<String, Object>) item;
                String id = JsonUtil.getString(smap, "id", "SHIP");
                int size = JsonUtil.getInt(smap, "size", 0);
                List<Object> coordsRaw = JsonUtil.getList(smap, "coordinates");
                List<Coordinate> coords = new ArrayList<>();
                if (coordsRaw != null) {
                    for (Object cItem : coordsRaw) {
                        if (cItem instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> cmap = (Map<String, Object>) cItem;
                            int x = JsonUtil.getInt(cmap, "x", 0);
                            int y = JsonUtil.getInt(cmap, "y", 0);
                            coords.add(new Coordinate(x, y));
                        }
                    }
                }
                ships.add(new Ship(id, size, coords));
            }
        }

        boolean valid = session.setPlayerShips(client, ships);
        if (!valid) {
            client.sendMessage(Message.error(MessageType.ERROR_NOTIFY, "Bố trí tàu không hợp lệ! Vui lòng kiểm tra lại kích thước, vị trí và không để tàu đè lên nhau."));
            return;
        }

        client.sendMessage(new Message(MessageType.PLACE_SHIPS_REQ, "SUCCESS", "Đã lưu bố trí tàu thành công!"));
    }

    public void handleReady(ClientHandler client) {
        MatchSession session = getSession(client);
        if (session == null) return;
        session.setPlayerReady(client);
    }

    public void handleFire(ClientHandler client, Message msg) {
        MatchSession session = getSession(client);
        if (session == null) return;

        int x = JsonUtil.getInt(msg.getData(), "x", -1);
        int y = JsonUtil.getInt(msg.getData(), "y", -1);
        if (x < 0 || x >= 10 || y < 0 || y >= 10) {
            client.sendMessage(Message.error(MessageType.ERROR_NOTIFY, "Tọa độ bắn không hợp lệ!"));
            return;
        }

        session.handleFire(client, new Coordinate(x, y));
    }

    public void handleSurrender(ClientHandler client) {
        MatchSession session = getSession(client);
        if (session == null) return;
        session.surrender(client);
        cleanupSession(session);
    }

    public void handleClientDisconnect(ClientHandler client) {
        MatchSession session = clientSessionMap.remove(client);
        if (session != null && !session.isFinished()) {
            session.surrender(client);
            cleanupSession(session);
        }
    }

    public void handleRematchReq(ClientHandler client) {
        MatchSession session = getSession(client);
        if (session == null) return;
        session.requestRematch(client);
    }

    public void handleRematchConfirm(ClientHandler client, Message msg) {
        MatchSession session = getSession(client);
        if (session == null) return;
        boolean accept = JsonUtil.getBoolean(msg.getData(), "accept", false);
        session.confirmRematch(client, accept);
    }

    public void cleanupSession(MatchSession session) {
        if (session == null) return;
        activeSessions.remove(session.getMatchId());
        clientSessionMap.remove(session.getPlayer1());
        clientSessionMap.remove(session.getPlayer2());
    }
}
