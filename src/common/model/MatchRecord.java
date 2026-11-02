package common.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MatchRecord {
    private int loserId;
    private String loserName;
    private long startedAt;
    private long endedAt;
    private int player1Points;
    private int player2Points;
    private String reason;
    private long matchmakingBlockedUntil;
    public long getMatchmakingBlockedUntil() { return matchmakingBlockedUntil; }
    public void setMatchmakingBlockedUntil(long value) { matchmakingBlockedUntil = value; }
    private boolean legacy;
    private String matchId;
    private int player1Id;
    private String player1Name;
    private int player2Id;
    private String player2Name;
    private int winnerId;
    private String winnerName;
    private long durationSeconds;
    private int player1Shots;
    private int player2Shots;
    private long timestamp;
    private List<Map<String,Object>> player1Ships = new ArrayList<>();
    private List<Map<String,Object>> player2Ships = new ArrayList<>();
    private List<Map<String,Object>> shotLog = new ArrayList<>();

    public MatchRecord() {
        this.timestamp = 0;
    }

    public List<Map<String,Object>> getPlayer1Ships() { return player1Ships; }
    public void setPlayer1Ships(List<Map<String,Object>> value) { player1Ships = value == null ? new ArrayList<Map<String,Object>>() : value; }
    public List<Map<String,Object>> getPlayer2Ships() { return player2Ships; }
    public void setPlayer2Ships(List<Map<String,Object>> value) { player2Ships = value == null ? new ArrayList<Map<String,Object>>() : value; }
    public List<Map<String,Object>> getShotLog() { return shotLog; }
    public void setShotLog(List<Map<String,Object>> value) { shotLog = value == null ? new ArrayList<Map<String,Object>>() : value; }
    public boolean hasReplay() { return !player1Ships.isEmpty() && !player2Ships.isEmpty(); }

    public String getMatchId() {
        return matchId;
    }

    public void setMatchId(String matchId) {
        this.matchId = matchId;
    }

    public int getPlayer1Id() {
        return player1Id;
    }

    public void setPlayer1Id(int player1Id) {
        this.player1Id = player1Id;
    }

    public String getPlayer1Name() {
        return player1Name;
    }

    public void setPlayer1Name(String player1Name) {
        this.player1Name = player1Name;
    }

    public int getPlayer2Id() {
        return player2Id;
    }

    public void setPlayer2Id(int player2Id) {
        this.player2Id = player2Id;
    }

    public String getPlayer2Name() {
        return player2Name;
    }

    public void setPlayer2Name(String player2Name) {
        this.player2Name = player2Name;
    }

    public int getWinnerId() {
        return winnerId;
    }

    public void setWinnerId(int winnerId) {
        this.winnerId = winnerId;
    }

    public String getWinnerName() {
        return winnerName;
    }

    public void setWinnerName(String winnerName) {
        this.winnerName = winnerName;
    }

    public long getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(long durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public int getPlayer1Shots() {
        return player1Shots;
    }

    public void setPlayer1Shots(int player1Shots) {
        this.player1Shots = player1Shots;
    }

    public int getPlayer2Shots() {
        return player2Shots;
    }

    public void setPlayer2Shots(int player2Shots) {
        this.player2Shots = player2Shots;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    public int getLoserId() { return loserId; }
    public void setLoserId(int value) { loserId = value; }
    public String getLoserName() { return loserName; }
    public void setLoserName(String value) { loserName = value; }
    public long getStartedAt() { return startedAt; }
    public void setStartedAt(long value) { startedAt = value; }
    public long getEndedAt() { return endedAt; }
    public void setEndedAt(long value) { endedAt = value; }
    public int getPlayer1Points() { return player1Points; }
    public void setPlayer1Points(int value) { player1Points = value; }
    public int getPlayer2Points() { return player2Points; }
    public void setPlayer2Points(int value) { player2Points = value; }
    public String getReason() { return reason; }
    public void setReason(String value) { reason = value; }
    public boolean getLegacy() { return legacy; }
    public void setLegacy(boolean value) { legacy = value; }
}
