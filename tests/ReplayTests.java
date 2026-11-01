package tests;

import common.model.MatchRecord;
import common.protocol.*;
import server.manager.PasswordHasher;
import server.manager.StorageManager;
import server.network.GameServer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Match replay: the sealed fleets and the ordered shot log are stored with the result, survive a
 * server restart, and are served only to the two players of that match. Records written before the
 * feature stay readable and simply report that no replay exists.
 */
public final class ReplayTests {
    private static void check(boolean condition, String message) { TestRunner.check(condition, message); }

    /** Two different layouts, so "my fleet" and "the enemy fleet" can be told apart. */
    private static List<Map<String, Object>> fleet(boolean vertical) {
        List<Map<String, Object>> list = new ArrayList<>();
        int[] sizes = {5, 4, 3, 3, 2};
        for (int i = 0; i < sizes.length; i++) {
            Map<String, Object> ship = new LinkedHashMap<>();
            ship.put("id", "S" + i); ship.put("size", sizes[i]);
            List<Map<String, Object>> coords = new ArrayList<>();
            for (int n = 0; n < sizes[i]; n++) {
                Map<String, Object> cell = new LinkedHashMap<>();
                cell.put("x", vertical ? i : n); cell.put("y", vertical ? n : i);
                coords.add(cell);
            }
            ship.put("coordinates", coords); list.add(ship);
        }
        return list;
    }

