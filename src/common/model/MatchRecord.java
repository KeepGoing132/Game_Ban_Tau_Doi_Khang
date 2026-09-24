package common.model;

public class MatchRecord {
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

    public MatchRecord() {
        this.timestamp = System.currentTimeMillis();
    }

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
}
