package server.model;

import common.config.GameConfig;
import common.model.*;
import common.protocol.*;
import server.manager.*;
import server.network.ClientHandler;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.LongSupplier;

/** Authoritative state machine. The registry lock is never acquired from inside this lock. */
public class MatchSession {
    public enum State {
        PLACING,RUNNING,FINISHED,CANCELLED
    }
    private final String matchId,roomId;
    private final ClientHandler p1,p2;
    private final User u1,u2;
    private Board b1=new Board(),b2=new Board();
    private boolean ready1,ready2,rematch1,rematch2,resultSaved;
    private State state=State.PLACING;
    private int currentPlayer=-1,turnId,shots1,shots2;

    /** Accepted shots in firing order; stored with the result so the two players can replay it. */
    private final List<Map<String,Object>> shotLog=new ArrayList<>();
    private long startedAt,endedAt,deadlineMs,deadlineNanos,rematchDeadline;
    private final UserManager users;
    private final StorageManager storage;
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> timer;
    private ScheduledFuture<?> postGameTimer;
    private ScheduledFuture<?> placementTimer;
    private final long placementDeadlineMs, placementDeadlineNanos;

    private Runnable placementExpiredCallback = () -> {
    };
    private Runnable finishedCallback;
    private final LongSupplier epoch,nano;
    private final Random random;

    public MatchSession(String id,String room,ClientHandler p1,ClientHandler p2,UserManager users,StorageManager storage,ScheduledExecutorService scheduler,Runnable finished) {
        this(id,room,p1,p2,users,storage,scheduler,finished,System::currentTimeMillis,System::nanoTime,new Random());
    }

    public MatchSession(String id,String room,ClientHandler p1,ClientHandler p2,UserManager users,StorageManager storage,ScheduledExecutorService scheduler,Runnable finished,LongSupplier epoch,LongSupplier nano,Random random) {
        this.matchId=id;
        this.roomId=room;
        this.p1=p1;
        this.p2=p2;
        this.u1=p1.getUser();
        this.u2=p2.getUser();
        this.users=users;
        this.storage=storage;
        this.scheduler=scheduler;
        this.finishedCallback=finished;
        this.epoch=epoch;
        this.nano=nano;
        this.random=random;
        placementDeadlineMs = epoch.getAsLong() + GameConfig.PLACEMENT_TIMEOUT_MS;
        placementDeadlineNanos = nano.getAsLong() + TimeUnit.MILLISECONDS.toNanos(GameConfig.PLACEMENT_TIMEOUT_MS);
    }

    /** Called after registration, so an expiry callback can safely release this session. */
    public synchronized void beginPlacement(Runnable onExpired) {
        placementExpiredCallback = onExpired;
        if (state == State.PLACING && placementTimer == null)
            placementTimer = scheduler.schedule(this::placementTimeout, Math.max(0, placementDeadlineNanos - nano.getAsLong()), TimeUnit.NANOSECONDS);
    }

    public Message placementInfo(Message message) {
        return message.put("placement_deadline_ms", placementDeadlineMs).put("placement_limit_ms", GameConfig.PLACEMENT_TIMEOUT_MS);
    }

    public synchronized void placementTimeout() {
        if (state != State.PLACING) return;
        long remaining = placementDeadlineNanos - nano.getAsLong();
        if (remaining > 0) {
            placementTimer = scheduler.schedule(this::placementTimeout, remaining, TimeUnit.NANOSECONDS);
            return;
        }
        state = State.CANCELLED;
        cancelTimers();
        // Never acquire the registry lock while holding the match monitor.
        scheduler.execute(placementExpiredCallback);
    }

    private void requirePlacementTime() {
        if (state == State.PLACING && nano.getAsLong() >= placementDeadlineNanos) {
            placementTimeout();
            throw new DomainException("PLACEMENT_EXPIRED", "Hết thời gian bố trí tàu. Trận đã được hủy, không tính điểm.");
        }
    }

    public void setFinishedCallback(Runnable callback) {
        finishedCallback=callback;
    }

    public String getMatchId() {
        return matchId;
    }

    public String getRoomId() {
        return roomId;
    }

    public ClientHandler getPlayer1() {
        return p1;
    }

    public ClientHandler getPlayer2() {
        return p2;
    }

    public ClientHandler getOpponent(ClientHandler c) {
        if(c==p1)return p2;
        if(c==p2)return p1;
        throw new DomainException("INVALID_STATE");
    }

    private User user(ClientHandler c) {
        if(c==p1)return u1;
        if(c==p2)return u2;
        throw new DomainException("INVALID_STATE");
    }

    private Board board(ClientHandler c) {
        user(c);
        return c==p1?b1:b2;
    }

    private Message event(MessageType type) {
        return new Message(type).put("match_id",matchId).put("room_id",roomId);
    }

