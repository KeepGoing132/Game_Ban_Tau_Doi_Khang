package server.model;

import common.config.GameConfig;
import common.model.*;
import common.protocol.Message;
import common.protocol.MessageType;
import server.manager.StorageManager;
import server.manager.UserManager;
import server.network.ClientHandler;

import java.util.*;
import java.util.concurrent.*;

public class MatchSession {
    private final String matchId;
    private final ClientHandler player1;
    private final ClientHandler player2;
    private final Board board1 = new Board();
    private final Board board2 = new Board();
    private boolean p1Ready = false;
    private boolean p2Ready = false;

    private int currentTurnPlayerId = -1;
    private long startTime;
    private int p1Shots = 0;
    private int p2Shots = 0;
    private boolean isFinished = false;

    private boolean p1RematchAgreed = false;
    private boolean p2RematchAgreed = false;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> turnTimerFuture;

    private final UserManager userManager;
    private final StorageManager storageManager;

    public MatchSession(String matchId, ClientHandler player1, ClientHandler player2, UserManager userManager, StorageManager storageManager) {
        this.matchId = matchId;
        this.player1 = player1;
        this.player2 = player2;
        this.userManager = userManager;
        this.storageManager = storageManager;
    }

    public String getMatchId() {
        return matchId;
    }

    public ClientHandler getPlayer1() {
        return player1;
    }

    public ClientHandler getPlayer2() {
        return player2;
    }

    public ClientHandler getOpponent(ClientHandler player) {
        if (player.equals(player1)) return player2;
        if (player.equals(player2)) return player1;
        return null;
    }

    public Board getBoard(ClientHandler player) {
        return player.equals(player1) ? board1 : board2;
    }

    public Board getOpponentBoard(ClientHandler player) {
        return player.equals(player1) ? board2 : board1;
    }

    public synchronized boolean setPlayerShips(ClientHandler player, List<Ship> ships) {
        Board board = getBoard(player);
        board.setShips(ships);
        return board.validateAllShips();
    }

    public synchronized void setPlayerReady(ClientHandler player) {
        if (isFinished) return;
        if (player.equals(player1)) {
            p1Ready = true;
        } else if (player.equals(player2)) {
            p2Ready = true;
        }

        ClientHandler opp = getOpponent(player);
        if (opp != null) {
            Message oppReadyMsg = new Message(MessageType.OPPONENT_READY_NOTIFY);
            oppReadyMsg.put("player_id", player.getUser().getUserId());
            oppReadyMsg.put("username", player.getUser().getUsername());
            opp.sendMessage(oppReadyMsg);
        }

        if (p1Ready && p2Ready) {
            startMatch();
        }
    }

    private synchronized void startMatch() {
        this.startTime = System.currentTimeMillis();
        // Chọn ngẫu nhiên người bắn phát đầu tiên (50/50)
        boolean p1First = new Random().nextBoolean();
        currentTurnPlayerId = p1First ? player1.getUser().getUserId() : player2.getUser().getUserId();

        Message startMsg = new Message(MessageType.MATCH_START);
        startMsg.put("match_id", matchId);
        startMsg.put("first_turn_player_id", currentTurnPlayerId);
        startMsg.put("turn_time_limit_sec", GameConfig.TURN_TIMEOUT_SECONDS);

        Map<String, Object> p1Data = new LinkedHashMap<>();
        p1Data.put("user_id", player1.getUser().getUserId());
        p1Data.put("username", player1.getUser().getUsername());
        startMsg.put("player1", p1Data);

        Map<String, Object> p2Data = new LinkedHashMap<>();
        p2Data.put("user_id", player2.getUser().getUserId());
        p2Data.put("username", player2.getUser().getUsername());
        startMsg.put("player2", p2Data);

        player1.sendMessage(startMsg);
        player2.sendMessage(startMsg);

        startTurnTimer();
    }

