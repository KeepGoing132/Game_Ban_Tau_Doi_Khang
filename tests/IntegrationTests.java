package tests;

import common.config.GameConfig;
import common.model.*;
import common.protocol.*;
import server.network.GameServer;
import server.manager.StorageManager;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class IntegrationTests {
    static void check(boolean c,String m){TestRunner.check(c,m);}
    static Message ok(SocketPeer c,Message m)throws Exception{Message r=c.request(m);check("SUCCESS".equals(r.getStatus()),"Expected success: "+r);return r;}
    static void error(SocketPeer c,Message m,String code)throws Exception{Message r=c.request(m);check(code.equals(r.getErrorCode()),"Expected "+code+", got "+r);}
    public static List<Map<String,Object>> fleet(){
        List<Map<String,Object>> list=new ArrayList<>();int[] sizes={5,4,3,3,2};
        for(int y=0;y<5;y++){Map<String,Object>s=new LinkedHashMap<>();s.put("id","S"+y);s.put("size",sizes[y]);List<Map<String,Object>> coords=new ArrayList<>();for(int x=0;x<sizes[y];x++){Map<String,Object>c=new LinkedHashMap<>();c.put("x",x);c.put("y",y);coords.add(c);}s.put("coordinates",coords);list.add(s);}return list;
    }
    static Message match(MessageType t,String id){return new Message(t).put("match_id",id);}
    static Message ready(String id,boolean value){return match(MessageType.PLAYER_READY_REQ,id).put("ready",value);}
    static String queuePair(SocketPeer a,SocketPeer b)throws Exception{
        ok(a,new Message(MessageType.QUEUE_JOIN_REQ));ok(b,new Message(MessageType.QUEUE_JOIN_REQ));Message ea=a.waitType(MessageType.MATCH_FOUND),eb=b.waitType(MessageType.MATCH_FOUND);String id=(String)ea.getData().get("match_id");check(id.equals(eb.getData().get("match_id")),"same pair id");return id;
    }
    static Message start(SocketPeer a,SocketPeer b,String id)throws Exception{
        ok(a,match(MessageType.PLACE_SHIPS_REQ,id).put("ships",fleet()));ok(b,match(MessageType.PLACE_SHIPS_REQ,id).put("ships",fleet()));
        ok(a,ready(id,true));ok(b,ready(id,true));Message sa=a.waitType(MessageType.MATCH_START),sb=b.waitType(MessageType.MATCH_START);check(sa.getData().equals(sb.getData()),"consistent start");return sa;
    }
    public static void run()throws Exception{
        Path dir=Files.createTempDirectory("battleship-tcp-");Files.write(dir.resolve("users.json"),"[]".getBytes(StandardCharsets.UTF_8));
        GameServer server=new GameServer(0,dir);Thread thread=new Thread(server::start,"test-server");thread.start();
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(server.getPort()<=0 && System.nanoTime()<end)Thread.sleep(5);int port=server.getPort();check(port>0,"server bound");
        try(SocketPeer a=new SocketPeer(port,true);SocketPeer b=new SocketPeer(port,true);SocketPeer c=new SocketPeer(port,true);SocketPeer d=new SocketPeer(port,true)){
            error(a,new Message(MessageType.CREATE_ROOM_REQ),"UNAUTHENTICATED");
            a.raw("{bad}\n{\"type\":\"NO_SUCH_TYPE\"}\n");check(a.waitType(MessageType.ERROR_NOTIFY).getErrorCode().equals("INVALID_MESSAGE"),"bad JSON");check(a.waitType(MessageType.ERROR_NOTIFY).getErrorCode().equals("UNKNOWN_TYPE"),"unknown type");
            String split=new Message(MessageType.HEARTBEAT_REQ).requestId("split").put("text","Tiếng Việt\n\"").toJson()+"\n";
            byte[] splitBytes=split.getBytes(StandardCharsets.UTF_8);
            synchronized(a){for(byte value:splitBytes)a.socket.getOutputStream().write(value);a.socket.getOutputStream().flush();}
            check(a.waitFor(m->"split".equals(m.getRequestId()),5000).getType()==MessageType.HEARTBEAT_RESP,"split unicode frame");
            a.raw(new Message(MessageType.HEARTBEAT_REQ).requestId("co1").toJson()+"\n"+new Message(MessageType.HEARTBEAT_REQ).requestId("co2").toJson()+"\n");a.waitFor(m->"co1".equals(m.getRequestId()),5000);a.waitFor(m->"co2".equals(m.getRequestId()),5000);
            a.login("alpha");b.login("beta");c.login("gamma");d.login("delta");
            for(SocketPeer peer:Arrays.asList(a,b,c,d))check(JsonUtil.getList(ok(peer,new Message(MessageType.GET_ONLINE_USERS_REQ)).getData(),"users").size()==4,"four consistent online views");
            error(a,new Message(MessageType.LOGIN_REQ).put("username","other").put("password","secret"),"INVALID_STATE");
            try(SocketPeer duplicate=new SocketPeer(port,true)){
                error(duplicate,new Message(MessageType.LOGIN_REQ).put("username","ALPHA").put("password",""),"AUTH_FAILED");
                error(duplicate,new Message(MessageType.LOGIN_REQ).put("username","ALPHA").put("password","wrong"),"AUTH_FAILED");
                error(duplicate,new Message(MessageType.LOGIN_REQ).put("username","ALPHA").put("password","secret"),"ALREADY_LOGGED_IN");
                ok(a,new Message(MessageType.GET_ONLINE_USERS_REQ));
            }
            Message create=new Message(MessageType.CREATE_ROOM_REQ).requestId("create-once");Message cr=ok(a,create);Message replay=ok(a,create);check(cr.getData().equals(replay.getData()),"request replay room");
            error(a,new Message(MessageType.QUEUE_JOIN_REQ).requestId("create-once"),"INVALID_MESSAGE");String room=(String)cr.getData().get("room_id");
            error(a,new Message(MessageType.JOIN_ROOM_REQ).put("room_code",room),"INVALID_STATE");error(b,new Message(MessageType.JOIN_ROOM_REQ).put("room_code","BAD123"),"ROOM_NOT_FOUND");
            ok(b,new Message(MessageType.JOIN_ROOM_REQ).put("room_code",room));Message joined=a.waitType(MessageType.ROOM_USER_JOINED);b.waitType(MessageType.ROOM_USER_JOINED);String placing=(String)joined.getData().get("match_id");
            error(c,new Message(MessageType.JOIN_ROOM_REQ).put("room_code",room),"ROOM_FULL");
            int records=server.getStorage().getMatchRecords().size();ok(a,match(MessageType.LEAVE_ROOM_REQ,placing));a.waitType(MessageType.ROOM_USER_LEFT);b.waitType(MessageType.ROOM_USER_LEFT);check(server.getStorage().getMatchRecords().size()==records,"placing exit unscored");
            error(a,new Message(MessageType.INVITE_REQ).put("target_user_id",a.userId),"INVALID_STATE");
            error(b,new Message(MessageType.INVITE_RESPOND_REQ).put("invitation_id","forged").put("accept",true),"INVITE_EXPIRED");
            Message invite=ok(a,new Message(MessageType.INVITE_REQ).put("target_user_id",b.userId));String iid=(String)invite.getData().get("invitation_id");b.waitType(MessageType.INVITE_NOTIFY);
            ok(b,new Message(MessageType.INVITE_RESPOND_REQ).put("invitation_id",iid).put("accept",false));a.waitType(MessageType.INVITE_RESULT);
            invite=ok(a,new Message(MessageType.INVITE_REQ).put("target_user_id",b.userId));iid=(String)invite.getData().get("invitation_id");b.waitType(MessageType.INVITE_NOTIFY);
            a.waitFor(m->m.getType()==MessageType.INVITE_RESULT && "INVITE_EXPIRED".equals(m.getErrorCode()),2000);
            error(b,new Message(MessageType.INVITE_RESPOND_REQ).put("invitation_id",iid).put("accept",true),"INVITE_EXPIRED");b.clear();a.clear();
            invite=ok(a,new Message(MessageType.INVITE_REQ).put("target_user_id",b.userId));iid=(String)invite.getData().get("invitation_id");b.waitType(MessageType.INVITE_NOTIFY);
            ok(b,new Message(MessageType.INVITE_RESPOND_REQ).put("invitation_id",iid).put("accept",true));Message ie=a.waitFor(m->m.getType()==MessageType.INVITE_RESULT && m.getData().containsKey("match_id"),5000);b.waitFor(m->m.getType()==MessageType.INVITE_RESULT && m.getData().containsKey("match_id"),5000);String id=(String)ie.getData().get("match_id");
            error(b,new Message(MessageType.INVITE_RESPOND_REQ).put("invitation_id",iid).put("accept",true),"INVITE_EXPIRED");error(a,new Message(MessageType.QUEUE_JOIN_REQ),"INVALID_STATE");
            String other=queuePair(c,d);check(!id.equals(other),"two independent matches");
            start(c,d,other);
            error(a,ready(id,true),"NOT_READY");
            ok(a,match(MessageType.PLACE_SHIPS_REQ,id).put("ships",fleet()));
            List<Map<String,Object>> malformed=fleet();malformed.get(1).put("id","S0");error(a,match(MessageType.PLACE_SHIPS_REQ,id).put("ships",malformed),"DUPLICATE_SHIP_ID");
            malformed=fleet();((Map<String,Object>)((List<?>)malformed.get(0).get("coordinates")).get(0)).put("x",0.5);error(a,match(MessageType.PLACE_SHIPS_REQ,id).put("ships",malformed),"INVALID_MESSAGE");
            malformed=fleet();((Map<String,Object>)((List<?>)malformed.get(0).get("coordinates")).get(0)).remove("x");error(a,match(MessageType.PLACE_SHIPS_REQ,id).put("ships",malformed),"INVALID_MESSAGE");
            malformed=fleet();malformed.remove(4);error(a,match(MessageType.PLACE_SHIPS_REQ,id).put("ships",malformed),"INVALID_FLEET");
            malformed=fleet();((Map<String,Object>)((List<?>)malformed.get(0).get("coordinates")).get(0)).put("x",-1);error(a,match(MessageType.PLACE_SHIPS_REQ,id).put("ships",malformed),"OUT_OF_BOUNDS");
            malformed=fleet();((Map<String,Object>)((List<?>)malformed.get(1).get("coordinates")).get(0)).put("y",0);error(a,match(MessageType.PLACE_SHIPS_REQ,id).put("ships",malformed),"OVERLAP");
            malformed=fleet();((Map<String,Object>)((List<?>)malformed.get(0).get("coordinates")).get(4)).put("x",6);error(a,match(MessageType.PLACE_SHIPS_REQ,id).put("ships",malformed),"INVALID_FLEET");
            ok(a,ready(id,true));error(a,match(MessageType.PLACE_SHIPS_REQ,id).put("ships",fleet()),"INVALID_STATE");ok(a,ready(id,false));ok(a,ready(id,true));
            ok(b,match(MessageType.PLACE_SHIPS_REQ,id).put("ships",fleet()));ok(b,ready(id,true));Message battle=a.waitType(MessageType.MATCH_START);b.waitType(MessageType.MATCH_START);
            error(a,ready(id,true),"INVALID_STATE");error(a,match(MessageType.PLACE_SHIPS_REQ,id).put("ships",fleet()),"INVALID_STATE");
            SocketPeer shooter=JsonUtil.requireInt(battle.getData(),"current_turn_player_id")==a.userId?a:b;SocketPeer target=shooter==a?b:a;int turn=JsonUtil.requireInt(battle.getData(),"turn_id");
            error(target,match(MessageType.FIRE_REQ,id).put("turn_id",turn).put("x",0).put("y",0),"NOT_YOUR_TURN");
            error(shooter,match(MessageType.FIRE_REQ,"old").put("turn_id",turn).put("x",0).put("y",0),"STALE_MATCH");
            error(shooter,match(MessageType.FIRE_REQ,id).put("turn_id",turn).put("x",10).put("y",0),"OUT_OF_BOUNDS");
            Message hitRequest=match(MessageType.FIRE_REQ,id).put("turn_id",turn).put("x",0).put("y",0);Message hit=ok(shooter,hitRequest);check(hit.getData().equals(ok(shooter,hitRequest).getData()),"replayed shot returns same result");check("HIT".equals(hit.getData().get("result")),"Hit");
            Message next=shooter.waitType(MessageType.TURN_CHANGE);target.waitType(MessageType.TURN_CHANGE);int newTurn=JsonUtil.requireInt(next.getData(),"turn_id");check(newTurn==turn+1 && JsonUtil.requireInt(next.getData(),"current_turn_player_id")==shooter.userId,"hit keeps shooter advances turn");
            error(shooter,match(MessageType.FIRE_REQ,id).put("turn_id",turn).put("x",1).put("y",0),"STALE_TURN");error(shooter,match(MessageType.FIRE_REQ,id).put("turn_id",newTurn).put("x",0).put("y",0),"ALREADY_SHOT");
            Message miss=ok(shooter,match(MessageType.FIRE_REQ,id).put("turn_id",newTurn).put("x",9).put("y",9));check("MISS".equals(miss.getData().get("result")),"miss");next=target.waitType(MessageType.TURN_CHANGE);shooter.waitType(MessageType.TURN_CHANGE);check(JsonUtil.requireInt(next.getData(),"current_turn_player_id")==target.userId,"miss changes turn");
            // Target now sinks the full fleet in 17 hits; no opponent fleet coordinates leak before sinking.
            shooter=target;target=shooter==a?b:a;turn=JsonUtil.requireInt(next.getData(),"turn_id");int[] sizes={5,4,3,3,2};int sunk=0;
            for(int y=0;y<5;y++)for(int x=0;x<sizes[y];x++){
                Message result=ok(shooter,match(MessageType.FIRE_REQ,id).put("turn_id",turn).put("x",x).put("y",y));
                if("SUNK".equals(result.getData().get("result"))){sunk++;check(((List<?>)result.getData().get("sunk_ship_coords")).size()==sizes[y],"sunk coords");}else check(!result.getData().containsKey("sunk_ship_coords"),"no hidden fleet leak");
                if(sunk<5){next=shooter.waitType(MessageType.TURN_CHANGE);target.waitType(MessageType.TURN_CHANGE);turn=JsonUtil.requireInt(next.getData(),"turn_id");}
            }
            Message ended=shooter.waitType(MessageType.MATCH_END);target.waitType(MessageType.MATCH_END);check(JsonUtil.requireInt(ended.getData(),"winner_id")==shooter.userId,"winner");check(Boolean.TRUE.equals(ended.getData().get("storage_saved")),"result stored");check(server.getStorage().getMatchRecords().size()==1,"one result");
            ok(a,match(MessageType.REMATCH_REQ,id));ok(b,match(MessageType.REMATCH_REQ,id));Message rm=a.waitType(MessageType.REMATCH_START);b.waitType(MessageType.REMATCH_START);String rematch=(String)rm.getData().get("match_id");check(!rematch.equals(id),"new rematch id");error(a,ready(id,true),"STALE_MATCH");
            battle=start(a,b,rematch);Message timeout=a.waitFor(m->m.getType()==MessageType.TIMEOUT_NOTIFY && rematch.equals(m.getData().get("match_id")),GameConfig.TURN_TIMEOUT_MS+3000);check(!timeout.getData().containsKey("auto_x"),"timeout never auto fires");next=a.waitFor(m->m.getType()==MessageType.TURN_CHANGE && rematch.equals(m.getData().get("match_id")),5000);check(JsonUtil.requireInt(next.getData(),"current_turn_player_id")!=JsonUtil.requireInt(battle.getData(),"current_turn_player_id"),"timeout swaps shooter");
            ok(a,match(MessageType.SURRENDER_REQ,rematch));check(server.getStorage().getMatchRecords().size()==2,"surrender result once");a.waitType(MessageType.ROOM_USER_LEFT);b.waitType(MessageType.ROOM_USER_LEFT);
            // The other RUNNING session remains independent after the first match ends.
            ok(c,match(MessageType.SURRENDER_REQ,other));check(server.getStorage().getMatchRecords().size()==3,"other match independent");c.waitType(MessageType.ROOM_USER_LEFT);d.waitType(MessageType.ROOM_USER_LEFT);
            Message board=ok(d,new Message(MessageType.GET_LEADERBOARD_REQ));check(JsonUtil.getList(board.getData(),"leaderboard").size()==4,"leaderboard accounts");
            ok(d,new Message(MessageType.LOGOUT_REQ));error(d,new Message(MessageType.GET_LEADERBOARD_REQ),"UNAUTHENTICATED");
            Message oldBoard=board;error(d,new Message(MessageType.GET_LEADERBOARD_REQ).requestId(oldBoard.getRequestId()),"UNAUTHENTICATED");
            // Oversized and incomplete/malformed UTF-8 connections cannot disturb authenticated peers.
            try(SocketPeer bad=new SocketPeer(port,false)){bad.raw(String.join("",Collections.nCopies(65538,"x")));}
            try(SocketPeer bad=new SocketPeer(port,false)){bad.raw("{\"type\":");bad.socket.shutdownOutput();}
            try(SocketPeer bad=new SocketPeer(port,false)){bad.socket.getOutputStream().write(new byte[]{(byte)0xC3,0x28,10});bad.socket.getOutputStream().flush();}
            ok(a,new Message(MessageType.GET_ONLINE_USERS_REQ));
            try(SocketPeer silent=new SocketPeer(port,false)){silent.login("silent");a.waitFor(m->m.getType()==MessageType.USER_STATUS_BROADCAST && "silent".equals(m.getData().get("username")) && "OFFLINE".equals(m.getData().get("status")),5000);}
            System.out.println("PASS TCP: framing/auth/presence/rooms/invites/queue/fleet/combat/rematch/timeout/disconnect (4 clients)");
        }finally{server.stop();thread.join(5000);check(!thread.isAlive(),"server shutdown");}
        StorageManager restarted=new StorageManager(dir);check(restarted.getMatchRecords().size()==3,"restart history count");for(MatchRecord r:restarted.getMatchRecords()){check(r.getTimestamp()==r.getEndedAt() && r.getStartedAt()>0,"timestamps survived restart");check(restarted.commitResult(r),"repeat commit acknowledged");}check(restarted.getMatchRecords().size()==3,"repeat commits idempotent");
        check(Files.exists(dir.resolve("battleship.db")) && new String(Files.readAllBytes(dir.resolve("battleship.db")),StandardCharsets.ISO_8859_1).startsWith("SQLite format 3"),"real SQLite database");
        System.out.println("PASS restart/atomic history/idempotent scoring; temporary data: "+dir);
    }
}
