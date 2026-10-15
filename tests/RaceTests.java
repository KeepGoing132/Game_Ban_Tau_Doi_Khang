package tests;

import common.config.GameConfig;
import common.protocol.*;
import common.model.*;
import server.network.GameServer;
import java.net.Socket;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public class RaceTests {
    static void check(boolean c,String m){TestRunner.check(c,m);}
    static List<Message> together(SocketPeer[] peers,Message[] requests)throws Exception{
        ExecutorService pool=Executors.newFixedThreadPool(peers.length);CountDownLatch ready=new CountDownLatch(peers.length),go=new CountDownLatch(1);List<Future<Message>> futures=new ArrayList<>();
        try{for(int i=0;i<peers.length;i++){final int n=i;futures.add(pool.submit(()->{ready.countDown();if(!go.await(5,TimeUnit.SECONDS))throw new AssertionError("barrier");return peers[n].request(requests[n]);}));}check(ready.await(5,TimeUnit.SECONDS),"workers ready");go.countDown();List<Message> results=new ArrayList<>();for(Future<Message> f:futures)results.add(f.get(6,TimeUnit.SECONDS));return results;}finally{pool.shutdownNow();}
    }
    static Message login(String username){return new Message(MessageType.LOGIN_REQ).put("username",username).put("password","secret");}
    static void leave(SocketPeer p,String id)throws Exception{IntegrationTests.ok(p,IntegrationTests.match(MessageType.LEAVE_ROOM_REQ,id));}
    static void clean(SocketPeer...peers){for(SocketPeer p:peers)p.clear();}
    static void sink(SocketPeer a,SocketPeer b,String id,Message start)throws Exception {
        SocketPeer shooter=JsonUtil.requireInt(start.getData(),"current_turn_player_id")==a.userId?a:b,target=shooter==a?b:a;int turn=JsonUtil.requireInt(start.getData(),"turn_id"),count=0;int[] sizes={5,4,3,3,2};
        for(int y=0;y<5;y++)for(int x=0;x<sizes[y];x++){IntegrationTests.ok(shooter,IntegrationTests.match(MessageType.FIRE_REQ,id).put("turn_id",turn).put("x",x).put("y",y));if(++count<17){turn=JsonUtil.requireInt(shooter.waitType(MessageType.TURN_CHANGE).getData(),"turn_id");target.waitType(MessageType.TURN_CHANGE);}}
    }
    public static void run()throws Exception{
        Path dir=DomainTests.empty("battleship-race-");java.util.concurrent.atomic.AtomicLong clockOffset=new java.util.concurrent.atomic.AtomicLong();GameServer server=new GameServer(0,new server.manager.StorageManager(dir,()->System.currentTimeMillis()+clockOffset.get()));Thread st=new Thread(server::start);st.start();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(server.getPort()<=0 && System.nanoTime()<deadline)Thread.sleep(5);
        int port=server.getPort();
        try(SocketPeer a=new SocketPeer(port,true);SocketPeer b=new SocketPeer(port,true);SocketPeer c=new SocketPeer(port,true);SocketPeer d=new SocketPeer(port,true)){
            List<Message> logins=together(new SocketPeer[]{a,b},new Message[]{login("race"),login("RACE")});check(logins.stream().filter(m->"SUCCESS".equals(m.getStatus())).count()==1,"one owner concurrent duplicate login");
            SocketPeer owner="SUCCESS".equals(logins.get(0).getStatus())?a:b,second=owner==a?b:a;owner.userId=JsonUtil.requireInt(logins.get(owner==a?0:1).getData(),"user_id");second.login("second");c.login("third");d.login("fourth");
            // Keep explicitly assigned IDs from the successful login, rather than deriving identity from presence.
            a.userId=a==owner?owner.userId:second.userId;b.userId=b==owner?owner.userId:second.userId;
            String room=(String)IntegrationTests.ok(a,new Message(MessageType.CREATE_ROOM_REQ)).getData().get("room_id");
            List<Message> joins=together(new SocketPeer[]{b,c},new Message[]{new Message(MessageType.JOIN_ROOM_REQ).put("room_code",room),new Message(MessageType.JOIN_ROOM_REQ).put("room_code",room)});
            check(joins.stream().filter(m->"SUCCESS".equals(m.getStatus())).count()==1,"one contested room slot");Message joined=a.waitType(MessageType.ROOM_USER_JOINED);leave(a,(String)joined.getData().get("match_id"));clean(a,b,c,d);
            String i1=(String)IntegrationTests.ok(a,new Message(MessageType.INVITE_REQ).put("target_user_id",b.userId)).getData().get("invitation_id");
            String i2=(String)IntegrationTests.ok(a,new Message(MessageType.INVITE_REQ).put("target_user_id",c.userId)).getData().get("invitation_id");
            List<Message> accepts=together(new SocketPeer[]{b,c},new Message[]{new Message(MessageType.INVITE_RESPOND_REQ).put("invitation_id",i1).put("accept",true),new Message(MessageType.INVITE_RESPOND_REQ).put("invitation_id",i2).put("accept",true)});
            check(accepts.stream().filter(m->"SUCCESS".equals(m.getStatus())).count()==1,"crossing invites cannot pair twice");Message pair=a.waitFor(m->m.getType()==MessageType.INVITE_RESULT && m.getData().containsKey("match_id"),5000);leave(a,(String)pair.getData().get("match_id"));clean(a,b,c,d);
            String invite=(String)IntegrationTests.ok(a,new Message(MessageType.INVITE_REQ).put("target_user_id",b.userId)).getData().get("invitation_id");
            List<Message> competing=together(new SocketPeer[]{b,b},new Message[]{new Message(MessageType.QUEUE_JOIN_REQ),new Message(MessageType.INVITE_RESPOND_REQ).put("invitation_id",invite).put("accept",true)});
            check(competing.stream().filter(m->"SUCCESS".equals(m.getStatus())).count()==1,"queue vs invitation one winner");
            if("SUCCESS".equals(competing.get(0).getStatus()))IntegrationTests.ok(b,new Message(MessageType.QUEUE_LEAVE_REQ));else{pair=a.waitFor(m->m.getType()==MessageType.INVITE_RESULT && m.getData().containsKey("match_id"),5000);leave(a,(String)pair.getData().get("match_id"));}clean(a,b,c,d);
            List<Message> queued=together(new SocketPeer[]{a,b,c,d},new Message[]{new Message(MessageType.QUEUE_JOIN_REQ),new Message(MessageType.QUEUE_JOIN_REQ),new Message(MessageType.QUEUE_JOIN_REQ),new Message(MessageType.QUEUE_JOIN_REQ)});check(queued.stream().allMatch(m->"SUCCESS".equals(m.getStatus())),"four queue acknowledgments");
            Map<String,List<SocketPeer>> groups=new HashMap<>();for(SocketPeer p:Arrays.asList(a,b,c,d)){String id=(String)p.waitType(MessageType.MATCH_FOUND).getData().get("match_id");groups.computeIfAbsent(id,k->new ArrayList<>()).add(p);}check(groups.size()==2 && groups.values().stream().allMatch(g->g.size()==2),"four concurrent users exactly two pairs");
            for(Map.Entry<String,List<SocketPeer>> entry:groups.entrySet())leave(entry.getValue().get(0),entry.getKey());clean(a,b,c,d);
            String id=IntegrationTests.queuePair(a,b);IntegrationTests.ok(a,IntegrationTests.match(MessageType.PLACE_SHIPS_REQ,id).put("ships",IntegrationTests.fleet()));IntegrationTests.ok(b,IntegrationTests.match(MessageType.PLACE_SHIPS_REQ,id).put("ships",IntegrationTests.fleet()));
            together(new SocketPeer[]{a,b},new Message[]{IntegrationTests.ready(id,true),IntegrationTests.ready(id,true)});a.waitType(MessageType.MATCH_START);b.waitType(MessageType.MATCH_START);
            // A non-reading socket fills only its own bounded write queue.
            try(Socket slow=new Socket("127.0.0.1",port)){
                slow.setReceiveBufferSize(1024);
                Thread flood=new Thread(()->{try{StringBuilder text=new StringBuilder(login("slow").toJson()+"\n");for(int n=0;n<8000;n++)text.append(new Message(MessageType.GET_ONLINE_USERS_REQ).requestId("flood-"+n).toJson()).append('\n');slow.getOutputStream().write(text.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));slow.getOutputStream().flush();}catch(Exception ignored){}},"slow-peer-flood");flood.setDaemon(true);flood.start();
                IntegrationTests.ok(c,new Message(MessageType.GET_ONLINE_USERS_REQ));a.waitFor(m->m.getType()==MessageType.TIMEOUT_NOTIFY && id.equals(m.getData().get("match_id")),GameConfig.TURN_TIMEOUT_MS+3000);
                c.waitFor(m->m.getType()==MessageType.USER_STATUS_BROADCAST && "slow".equals(m.getData().get("username")) && "OFFLINE".equals(m.getData().get("status")),5000);
                check(server.getConnectionCount()<=4,"non-reading peer was cleaned up");
            }
            a.close();Message ended=b.waitType(MessageType.MATCH_END);check("DISCONNECTED".equals(ended.getData().get("reason")),"disconnect distinct from surrender");b.waitType(MessageType.ROOM_USER_LEFT);check(server.getStorage().getMatchRecords().size()==1,"disconnect awards once");
            clean(b,c,d);String post=IntegrationTests.queuePair(c,d);IntegrationTests.start(c,d,post);
            // Sink via surrender-like direct disconnect is already checked; normal game end retains post-game state.
            // Domain final-shot is covered in IntegrationTests; use a full fleet here for post-game expiry.
            Message online=IntegrationTests.ok(c,new Message(MessageType.GET_ONLINE_USERS_REQ));
            // Close d in PLACING is separately checked by queue cleanup below.
            IntegrationTests.ok(c,IntegrationTests.match(MessageType.SURRENDER_REQ,post));c.waitType(MessageType.ROOM_USER_LEFT);d.waitType(MessageType.ROOM_USER_LEFT);
            clockOffset.addAndGet(GameConfig.MATCHMAKING_PENALTY_MS);
            try(SocketPeer dead=new SocketPeer(port,true)){dead.login("queue_dead");IntegrationTests.ok(dead,new Message(MessageType.QUEUE_JOIN_REQ));dead.close();b.waitFor(m->m.getType()==MessageType.USER_STATUS_BROADCAST && "queue_dead".equals(m.getData().get("username")) && "OFFLINE".equals(m.getData().get("status")),5000);}
            String kept=IntegrationTests.queuePair(b,c);leave(b,kept);
            clean(c,d);String expiry=IntegrationTests.queuePair(c,d);Message start=IntegrationTests.start(c,d,expiry);sink(c,d,expiry,start);c.waitType(MessageType.MATCH_END);d.waitType(MessageType.MATCH_END);
            c.waitFor(m->m.getType()==MessageType.ROOM_USER_LEFT && expiry.equals(m.getData().get("match_id")),GameConfig.REMATCH_TIMEOUT_MS+2000);d.waitType(MessageType.ROOM_USER_LEFT);
            clean(c,d);String reject=IntegrationTests.queuePair(c,d);start=IntegrationTests.start(c,d,reject);sink(c,d,reject,start);c.waitType(MessageType.MATCH_END);d.waitType(MessageType.MATCH_END);
            IntegrationTests.ok(c,IntegrationTests.match(MessageType.REMATCH_CONFIRM_REQ,reject).put("accept",false));c.waitType(MessageType.ROOM_USER_LEFT);d.waitType(MessageType.ROOM_USER_LEFT);
            try(SocketPeer phase=new SocketPeer(port,true)){
                phase.login("phase_user");IntegrationTests.ok(phase,new Message(MessageType.QUEUE_JOIN_REQ));phase.close();d.waitFor(m->m.getType()==MessageType.USER_STATUS_BROADCAST && "phase_user".equals(m.getData().get("username")) && "OFFLINE".equals(m.getData().get("status")),5000);
            }
            try(SocketPeer phase=new SocketPeer(port,true)){
                phase.login("phase_user");String waitingRoom=(String)IntegrationTests.ok(phase,new Message(MessageType.CREATE_ROOM_REQ)).getData().get("room_id");phase.close();d.waitFor(m->m.getType()==MessageType.USER_STATUS_BROADCAST && "phase_user".equals(m.getData().get("username")) && "OFFLINE".equals(m.getData().get("status")),5000);IntegrationTests.error(d,new Message(MessageType.JOIN_ROOM_REQ).put("room_code",waitingRoom),"ROOM_NOT_FOUND");
            }
            clean(d);try(SocketPeer phase=new SocketPeer(port,true)){
                phase.login("phase_user");String preparing=IntegrationTests.queuePair(phase,d);int count=server.getStorage().getMatchRecords().size();phase.close();d.waitFor(m->m.getType()==MessageType.ROOM_USER_LEFT && preparing.equals(m.getData().get("match_id")),5000);check(server.getStorage().getMatchRecords().size()==count,"PLACING disconnect unscored");
            }
            clean(d);try(SocketPeer phase=new SocketPeer(port,true)){
                phase.login("phase_user");String finished=IntegrationTests.queuePair(phase,d);Message beginning=IntegrationTests.start(phase,d,finished);sink(phase,d,finished,beginning);phase.waitType(MessageType.MATCH_END);d.waitType(MessageType.MATCH_END);int count=server.getStorage().getMatchRecords().size();phase.close();d.waitFor(m->m.getType()==MessageType.ROOM_USER_LEFT && finished.equals(m.getData().get("match_id")),5000);check(server.getStorage().getMatchRecords().size()==count,"POST_GAME disconnect never scores again");
            }
            System.out.println("PASS races: duplicate login/contested room/crossing invite/queue-accept/four queue/simultaneous Ready/slow peer/cleanup");
        }finally{server.stop();st.join(5000);check(!st.isAlive(),"race server stopped");}
    }
}
