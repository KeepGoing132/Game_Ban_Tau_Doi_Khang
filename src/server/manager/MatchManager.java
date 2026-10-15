package server.manager;

import common.model.*;
import common.protocol.*;
import server.model.MatchSession;
import server.network.ClientHandler;
import java.util.*;
import java.util.concurrent.*;

public class MatchManager {
    private final UserManager users;
    private final StorageManager storage;
    private final Object lock;
    private final Map<String,MatchSession> sessions=new ConcurrentHashMap<>();
    private final Map<ClientHandler,MatchSession> membership=new ConcurrentHashMap<>();
    private final ScheduledThreadPoolExecutor scheduler=new ScheduledThreadPoolExecutor(2);
    private LobbyManager lobby;
    public MatchManager(UserManager users,StorageManager storage){this.users=users;this.storage=storage;lock=users.getRegistryLock();scheduler.setRemoveOnCancelPolicy(true);
        scheduler.scheduleWithFixedDelay(()->{for(MatchRecord r:storage.retryPending()){MatchSession s=sessions.get(r.getMatchId());if(s!=null)s.resultSaved();for(User u:storage.getAllUsers())if(u.getUserId()==r.getPlayer1Id() || u.getUserId()==r.getPlayer2Id())users.broadcastUserStatus(u);}},1,1,TimeUnit.SECONDS);
        scheduler.scheduleWithFixedDelay(users::refreshExpiredPenalties,1,1,TimeUnit.SECONDS);
    }
    public void setLobby(LobbyManager lobby){this.lobby=lobby;}
    public ScheduledExecutorService scheduler(){return scheduler;}
    public MatchSession createSession(ClientHandler p1,ClientHandler p2,String roomId){synchronized(lock){
        users.requireCanMatch(p1);users.requireCanMatch(p2);
        if(p1==p2 || p1.getUser()==null || p2.getUser()==null || membership.containsKey(p1) || membership.containsKey(p2))throw new DomainException("INVALID_STATE");
        MatchSession s=new MatchSession("M_"+UUID.randomUUID(),roomId,p1,p2,users,storage,scheduler,()->{});
        s.setFinishedCallback(()->finished(s));sessions.put(s.getMatchId(),s);membership.put(p1,s);membership.put(p2,s);users.updatePhase(p1,"PLACING");users.updatePhase(p2,"PLACING");return s;
    }}
    public MatchSession getSession(ClientHandler client){return membership.get(client);}
    private MatchSession require(ClientHandler client,Message request){
        MatchSession s=getSession(client);if(s==null || !s.getMatchId().equals(request.getData().get("match_id")))throw new DomainException("STALE_MATCH","Trận không tồn tại hoặc yêu cầu thuộc ván cũ");return s;
    }
    public void handlePlaceShips(ClientHandler client,Message request){
        MatchSession s=require(client,request);List<Object> raw=JsonUtil.getList(request.getData(),"ships");if(raw==null || raw.size()!=5)throw new DomainException("INVALID_FLEET");
        List<Ship> ships=new ArrayList<>();Set<String> ids=new HashSet<>();Set<Coordinate> occupied=new HashSet<>();
        for(Object item:raw){
            if(!(item instanceof Map))throw new DomainException("INVALID_FLEET");
            @SuppressWarnings("unchecked") Map<String,Object> map=(Map<String,Object>)item;
            if(!(map.get("id") instanceof String))throw new DomainException("INVALID_FLEET");String id=(String)map.get("id");
            if(id.trim().isEmpty() || id.length()>64)throw new DomainException("INVALID_FLEET");if(!ids.add(id))throw new DomainException("DUPLICATE_SHIP_ID");
            int size=JsonUtil.requireInt(map,"size");List<Object> coordsRaw=JsonUtil.getList(map,"coordinates");if(coordsRaw==null || coordsRaw.size()!=size || size<2 || size>5)throw new DomainException("INVALID_FLEET");
            List<Coordinate> coords=new ArrayList<>();
            for(Object c:coordsRaw){if(!(c instanceof Map))throw new DomainException("INVALID_FLEET");@SuppressWarnings("unchecked") Map<String,Object> coord=(Map<String,Object>)c;
                int x=JsonUtil.requireInt(coord,"x"),y=JsonUtil.requireInt(coord,"y");if(x<0 || x>=10 || y<0 || y>=10)throw new DomainException("OUT_OF_BOUNDS","Tàu " + id + " vượt biên tại (" + x + "," + y + ")");Coordinate point=new Coordinate(x,y);if(!occupied.add(point))throw new DomainException("OVERLAP","Tàu " + id + " trùng ô (" + x + "," + y + ")");coords.add(point);
            }
            ships.add(new Ship(id,size,coords));
        }
        s.place(client,ships);
    }
    public void handleReady(ClientHandler client,Message request){
        Object ready=request.getData().get("ready");if(!(ready instanceof Boolean))throw new IllegalArgumentException();require(client,request).ready(client,(Boolean)ready);
    }
    public void handleFire(ClientHandler client,Message request){MatchSession s=require(client,request);s.fire(client,request,JsonUtil.requireInt(request.getData(),"turn_id"),new Coordinate(JsonUtil.requireInt(request.getData(),"x"),JsonUtil.requireInt(request.getData(),"y")));}
    private void finished(MatchSession s){
        synchronized(lock) {
            if(getSession(s.getPlayer1())!=s || !s.isFinished())return;
            s.setPostGameTimer(scheduler.schedule(()->{synchronized(lock){if(getSession(s.getPlayer1())==s && s.isFinished())lobby.release(s,"Hết thời gian chờ chơi lại");}},Math.max(0,s.getRematchDeadline()-System.currentTimeMillis()),TimeUnit.MILLISECONDS));
        }
    }
    public void handleRematch(ClientHandler client,Message request,boolean accept){synchronized(lock){
        if(accept)users.requireCanMatch(client);
        MatchSession old=require(client,request);
        if(!old.isFinished())throw new DomainException("INVALID_STATE");
        if(!old.isResultSaved())throw new DomainException("STORAGE_ERROR","Kết quả đang chờ lưu; chưa thể chơi lại");
        boolean already=old.hasAgreed(client);
        boolean both=accept && old.agreeRematch(client);
        client.reply(new Message(ClientHandler.responseType(request.getType())).put("match_id",old.getMatchId()).put("accepted",accept));
        if(!accept){lobby.release(old,"Đối thủ từ chối chơi lại");return;}
        if(already)return;
        if(!both){
            old.getOpponent(client).sendMessage(new Message(MessageType.REMATCH_NOTIFY).put("match_id",old.getMatchId()).put("requester_id",client.getUser().getUserId()).put("expires_at_ms",old.getRematchDeadline()));return;
        }
        ClientHandler p1=old.getPlayer1(),p2=old.getPlayer2();
        if(!p1.isConnected() || !p2.isConnected() || p1.getUser()==null || p2.getUser()==null){lobby.release(old,"Đối thủ đã ngắt kết nối");return;}
        cleanupSession(old);MatchSession next=createSession(p1,p2,old.getRoomId());
        Message event=new Message(MessageType.REMATCH_START).put("previous_match_id",old.getMatchId()).put("match_id",next.getMatchId()).put("room_id",next.getRoomId());p1.sendMessage(event);p2.sendMessage(event);
    }}
    public void cleanupSession(MatchSession s){synchronized(lock){if(s.getPlayer1() instanceof server.bot.BotClient)((server.bot.BotClient)s.getPlayer1()).cancelShot();if(s.getPlayer2() instanceof server.bot.BotClient)((server.bot.BotClient)s.getPlayer2()).cancelShot();sessions.remove(s.getMatchId(),s);membership.remove(s.getPlayer1(),s);membership.remove(s.getPlayer2(),s);s.cancelTimers();}}
    public void shutdown(){synchronized(lock){for(MatchSession s:sessions.values())s.cancel();sessions.clear();membership.clear();}scheduler.shutdownNow();}
}