    public static void run() throws Exception {
        Path dir = DomainTests.empty("battleship-replay-");
        GameServer server = new GameServer(0, dir);
        Thread thread = PenaltyTests.start(server);
        String matchId;
        int shots;
        try (SocketPeer a = new SocketPeer(server.getPort(), true); SocketPeer b = new SocketPeer(server.getPort(), true); SocketPeer c = new SocketPeer(server.getPort(), true)) {
            a.login("replay_a"); b.login("replay_b"); c.login("replay_c");
            matchId = IntegrationTests.queuePair(a, b);
            IntegrationTests.ok(a, IntegrationTests.match(MessageType.PLACE_SHIPS_REQ, matchId).put("ships", fleet(false)));
            IntegrationTests.ok(b, IntegrationTests.match(MessageType.PLACE_SHIPS_REQ, matchId).put("ships", fleet(true)));
            IntegrationTests.ok(a, IntegrationTests.ready(matchId, true)); IntegrationTests.ok(b, IntegrationTests.ready(matchId, true));
            Message start = a.waitType(MessageType.MATCH_START); b.waitType(MessageType.MATCH_START);
            SocketPeer shooter = JsonUtil.requireInt(start.getData(), "current_turn_player_id") == a.userId ? a : b;
            SocketPeer target = shooter == a ? b : a;
            int turn = JsonUtil.requireInt(start.getData(), "turn_id"), sunk = 0;
            shots = 0;
            int[] sizes = {5, 4, 3, 3, 2};
            for (int i = 0; i < sizes.length; i++) for (int n = 0; n < sizes[i]; n++) {
                // the target of b is laid out vertically, the target of a horizontally
                int x = target == a ? n : i, y = target == a ? i : n;
                Message result = IntegrationTests.ok(shooter, IntegrationTests.match(MessageType.FIRE_REQ, matchId).put("turn_id", turn).put("x", x).put("y", y));
                shots++;
                check(!result.getData().containsKey("sunk_ship_coords") || "SUNK".equals(result.getData().get("result")), "sunk cells only on a sunk shot");
                if ("SUNK".equals(result.getData().get("result"))) sunk++;
                if (sunk < 5) { Message next = shooter.waitType(MessageType.TURN_CHANGE); target.waitType(MessageType.TURN_CHANGE); turn = JsonUtil.requireInt(next.getData(), "turn_id"); }
            }
            Message ended = shooter.waitType(MessageType.MATCH_END); target.waitType(MessageType.MATCH_END);
            check(JsonUtil.requireInt(ended.getData(), "winner_id") == shooter.userId, "shooter wins the replay match");

            // history flags the row as replayable
            Message history = IntegrationTests.ok(a, new Message(MessageType.GET_MATCH_HISTORY_REQ));
            List<Object> rows = JsonUtil.getList(history.getData(), "matches");
            check(rows != null && rows.size() == 1, "one finished match in the history");
            @SuppressWarnings("unchecked") Map<String, Object> row = (Map<String, Object>) rows.get(0);
            check(JsonUtil.getBoolean(row, "replay", false), "history marks the match as replayable");
            check(matchId.equals(row.get("match_id")), "history points at the finished match");

            // the winner gets its own fleet first and the enemy fleet second, with the whole log
            Message detail = IntegrationTests.ok(shooter, new Message(MessageType.GET_MATCH_DETAIL_REQ).put("match_id", matchId));
            check(JsonUtil.getBoolean(detail.getData(), "replay_available", false), "replay is available");
            List<Object> mine = JsonUtil.getList(detail.getData(), "my_ships"), theirs = JsonUtil.getList(detail.getData(), "opponent_ships");
            check(cells(mine) == 17 && cells(theirs) == 17, "both sealed fleets carry all 17 ship cells");
            check(mine.size() == 5 && theirs.size() == 5, "both sealed fleets carry five ships");
            check(!rows.isEmpty() && sameShape(mine, shooter == a), "own fleet comes back in the requester's own orientation");
            List<Object> log = JsonUtil.getList(detail.getData(), "shot_log");
            check(log != null && log.size() == shots, "shot log holds every accepted shot, got " + (log == null ? 0 : log.size()) + " of " + shots);
            int mineShots = 0, sunkEntries = 0, turns = 0, previousTurn = 0;
            Set<String> seen = new HashSet<>();
            for (Object item : log) {
                @SuppressWarnings("unchecked") Map<String, Object> shot = (Map<String, Object>) item;
                int x = JsonUtil.requireInt(shot, "x"), y = JsonUtil.requireInt(shot, "y"), turnId = JsonUtil.requireInt(shot, "turn_id");
                check(x >= 0 && x < 10 && y >= 0 && y < 10, "shot coordinates stay on the board");
                String result = JsonUtil.getString(shot, "result", "");
                check("HIT".equals(result) || "MISS".equals(result) || "SUNK".equals(result), "shot result is a legal outcome");
                if (JsonUtil.requireInt(shot, "shooter_id") == shooter.userId) mineShots++;
                if ("SUNK".equals(result)) { sunkEntries++; check(JsonUtil.getList(shot, "sunk_ship_coords") != null, "sunk shot carries the sunk ship cells"); }
                check(turnId > previousTurn, "shot log keeps the firing order"); previousTurn = turnId; turns++;
                check(seen.add(x + ":" + y), "no cell is fired twice in one match");
            }
            check(turns == shots, "shot log covers the whole match");
            check(sunkEntries == 5, "shot log records all five sinkings");
            check(mineShots == JsonUtil.requireInt(detail.getData(), "my_shots"), "shot log agrees with the shooter's shot count");
            check(shots - mineShots == JsonUtil.requireInt(detail.getData(), "opponent_shots"), "the other side is counted as the opponent");

            // the same match seen by the loser is mirrored, not copied
            Message mirror = IntegrationTests.ok(target, new Message(MessageType.GET_MATCH_DETAIL_REQ).put("match_id", matchId));
            check(!JsonUtil.getBoolean(mirror.getData(), "won", true), "the loser sees the loss");
            check(sameShape(JsonUtil.getList(mirror.getData(), "my_ships"), target == a), "the other player sees its own fleet first");

            // fleets are released only to the two players of that match
            IntegrationTests.error(c, new Message(MessageType.GET_MATCH_DETAIL_REQ).put("match_id", matchId), "FORBIDDEN");
            IntegrationTests.error(a, new Message(MessageType.GET_MATCH_DETAIL_REQ).put("match_id", "M_nope"), "MATCH_NOT_FOUND");
            IntegrationTests.error(a, new Message(MessageType.GET_MATCH_DETAIL_REQ).put("match_id", ""), "INVALID_MESSAGE");
        } finally { server.stop(); thread.join(5000); }

        StorageManager restored = new StorageManager(dir);
        MatchRecord record = restored.findMatch(matchId);
        check(record != null && record.hasReplay(), "sealed fleets survive a server restart");
        check(record.getShotLog().size() == shots, "shot log survives a server restart");
        check(record.getPlayer1Ships().size() == 5 && record.getPlayer2Ships().size() == 5, "both fleets reload from MySQL");
        legacy();
        System.out.println("PASS replay: sealed fleets + shot log stored, mirrored per player, restart-safe, participants only, legacy rows still readable");
    }

