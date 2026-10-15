package tests;

import common.config.GameConfig;
import common.model.*;
import common.protocol.*;
import server.manager.*;
import server.network.GameServer;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Real TCP enforcement plus a controllable clock; no minute-long test sleeps. */
public class PenaltyTests {
    static void check(boolean value,String text){TestRunner.check(value,text);}
    static Thread start(GameServer server)throws Exception {
        Thread thread=new Thread(server::start,"penalty-test-server");thread.start();
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(server.getPort()<=0 && System.nanoTime()<until)Thread.sleep(5);
        check(server.getPort()>0,"penalty server listening");return thread;
    }
    static Message login(SocketPeer peer,String name)throws Exception {
        Message response=IntegrationTests.ok(peer,new Message(MessageType.LOGIN_REQ).put("username",name).put("password","secret"));
        peer.userId=JsonUtil.requireInt(response.getData(),"user_id");return response;
    }
    static Message blocked(SocketPeer peer,Message request,int penalizedUser)throws Exception {
        Message response=peer.request(request);
        check("MATCHMAKING_COOLDOWN".equals(response.getErrorCode()),"server rejects bypass: "+request.getType()+" => "+response);
        check(JsonUtil.requireInt(response.getData(),"user_id")==penalizedUser,"cooldown identifies the correct user");
        return response;
    }
    static User seed(StorageManager storage,String name,int points){User user=storage.registerUser(name,"secret");user.setTotalPoints(points);return user;}
    public static void run()throws Exception {
        check(GameConfig.FORFEIT_PENALTY_POINTS==3 && GameConfig.MATCHMAKING_PENALTY_MS==60000,"agreed penalty is exactly 3 points / 60 seconds");
        Path directory=DomainTests.empty("battleship-penalty-");AtomicLong clock=new AtomicLong(System.currentTimeMillis());
        StorageManager storage=new StorageManager(directory,clock::get);
        User quitter=seed(storage,"quitter",6),winner=seed(storage,"winner",6);seed(storage,"low",1);storage.saveUsers();
        GameServer server=new GameServer(0,storage);Thread thread=start(server);
        long blockedUntil;
        try(SocketPeer a=new SocketPeer(server.getPort(),true);SocketPeer b=new SocketPeer(server.getPort(),true)) {
            a.login("quitter");b.login("winner");
            String preparing=IntegrationTests.queuePair(a,b);
            IntegrationTests.ok(a,IntegrationTests.match(MessageType.LEAVE_ROOM_REQ,preparing));a.waitType(MessageType.ROOM_USER_LEFT);b.waitType(MessageType.ROOM_USER_LEFT);
            check(quitter.getTotalPoints()==6 && quitter.getMatchmakingBlockedUntil()==0 && storage.getMatchRecords().isEmpty(),"leaving placement has no penalty or result");
            a.clear();b.clear();String match=IntegrationTests.queuePair(a,b);IntegrationTests.start(a,b,match);
            Message surrender=IntegrationTests.match(MessageType.SURRENDER_REQ,match).requestId("penalty-once");
            IntegrationTests.ok(a,surrender);Message result=a.waitType(MessageType.MATCH_END);b.waitType(MessageType.MATCH_END);
            a.waitType(MessageType.ROOM_USER_LEFT);b.waitType(MessageType.ROOM_USER_LEFT);
            check(quitter.getTotalPoints()==3 && winner.getTotalPoints()==9,"surrender deducts 3 and awards opponent 3");
            check(quitter.getTotalGames()==1 && winner.getTotalGames()==1,"forfeit counts one match");
            Map<String,Object> own=JsonUtil.getMap(JsonUtil.getMap(result.getData(),"scores"),String.valueOf(a.userId));
            check(JsonUtil.requireInt(own,"points_awarded")==-3,"negative score delta is sent to client");
            MatchRecord record=storage.getMatchRecords().get(0);blockedUntil=record.getMatchmakingBlockedUntil();
            check(blockedUntil-record.getEndedAt()==60000 && quitter.getMatchmakingBlockedUntil()==blockedUntil,"cooldown starts at match end and lasts exactly one minute");
            check((record.getPlayer1Id()==a.userId?record.getPlayer1Points():record.getPlayer2Points())==-3,"history records the actual deduction");
            clock.set(blockedUntil-59000);
            IntegrationTests.ok(a,surrender);
            IntegrationTests.error(a,IntegrationTests.match(MessageType.SURRENDER_REQ,match),"STALE_MATCH");
            check(storage.getMatchRecords().size()==1 && quitter.getTotalPoints()==3 && quitter.getMatchmakingBlockedUntil()==blockedUntil,"replayed and duplicate leaves never reapply penalty");
            blocked(a,new Message(MessageType.QUEUE_JOIN_REQ),a.userId);
            blocked(a,new Message(MessageType.CREATE_ROOM_REQ),a.userId);
            blocked(a,new Message(MessageType.INVITE_REQ).put("target_user_id",b.userId),a.userId);
            blocked(b,new Message(MessageType.INVITE_REQ).put("target_user_id",a.userId),a.userId);
            String room=(String)IntegrationTests.ok(b,new Message(MessageType.CREATE_ROOM_REQ)).getData().get("room_id");
            blocked(a,new Message(MessageType.JOIN_ROOM_REQ).put("room_code",room),a.userId);
            IntegrationTests.ok(b,new Message(MessageType.LEAVE_ROOM_REQ).put("room_id",room));
            IntegrationTests.ok(a,new Message(MessageType.LOGOUT_REQ));
            Message relogin=login(a,"quitter");
            check(JsonUtil.getLong(relogin.getData(),"matchmaking_blocked_until",0)==blockedUntil && "COOLDOWN".equals(relogin.getData().get("phase")),"relogin restores cooldown and status");
            blocked(a,new Message(MessageType.QUEUE_JOIN_REQ),a.userId);
        } finally {server.stop();thread.join(5000);}

        StorageManager restarted=new StorageManager(directory,clock::get);
        check(restarted.findByUsername("quitter").getTotalPoints()==3 && restarted.findByUsername("quitter").getMatchmakingBlockedUntil()==blockedUntil,"points and cooldown survive server restart");
        GameServer next=new GameServer(0,restarted);Thread nextThread=start(next);
        try(SocketPeer a=new SocketPeer(next.getPort(),true);SocketPeer b=new SocketPeer(next.getPort(),true);SocketPeer low=new SocketPeer(next.getPort(),true)) {
            Message relogin=login(a,"quitter");b.login("winner");low.login("low");
            check(JsonUtil.getLong(relogin.getData(),"matchmaking_blocked_until",0)==blockedUntil,"login after restart carries original deadline");
            clock.set(blockedUntil-1);blocked(a,new Message(MessageType.QUEUE_JOIN_REQ),a.userId);
            clock.set(blockedUntil);String match=IntegrationTests.queuePair(a,b);
            IntegrationTests.ok(a,IntegrationTests.match(MessageType.LEAVE_ROOM_REQ,match));a.waitType(MessageType.ROOM_USER_LEFT);b.waitType(MessageType.ROOM_USER_LEFT);
            check(restarted.findByUsername("quitter").getTotalPoints()==3,"expiry restores access without refund or extra deduction");
            a.clear();b.clear();clock.set(System.currentTimeMillis());
            String lowMatch=IntegrationTests.queuePair(low,b);IntegrationTests.start(low,b,lowMatch);
            low.close();Message result=b.waitType(MessageType.MATCH_END);b.waitType(MessageType.ROOM_USER_LEFT);
            check("DISCONNECTED".equals(result.getData().get("reason")),"socket loss counts as a forfeit");
            User poor=restarted.findByUsername("low");check(poor.getTotalPoints()==0,"one point is clamped to zero, never negative");
            Map<String,Object> own=JsonUtil.getMap(JsonUtil.getMap(result.getData(),"scores"),String.valueOf(poor.getUserId()));
            check(JsonUtil.requireInt(own,"points_awarded")==-1,"low-score account receives truthful delta");
            b.waitFor(m->m.getType()==MessageType.USER_STATUS_BROADCAST && "low".equals(m.getData().get("username")) && "OFFLINE".equals(m.getData().get("status")),5000);
            try(SocketPeer back=new SocketPeer(next.getPort(),true)) {
                login(back,"low");blocked(back,new Message(MessageType.CREATE_ROOM_REQ),poor.getUserId());
                clock.set(poor.getMatchmakingBlockedUntil());
                back.waitFor(m->m.getType()==MessageType.USER_STATUS_BROADCAST && JsonUtil.getInt(m.getData(),"user_id",-1)==poor.getUserId() && "IDLE".equals(m.getData().get("phase")),3000);
                b.clear();back.clear();String normal=IntegrationTests.queuePair(back,b);Message start=IntegrationTests.start(back,b,normal);RaceTests.sink(back,b,normal,start);
                back.waitType(MessageType.MATCH_END);b.waitType(MessageType.MATCH_END);
                MatchRecord normalRecord=restarted.getMatchRecords().get(restarted.getMatchRecords().size()-1);
                check(normalRecord.getMatchmakingBlockedUntil()==0 && (normalRecord.getWinnerId()==normalRecord.getPlayer1Id()?normalRecord.getPlayer2Points():normalRecord.getPlayer1Points())==0,"normal defeat has no points or matchmaking penalty");
                IntegrationTests.ok(back,IntegrationTests.match(MessageType.LEAVE_ROOM_REQ,normal));back.waitType(MessageType.ROOM_USER_LEFT);b.waitType(MessageType.ROOM_USER_LEFT);
                back.clear();b.clear();String interrupted=IntegrationTests.queuePair(back,b);IntegrationTests.start(back,b,interrupted);
                int before=restarted.getMatchRecords().size();next.stop();
                check(restarted.getMatchRecords().size()==before,"server shutdown does not punish either player");
            }
        } finally {next.stop();nextThread.join(5000);}
        pendingWrite();
        System.out.println("PASS penalties: -3/floor zero, 60s boundary, queue/room/invite guards, replay, logout/login/restart, disconnect, expiry broadcast, normal defeat, shutdown, storage retry");
    }
    private static class FaultStorage extends StorageManager {
        boolean fail;
        FaultStorage(Path path,AtomicLong clock){super(path,clock::get);}
        protected void writeSnapshot(List<Map<String,Object>> users,List<Object> matches)throws IOException {
            if(fail)throw new IOException("penalty write fault");super.writeSnapshot(users,matches);
        }
    }
    private static void pendingWrite()throws Exception {
        AtomicLong clock=new AtomicLong(System.currentTimeMillis());Path path=DomainTests.empty("battleship-penalty-fault-");
        FaultStorage storage=new FaultStorage(path,clock);User loser=seed(storage,"loser",2),winner=seed(storage,"winner",0);storage.saveUsers();
        MatchRecord record=new MatchRecord();record.setMatchId("pending-penalty");record.setPlayer1Id(loser.getUserId());record.setPlayer2Id(winner.getUserId());record.setWinnerId(winner.getUserId());record.setLoserId(loser.getUserId());record.setEndedAt(clock.get());record.setReason("SURRENDER");
        storage.fail=true;check(!storage.commitResult(record),"disk failure returns pending");
        check(loser.getTotalPoints()==2 && loser.getMatchmakingBlockedUntil()==0,"failed transaction leaves committed user unchanged");
        UserManager users=new UserManager(storage);DomainTests.Capture handler=new DomainTests.Capture(loser,users,null);
        DomainTests.expect("MATCHMAKING_COOLDOWN",()->users.requireCanMatch(handler));
        clock.addAndGet(60000);DomainTests.expect("MATCHMAKING_COOLDOWN",()->users.requireCanMatch(handler));
        storage.fail=false;check(storage.retryPending().size()==1,"pending penalty commits once on retry");
        check(loser.getTotalPoints()==0 && winner.getTotalPoints()==3 && loser.getTotalGames()==1,"retry applies floor deduction and win atomically");
        storage.commitResult(record);check(loser.getTotalGames()==1 && loser.getTotalPoints()==0,"duplicate commit is inert");users.requireCanMatch(handler);
        StorageManager reload=new StorageManager(path,clock::get);check(reload.findById(loser.getUserId()).getMatchmakingBlockedUntil()==record.getMatchmakingBlockedUntil(),"retry persists original penalty deadline");
    }
}