    private void both(Message event) {
        p1.sendMessage(event);
        p2.sendMessage(event);
    }

    public synchronized void place(ClientHandler c,List<Ship> ships) {
        requirePlacementTime();
        user(c);
        if(state!=State.PLACING || (c==p1?ready1:ready2))throw new DomainException("INVALID_STATE","Hủy Ready trước khi sửa tàu");
        if(ships==null || ships.size()!=5)throw new DomainException("INVALID_FLEET");
        Board candidate=new Board();
        List<Ship> copy=new ArrayList<>();
        for(Ship s:ships) {
            if(s==null || s.getCoordinates()==null)throw new DomainException("INVALID_FLEET");
            List<Coordinate> points=new ArrayList<>();
            for(Coordinate point:s.getCoordinates()) {
                if(point==null)throw new DomainException("INVALID_FLEET");
                points.add(new Coordinate(point.getX(),point.getY()));
            }
            copy.add(new Ship(s.getId(),s.getSize(),points));
        }
        candidate.setShips(copy);
        if(!candidate.validateAllShips())throw new DomainException("INVALID_FLEET","Bộ tàu phải thẳng, liên tục và đủ [5,4,3,3,2]");
        if(c==p1)b1=candidate;
        else b2=candidate;
        c.reply(event(MessageType.PLACE_SHIPS_RESP));
    }

    public synchronized void ready(ClientHandler c,boolean value) {
        requirePlacementTime();
        user(c);
        if(state!=State.PLACING)throw new DomainException("INVALID_STATE");
        if(value && !board(c).validateAllShips())throw new DomainException("NOT_READY","Chưa có bố trí tàu hợp lệ");
        if(c==p1)ready1=value;
        else ready2=value;
        users.updatePhase(c,value?"READY_WAITING":"PLACING");
        c.reply(event(MessageType.PLAYER_READY_RESP).put("ready",value));
        both(event(MessageType.READY_STATE_NOTIFY).put("player1_id",u1.getUserId()).put("player2_id",u2.getUserId()).put("player1_ready",ready1).put("player2_ready",ready2));
        if(ready1 && ready2)start();
    }

    private void start() {
        if(state!=State.PLACING)return;
        state=State.RUNNING;
        startedAt=epoch.getAsLong();
        currentPlayer=random.nextBoolean()?u1.getUserId():u2.getUserId();
        if (placementTimer != null) {
            placementTimer.cancel(false);
            placementTimer = null;
        }
        users.updatePhase(p1,"IN_BATTLE");
        users.updatePhase(p2,"IN_BATTLE");
        newTurn();
        Message start=turnEvent(MessageType.MATCH_START).put("first_turn_player_id",currentPlayer).put("turn_time_limit_sec",GameConfig.TURN_TIMEOUT_SECONDS).put("player1",users.publicUser(u1)).put("player2",users.publicUser(u2));
        both(start);
    }

    private Message turnEvent(MessageType type) {
        return event(type).put("turn_id",turnId).put("current_turn_player_id",currentPlayer).put("deadline_ms",deadlineMs).put("time_limit_sec",GameConfig.TURN_TIMEOUT_SECONDS);
    }

    private void newTurn() {
        if(timer!=null)timer.cancel(false);
        turnId++;
        deadlineMs=epoch.getAsLong()+GameConfig.TURN_TIMEOUT_MS;
        deadlineNanos=nano.getAsLong()+TimeUnit.MILLISECONDS.toNanos(GameConfig.TURN_TIMEOUT_MS);
        final int expectedTurn=turnId;
        timer=scheduler.schedule(()->timeout(expectedTurn),GameConfig.TURN_TIMEOUT_MS,TimeUnit.MILLISECONDS);
    }

    public synchronized void timeout(int expectedTurn) {
        if(state!=State.RUNNING || turnId!=expectedTurn)return;
        long remaining=deadlineNanos-nano.getAsLong();
        if(remaining>0) {
            timer=scheduler.schedule(()->timeout(expectedTurn),remaining,TimeUnit.NANOSECONDS);
            return;
        }
        int expired=currentPlayer;
        both(event(MessageType.TIMEOUT_NOTIFY).put("turn_id",turnId).put("player_id",expired).put("deadline_ms",deadlineMs).put("action","LOSE_TURN"));
        currentPlayer=expired==u1.getUserId()?u2.getUserId():u1.getUserId();
        newTurn();
        both(turnEvent(MessageType.TURN_CHANGE));
    }