    /** A record written before the feature: it must load, list, and report that it cannot be replayed. */
    private static void legacy() throws Exception {
        Path dir = Files.createTempDirectory("battleship-replay-legacy-");
        String hash = PasswordHasher.hash("secret");
        List<Object> users = new ArrayList<>();
        users.add(legacyUser(41, "legacy_a", hash)); users.add(legacyUser(42, "legacy_b", hash));
        Map<String, Object> match = new LinkedHashMap<>();
        match.put("matchId", "M_legacy"); match.put("player1Id", 41); match.put("player2Id", 42);
        match.put("player1Name", "legacy_a"); match.put("player2Name", "legacy_b");
        match.put("winnerId", 41); match.put("winnerName", "legacy_a"); match.put("loserId", 42); match.put("loserName", "legacy_b");
        match.put("player1Shots", 21); match.put("player2Shots", 18); match.put("reason", "ALL_SHIPS_DESTROYED");
        match.put("durationSeconds", 120); match.put("startedAt", 1000L); match.put("endedAt", 121000L); match.put("timestamp", 121000L);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("version", 1); snapshot.put("users", users); snapshot.put("matches", Arrays.asList(match));
        Files.write(dir.resolve("snapshot.json"), JsonUtil.toJson(snapshot).getBytes(StandardCharsets.UTF_8));
        GameServer server = new GameServer(0, dir);
        Thread thread = PenaltyTests.start(server);
        try (SocketPeer peer = new SocketPeer(server.getPort(), true)) {
            peer.login("legacy_a");
            Message history = IntegrationTests.ok(peer, new Message(MessageType.GET_MATCH_HISTORY_REQ));
            List<Object> rows = JsonUtil.getList(history.getData(), "matches");
            check(rows != null && rows.size() == 1, "legacy record stays in the history");
            @SuppressWarnings("unchecked") Map<String, Object> row = (Map<String, Object>) rows.get(0);
            check(!JsonUtil.getBoolean(row, "replay", true), "legacy row is flagged as not replayable");
            IntegrationTests.error(peer, new Message(MessageType.GET_MATCH_DETAIL_REQ).put("match_id", "M_legacy"), "REPLAY_UNAVAILABLE");
        } finally { server.stop(); thread.join(5000); }
    }

    private static Map<String, Object> legacyUser(int id, String name, String hash) {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("userId", id); user.put("username", name); user.put("passwordHash", hash);
        user.put("totalPoints", 3); user.put("totalWins", 1); user.put("totalGames", 1);
        user.put("matchmakingBlockedUntil", 0); user.put("deleted", false);
        return user;
    }

    private static int cells(List<Object> fleet) {
        int total = 0;
        if (fleet == null) return 0;
        for (Object item : fleet) if (item instanceof Map) {
            @SuppressWarnings("unchecked") Map<String, Object> ship = (Map<String, Object>) item;
            List<Object> coords = JsonUtil.getList(ship, "coordinates");
            total += coords == null ? 0 : coords.size();
        }
        return total;
    }

    /** True when the first ship runs along the bottom row, i.e. the horizontal layout. */
    private static boolean sameShape(List<Object> fleet, boolean horizontal) {
        if (fleet == null || fleet.isEmpty()) return false;
        @SuppressWarnings("unchecked") Map<String, Object> first = (Map<String, Object>) fleet.get(0);
        List<Object> coords = JsonUtil.getList(first, "coordinates");
        if (coords == null || coords.size() < 2) return false;
        @SuppressWarnings("unchecked") Map<String, Object> one = (Map<String, Object>) coords.get(0);
        @SuppressWarnings("unchecked") Map<String, Object> two = (Map<String, Object>) coords.get(1);
        boolean runsAlongX = JsonUtil.requireInt(two, "x") != JsonUtil.requireInt(one, "x");
        return runsAlongX == horizontal;
    }
}
