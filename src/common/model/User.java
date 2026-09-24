package common.model;

public class User {
    private int userId;
    private String username;
    private String password;
    private int totalPoints;
    private int totalWins;
    private int totalGames;
    private UserStatus status = UserStatus.OFFLINE;

    public User() {
    }

    public User(int userId, String username, String password) {
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.totalPoints = 0;
        this.totalWins = 0;
        this.totalGames = 0;
        this.status = UserStatus.OFFLINE;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public int getTotalPoints() {
        return totalPoints;
    }

    public void setTotalPoints(int totalPoints) {
        this.totalPoints = totalPoints;
    }

    public int getTotalWins() {
        return totalWins;
    }

    public void setTotalWins(int totalWins) {
        this.totalWins = totalWins;
    }

    public int getTotalGames() {
        return totalGames;
    }

    public void setTotalGames(int totalGames) {
        this.totalGames = totalGames;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public double getWinRate() {
        if (totalGames <= 0) return 0.0;
        double rate = ((double) totalWins / totalGames) * 100.0;
        return Math.round(rate * 100.0) / 100.0;
    }

    public void addWin(int points) {
        this.totalGames++;
        this.totalWins++;
        this.totalPoints += points;
    }

    public void addLoss(int points) {
        this.totalGames++;
        this.totalPoints += points;
    }
}