    public synchronized void fire(ClientHandler c,Message request,int requestedTurn,Coordinate point) {
        User shooter=user(c);
        if(state!=State.RUNNING)throw new DomainException("INVALID_STATE");
        if(requestedTurn!=turnId)throw new DomainException("STALE_TURN");
        if(nano.getAsLong()>=deadlineNanos) {
            timeout(turnId);
            throw new DomainException("TURN_EXPIRED");
        }
        if(shooter.getUserId()!=currentPlayer)throw new DomainException("NOT_YOUR_TURN");
        if(point.getX()<0 || point.getX()>=10 || point.getY()<0 || point.getY()>=10)throw new DomainException("OUT_OF_BOUNDS");
        Board target=board(getOpponent(c));
        if(target.hasBeenShot(point))throw new DomainException("ALREADY_SHOT");
        int processedTurn=turnId;
        if(c==p1)shots1++;
        else shots2++;
        ShotResult result=target.receiveShot(point);
        boolean over=target.areAllShipsSunk();
        int next=over?-1:(result==ShotResult.MISS?user(getOpponent(c)).getUserId():shooter.getUserId());
        Message fire=event(MessageType.FIRE_RESULT).put("turn_id",processedTurn).put("shooter_id",shooter.getUserId()).put("target_id",user(getOpponent(c)).getUserId()).put("coordinate",coord(point)).put("result",result.name()).put("remaining_ships_target",target.getRemainingShipsCount()).put("next_turn_player_id",next);
        if(result==ShotResult.SUNK) {
            Ship ship=target.getShipAt(point);
            List<Map<String,Object>> coords=new ArrayList<>();
            for(Coordinate p:ship.getCoordinates())coords.add(coord(p));
            fire.put("sunk_ship_coords",coords).put("sunk_ship_name",ship.getId());
        }
        Map<String,Object> entry=new LinkedHashMap<>();
        entry.put("turn_id",processedTurn);
        entry.put("shooter_id",shooter.getUserId());
        entry.put("x",point.getX());
        entry.put("y",point.getY());
        entry.put("result",result.name());
        if(result==ShotResult.SUNK) {
            entry.put("sunk_ship_coords",copyCoords(fire.getData().get("sunk_ship_coords")));
            entry.put("sunk_ship_name",fire.getData().get("sunk_ship_name"));
        }
        shotLog.add(entry);
        getOpponent(c).sendMessage(fire);
        c.reply(fire);
        currentPlayer=next;
        if(over)finish(c,getOpponent(c),"ALL_SHIPS_DESTROYED");
        else {
            newTurn();
            both(turnEvent(MessageType.TURN_CHANGE));
        }
    }

    private static Map<String,Object> coord(Coordinate p) {
        Map<String,Object> m=new LinkedHashMap<>();
        m.put("x",p.getX());
        m.put("y",p.getY());
        return m;
    }

    /** Detaches the coordinate list from the event message, so the stored log owns its own maps. */
    private static List<Map<String,Object>> copyCoords(Object raw) {
        List<Map<String,Object>> copy=new ArrayList<>();
        if(raw instanceof List)for(Object item:(List<?>)raw)if(item instanceof Map) {
            @SuppressWarnings("unchecked") Map<String,Object> cell=(Map<String,Object>)item;
            Map<String,Object> point=new LinkedHashMap<>();
            point.put("x",cell.get("x"));
            point.put("y",cell.get("y"));
            copy.add(point);
        }
        return copy;
    }

    public synchronized void leave(ClientHandler c,String reason) {
        user(c);
        if(state==State.RUNNING)finish(getOpponent(c),c,reason);
        else if(state==State.PLACING)cancel();
    }

