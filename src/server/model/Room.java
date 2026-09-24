package server.model;

import server.network.ClientHandler;

public class Room {
    public enum RoomStatus {
        WAITING,
        IN_GAME,
        CLOSED
    }

    private final String roomCode;
    private ClientHandler host;
    private ClientHandler guest;
    private RoomStatus status = RoomStatus.WAITING;

    public Room(String roomCode, ClientHandler host) {
        this.roomCode = roomCode;
        this.host = host;
    }

    public String getRoomCode() {
        return roomCode;
    }

    public ClientHandler getHost() {
        return host;
    }

    public void setHost(ClientHandler host) {
        this.host = host;
    }

    public ClientHandler getGuest() {
        return guest;
    }

    public void setGuest(ClientHandler guest) {
        this.guest = guest;
    }

    public RoomStatus getStatus() {
        return status;
    }

    public void setStatus(RoomStatus status) {
        this.status = status;
    }

    public boolean isFull() {
        return host != null && guest != null;
    }

    public boolean contains(ClientHandler client) {
        return client != null && (client.equals(host) || client.equals(guest));
    }

    public ClientHandler getOpponent(ClientHandler client) {
        if (client == null) return null;
        if (client.equals(host)) return guest;
        if (client.equals(guest)) return host;
        return null;
    }
}