    private synchronized void startTurnTimer() {
        if (turnTimerFuture != null && !turnTimerFuture.isDone()) {
            turnTimerFuture.cancel(true);
        }
        if (isFinished) return;

        turnTimerFuture = scheduler.schedule(this::handleTurnTimeout, GameConfig.TURN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private synchronized void handleTurnTimeout() {
        if (isFinished) return;

        ClientHandler currentShooter = (currentTurnPlayerId == player1.getUser().getUserId()) ? player1 : player2;
        Board oppBoard = getOpponentBoard(currentShooter);

        // Chọn ngẫu nhiên một ô chưa bắn trên bàn cờ đối phương để bắn tự động
        List<Coordinate> unshotCoords = new ArrayList<>();
        for (int x = 0; x < GameConfig.BOARD_SIZE; x++) {
            for (int y = 0; y < GameConfig.BOARD_SIZE; y++) {
                Coordinate c = new Coordinate(x, y);
                if (!oppBoard.hasBeenShot(c)) {
                    unshotCoords.add(c);
                }
            }
        }

        if (unshotCoords.isEmpty()) return;

        Coordinate autoCoord = unshotCoords.get(new Random().nextInt(unshotCoords.size()));

        Message notifyMsg = new Message(MessageType.TIMEOUT_NOTIFY);
        notifyMsg.put("player_id", currentShooter.getUser().getUserId());
        notifyMsg.put("auto_x", autoCoord.getX());
        notifyMsg.put("auto_y", autoCoord.getY());
        notifyMsg.setMessage("Người chơi " + currentShooter.getUser().getUsername() + " hết 15s lượt bắn! Server tự động bắn ô (" + autoCoord.getX() + ", " + autoCoord.getY() + ")");
        player1.sendMessage(notifyMsg);
        player2.sendMessage(notifyMsg);

        executeShot(currentShooter, autoCoord);
    }

    public synchronized void handleFire(ClientHandler shooter, Coordinate c) {
        if (isFinished) return;
        if (shooter.getUser().getUserId() != currentTurnPlayerId) {
            shooter.sendMessage(Message.error(MessageType.ERROR_NOTIFY, "Chưa tới lượt bắn của bạn!"));
            return;
        }

        Board oppBoard = getOpponentBoard(shooter);
        if (oppBoard.hasBeenShot(c)) {
            shooter.sendMessage(Message.error(MessageType.ERROR_NOTIFY, "Ô này đã được bắn trước đó!"));
            return;
        }

        // Hủy timer cũ vì đã bắn trong hạn 15s
        if (turnTimerFuture != null && !turnTimerFuture.isDone()) {
            turnTimerFuture.cancel(true);
        }

        executeShot(shooter, c);
    }

    private synchronized void executeShot(ClientHandler shooter, Coordinate c) {
        if (shooter.equals(player1)) p1Shots++;
        else p2Shots++;

        ClientHandler target = getOpponent(shooter);
        Board oppBoard = getOpponentBoard(shooter);

        ShotResult result = oppBoard.receiveShot(c);
        Ship sunkShip = null;
        if (result == ShotResult.SUNK) {
            sunkShip = oppBoard.getShipAt(c);
        }

        // Kiểm tra xem trận đấu đã ngã ngũ chưa
        boolean matchOver = oppBoard.areAllShipsSunk();

        int nextTurnId;
        if (matchOver) {
            nextTurnId = -1;
        } else if (result == ShotResult.HIT || result == ShotResult.SUNK) {
            // LUẬT: Bắn trúng hoặc đánh chìm tàu -> TIẾP TỤC ĐƯỢC BẮN!
            nextTurnId = shooter.getUser().getUserId();
        } else {
            // Bắn trượt (MISS) -> Đổi lượt cho đối thủ!
            nextTurnId = target.getUser().getUserId();
        }
        currentTurnPlayerId = nextTurnId;

        // Gửi kết quả phát bắn tới cả 2 bên
        Message fireResultMsg = new Message(MessageType.FIRE_RESULT);
        fireResultMsg.put("shooter_id", shooter.getUser().getUserId());
        fireResultMsg.put("target_id", target.getUser().getUserId());
        Map<String, Object> coordMap = new LinkedHashMap<>();
        coordMap.put("x", c.getX());
        coordMap.put("y", c.getY());
        fireResultMsg.put("coordinate", coordMap);
        fireResultMsg.put("result", result.name());
        fireResultMsg.put("remaining_ships_target", oppBoard.getRemainingShipsCount());
        fireResultMsg.put("next_turn_player_id", nextTurnId);

        if (sunkShip != null) {
            List<Map<String, Object>> sunkCoords = new ArrayList<>();
            for (Coordinate sc : sunkShip.getCoordinates()) {
                Map<String, Object> sm = new LinkedHashMap<>();
                sm.put("x", sc.getX());
                sm.put("y", sc.getY());
                sunkCoords.add(sm);
            }
            fireResultMsg.put("sunk_ship_coords", sunkCoords);
            fireResultMsg.put("sunk_ship_name", sunkShip.getId());
        }

        player1.sendMessage(fireResultMsg);
        player2.sendMessage(fireResultMsg);

        if (matchOver) {
            endMatch(shooter, target, "ALL_SHIPS_DESTROYED");
        } else {
            // Phát tiếp thông báo TURN_CHANGE và kích hoạt Timer 15s mới
            Message turnMsg = new Message(MessageType.TURN_CHANGE);
            turnMsg.put("current_turn_player_id", currentTurnPlayerId);
            turnMsg.put("time_limit_sec", GameConfig.TURN_TIMEOUT_SECONDS);
            player1.sendMessage(turnMsg);
            player2.sendMessage(turnMsg);

            startTurnTimer();
        }
    }

    public synchronized void surrender(ClientHandler surrenderingPlayer) {
        if (isFinished) return;
        ClientHandler winner = getOpponent(surrenderingPlayer);
        endMatch(winner, surrenderingPlayer, "SURRENDER");
    }

    public synchronized void endMatch(ClientHandler winner, ClientHandler loser, String reason) {
        if (isFinished) return;
        isFinished = true;

        if (turnTimerFuture != null && !turnTimerFuture.isDone()) {
            turnTimerFuture.cancel(true);
        }
        scheduler.shutdown();

        long durationSec = (System.currentTimeMillis() - startTime) / 1000;

        // Cập nhật điểm số
        winner.getUser().addWin(GameConfig.WIN_POINTS);
        loser.getUser().addLoss(GameConfig.LOSE_POINTS);
        storageManager.saveUsers();

        // Ghi lại lịch sử trận đấu
        MatchRecord record = new MatchRecord();
        record.setMatchId(matchId);
        record.setPlayer1Id(player1.getUser().getUserId());
        record.setPlayer1Name(player1.getUser().getUsername());
        record.setPlayer2Id(player2.getUser().getUserId());
        record.setPlayer2Name(player2.getUser().getUsername());
        record.setWinnerId(winner.getUser().getUserId());
        record.setWinnerName(winner.getUser().getUsername());
        record.setDurationSeconds(durationSec);
        record.setPlayer1Shots(p1Shots);
        record.setPlayer2Shots(p2Shots);
        storageManager.saveMatch(record);

        // Gửi MATCH_END tới 2 Client
        Message endMsg = new Message(MessageType.MATCH_END);
        endMsg.put("match_id", matchId);
        endMsg.put("winner_id", winner.getUser().getUserId());
        endMsg.put("loser_id", loser.getUser().getUserId());
        endMsg.put("reason", reason);
        endMsg.put("duration_seconds", durationSec);
        endMsg.put("player1_shots", p1Shots);
        endMsg.put("player2_shots", p2Shots);

        Map<String, Object> p1Score = new LinkedHashMap<>();
        p1Score.put("points_awarded", winner.equals(player1) ? GameConfig.WIN_POINTS : GameConfig.LOSE_POINTS);
        p1Score.put("total_points", player1.getUser().getTotalPoints());
        p1Score.put("total_wins", player1.getUser().getTotalWins());

        Map<String, Object> p2Score = new LinkedHashMap<>();
        p2Score.put("points_awarded", winner.equals(player2) ? GameConfig.WIN_POINTS : GameConfig.LOSE_POINTS);
        p2Score.put("total_points", player2.getUser().getTotalPoints());
        p2Score.put("total_wins", player2.getUser().getTotalWins());

        Map<String, Object> scoresMap = new LinkedHashMap<>();
        scoresMap.put(String.valueOf(player1.getUser().getUserId()), p1Score);
        scoresMap.put(String.valueOf(player2.getUser().getUserId()), p2Score);
        endMsg.put("scores", scoresMap);

        player1.sendMessage(endMsg);
        player2.sendMessage(endMsg);

        // Đổi trạng thái user về IDLE
        userManager.updateUserStatus(player1.getUser(), UserStatus.IDLE);
        userManager.updateUserStatus(player2.getUser(), UserStatus.IDLE);
    }

    public synchronized void requestRematch(ClientHandler requester) {
        if (requester.equals(player1)) p1RematchAgreed = true;
        if (requester.equals(player2)) p2RematchAgreed = true;

        ClientHandler opp = getOpponent(requester);
        if (opp != null) {
            Message notify = new Message(MessageType.REMATCH_NOTIFY);
            notify.put("requester_id", requester.getUser().getUserId());
            notify.put("requester_name", requester.getUser().getUsername());
            opp.sendMessage(notify);
        }

        checkBothRematchAgreed();
    }

    public synchronized void confirmRematch(ClientHandler responder, boolean accept) {
        if (!accept) {
            ClientHandler opp = getOpponent(responder);
            if (opp != null) {
                opp.sendMessage(Message.error(MessageType.ERROR_NOTIFY, "Đối thủ đã từ chối chơi lại."));
            }
            return;
        }

        if (responder.equals(player1)) p1RematchAgreed = true;
        if (responder.equals(player2)) p2RematchAgreed = true;

        checkBothRematchAgreed();
    }

    private synchronized void checkBothRematchAgreed() {
        if (p1RematchAgreed && p2RematchAgreed) {
            // Cả hai đồng ý chơi lại -> Reset bàn cờ và gửi REMATCH_START
            p1Ready = false;
            p2Ready = false;
            p1RematchAgreed = false;
            p2RematchAgreed = false;
            board1.getShips().clear();
            board1.getShotHistory().clear();
            board2.getShips().clear();
            board2.getShotHistory().clear();
            isFinished = false;
            p1Shots = 0;
            p2Shots = 0;

            userManager.updateUserStatus(player1.getUser(), UserStatus.BUSY);
            userManager.updateUserStatus(player2.getUser(), UserStatus.BUSY);

            Message rematchStartMsg = new Message(MessageType.REMATCH_START);
            player1.sendMessage(rematchStartMsg);
            player2.sendMessage(rematchStartMsg);
        }
    }

    public boolean isFinished() {
        return isFinished;
    }
}