    private void finish(ClientHandler winner,ClientHandler loser,String reason) {
        if(state!=State.RUNNING)return;
        state=State.FINISHED;
        endedAt=epoch.getAsLong();
        rematchDeadline=endedAt+GameConfig.REMATCH_TIMEOUT_MS;
        cancelTimers();
        MatchRecord r=new MatchRecord();
        r.setMatchId(matchId);
        r.setPlayer1Id(u1.getUserId());
        r.setPlayer1Name(u1.getUsername());
        r.setPlayer2Id(u2.getUserId());
        r.setPlayer2Name(u2.getUsername());
        r.setWinnerId(user(winner).getUserId());
        r.setWinnerName(user(winner).getUsername());
        r.setLoserId(user(loser).getUserId());
        r.setLoserName(user(loser).getUsername());
        r.setStartedAt(startedAt);
        r.setEndedAt(endedAt);
        r.setTimestamp(endedAt);
        r.setDurationSeconds(Math.max(0,(endedAt-startedAt)/1000));
        r.setPlayer1Shots(shots1);
        r.setPlayer2Shots(shots2);
        r.setPlayer1Points(winner==p1?3:0);
        r.setPlayer2Points(winner==p2?3:0);
        r.setReason(reason);
        // Sealed fleets and the whole shot log are stored with the result, so both players can
        // reopen this match from their own history. Nothing here leaves the server during a match.
        r.setPlayer1Ships(fleetOf(b1));
        r.setPlayer2Ships(fleetOf(b2));
        r.setShotLog(new ArrayList<>(shotLog));
        boolean practice=u1.getUserId()<0 || u2.getUserId()<0;
        if(practice) {
            r.setPlayer1Points(0);
            r.setPlayer2Points(0);
            resultSaved=true;
        }
        else resultSaved=storage.commitResult(r);
        Map<String,Object> scores=new LinkedHashMap<>();
        scores.put(String.valueOf(u1.getUserId()),score(u1,r.getPlayer1Points()));
        scores.put(String.valueOf(u2.getUserId()),score(u2,r.getPlayer2Points()));
        users.updatePhase(p1,"POST_GAME");
        users.updatePhase(p2,"POST_GAME");
        Message end=event(MessageType.MATCH_END).put("winner_id",user(winner).getUserId()).put("loser_id",user(loser).getUserId()).put("reason",reason).put("duration_seconds",r.getDurationSeconds()).put("player1_shots",shots1).put("player2_shots",shots2).put("scores",scores).put("storage_saved",resultSaved).put("expires_at_ms",rematchDeadline);
        // The match is over, so each side now receives the other side's fleet for the report.
        p1.sendMessage(copyOf(end).put("opponent_ships",fleetOf(b2)).put("opponent_shots",shots2));
        p2.sendMessage(copyOf(end).put("opponent_ships",fleetOf(b1)).put("opponent_shots",shots1));
        if(!resultSaved)both(Message.error(MessageType.ERROR_NOTIFY,"Kết quả đang chờ lưu; điểm chưa được áp dụng").errorCode("STORAGE_ERROR").put("match_id",matchId));
        // Callback runs later on scheduler, outside this match lock.
        scheduler.execute(finishedCallback);
    }

    private static Message copyOf(Message message) {
        return Message.fromJson(message.toJson());
    }

    /** Fleet layout in the same shape as PLACE_SHIPS_REQ, released only once the match is over. */
    private static List<Map<String,Object>> fleetOf(Board board) {
        List<Map<String,Object>> fleet=new ArrayList<>();
        for(Ship ship:board.getShips()) {
            Map<String,Object> entry=new LinkedHashMap<>();
            entry.put("id",ship.getId());
            entry.put("size",ship.getSize());
            List<Map<String,Object>> coords=new ArrayList<>();
            for(Coordinate c:ship.getCoordinates())coords.add(coord(c));
            entry.put("coordinates",coords);
            fleet.add(entry);
        }
        return fleet;
    }

    private Map<String,Object> score(User u,int awarded) {
        Map<String,Object> m=users.publicUser(u);
        m.put("points_awarded",resultSaved?awarded:0);
        return m;
    }

    public synchronized boolean agreeRematch(ClientHandler c) {
        user(c);
        if(state!=State.FINISHED || epoch.getAsLong()>=rematchDeadline)throw new DomainException("INVALID_STATE","Hết thời gian chơi lại");
        if(c==p1)rematch1=true;
        else rematch2=true;
        return rematch1 && rematch2;
    }

    public synchronized boolean hasAgreed(ClientHandler c) {
        user(c);
        return c==p1?rematch1:rematch2;
    }

    public synchronized void setPostGameTimer(ScheduledFuture<?> future) {
        postGameTimer=future;
    }

    public synchronized void resultSaved() {
        resultSaved=true;
        MatchRecord record=storage.findMatch(matchId);
        Message saved=event(MessageType.RESULT_SAVED_NOTIFY);
        if (record!=null) {
            Map<String,Object> scores=new LinkedHashMap<>();
            scores.put(String.valueOf(u1.getUserId()),score(u1,record.getPlayer1Points()));
            scores.put(String.valueOf(u2.getUserId()),score(u2,record.getPlayer2Points()));
            saved.put("scores",scores);
        }
        both(saved);
    }

    public synchronized boolean isResultSaved() {
        return resultSaved;
    }

    public synchronized boolean isFinished() {
        return state==State.FINISHED;
    }

    public synchronized State getState() {
        return state;
    }

    public synchronized int getTurnId() {
        return turnId;
    }

    public synchronized int getCurrentPlayer() {
        return currentPlayer;
    }

    public synchronized long getDeadlineMs() {
        return deadlineMs;
    }

    public synchronized long getRematchDeadline() {
        return rematchDeadline;
    }

    public synchronized int getShotCount() {
        return shots1+shots2;
    }

    public synchronized void cancelTimers() {
        if(placementTimer!=null) {
            placementTimer.cancel(false);
            placementTimer=null;
        }
        if(timer!=null) {
            timer.cancel(false);
            timer=null;
        }
        if(postGameTimer!=null) {
            postGameTimer.cancel(false);
            postGameTimer=null;
        }
    }

    public synchronized void cancel() {
        if(state!=State.FINISHED)state=State.CANCELLED;
        cancelTimers();
    }
}
