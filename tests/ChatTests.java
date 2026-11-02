package tests;

import common.protocol.*;
import server.manager.*;
import server.network.GameServer;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

public final class ChatTests {
    private static void check(boolean value,String text){TestRunner.check(value,text);}
    private static Message send(int recipient,String text,String key){return new Message(MessageType.CHAT_SEND_REQ).put("recipient_id",recipient).put("body",text).put("client_message_id",key);}
    private static Message ok(SocketPeer p,Message m)throws Exception{Message r=p.request(m);check("SUCCESS".equals(r.getStatus()),"chat request succeeds: "+r);return r;}
    private static Message history(SocketPeer p,int peer,long before)throws Exception{return ok(p,new Message(MessageType.CHAT_HISTORY_REQ).put("peer_id",peer).put("before_id",before));}
    private static List<Object> rows(Message m){return JsonUtil.getList(m.getData(),"messages");}
    private static void noChat(SocketPeer peer)throws Exception {
        try{peer.waitFor(m->m.getType()==MessageType.CHAT_MSG,180);}catch(AssertionError expected){if(expected.getMessage().startsWith("Timed out"))return;throw expected;}
        throw new AssertionError("Private chat was delivered to an unintended recipient or twice");
    }
    public static void run()throws Exception {
        Path dir=DomainTests.empty("battleship-chat-");GameServer server=new GameServer(0,dir);Thread thread=PenaltyTests.start(server);int alice,bob,third;
        String body="Chào bạn 👋\nHẹn gặp ở biển! <html> & ' ; DROP TABLE accounts; --";
        try(SocketPeer a=new SocketPeer(server.getPort(),true);SocketPeer b=new SocketPeer(server.getPort(),true);SocketPeer c=new SocketPeer(server.getPort(),true)) {
            IntegrationTests.error(a,send(1,"hello","no-auth"),"UNAUTHENTICATED");
            IntegrationTests.error(a,new Message(MessageType.CHAT_HISTORY_REQ).put("peer_id",1),"UNAUTHENTICATED");
            IntegrationTests.error(a,new Message(MessageType.CHAT_CONVERSATIONS_REQ),"UNAUTHENTICATED");
            a.login("chat_alice");b.login("chat_bob");c.login("chat_charlie");alice=a.userId;bob=b.userId;third=c.userId;
            Message first=ok(a,send(bob,body,"hello-1").put("sender_id",third));Map<String,Object> saved=JsonUtil.getMap(first.getData(),"chat");
            check(JsonUtil.getInt(saved,"sender_id",0)==alice,"client cannot spoof sender");check(body.equals(saved.get("body")),"Unicode, newlines and literal markup preserved");
            check(JsonUtil.getMap(b.waitType(MessageType.CHAT_MSG).getData(),"chat").get("message_id").equals(saved.get("message_id")),"recipient sees committed message");noChat(c);
            Message retry=ok(a,send(bob,body,"hello-1"));check(JsonUtil.getMap(retry.getData(),"chat").get("message_id").equals(saved.get("message_id")),"SQL retry keeps stable message ID");noChat(b);
            IntegrationTests.error(a,send(bob,"changed","hello-1"),"INVALID_MESSAGE");
            IntegrationTests.error(a,send(alice,"self","bad-self"),"INVALID_MESSAGE");
            IntegrationTests.error(a,send(999999,"missing","bad-user"),"USER_NOT_FOUND");
            IntegrationTests.error(a,send(bob," \n ","blank"),"INVALID_MESSAGE");
            IntegrationTests.error(a,send(bob,String.join("",Collections.nCopies(501,"a")),"large"),"INVALID_MESSAGE");
            IntegrationTests.error(a,send(bob,"bad\u0000control","control"),"INVALID_MESSAGE");
            check(rows(history(c,alice,0)).isEmpty(),"third account cannot read Alice/Bob conversation");
            Message spoofed=ok(c,new Message(MessageType.CHAT_HISTORY_REQ).put("peer_id",bob).put("user_id",alice));check(rows(spoofed).isEmpty(),"history ignores forged owner ID");
            check(JsonUtil.getList(ok(c,new Message(MessageType.CHAT_CONVERSATIONS_REQ)).getData(),"conversations").isEmpty(),"conversation list is private");
            IntegrationTests.error(c,new Message(MessageType.CHAT_HISTORY_REQ).put("peer_id",bob).put("before_id",-1),"INVALID_MESSAGE");
            // A persistence failure must not acknowledge or deliver a message.
            try(Connection db=DriverManager.getConnection("jdbc:sqlite:"+dir.resolve("battleship.db"));Statement s=db.createStatement()){s.execute("CREATE TRIGGER chat_fail BEFORE INSERT ON chat_messages BEGIN SELECT RAISE(ABORT,'test disk failure'); END");}
            IntegrationTests.error(b,send(alice,"fail-save","fail-save"),"STORAGE_ERROR");noChat(a);
            try(Connection db=DriverManager.getConnection("jdbc:sqlite:"+dir.resolve("battleship.db"));Statement s=db.createStatement()){s.execute("DROP TRIGGER chat_fail");}
            ok(b,send(alice,"Đã lưu thành công","saved-2"));a.waitType(MessageType.CHAT_MSG);
            // Both players can chat during placement and RUNNING without advancing a turn.
            String match=IntegrationTests.queuePair(a,b);ok(a,send(bob,"Sẵn sàng chưa?","placing"));b.waitType(MessageType.CHAT_MSG);
            IntegrationTests.ok(a,IntegrationTests.match(MessageType.PLACE_SHIPS_REQ,match).put("ships",IntegrationTests.fleet()));IntegrationTests.ok(b,IntegrationTests.match(MessageType.PLACE_SHIPS_REQ,match).put("ships",IntegrationTests.fleet()));
            IntegrationTests.ok(a,IntegrationTests.ready(match,true));IntegrationTests.ok(b,IntegrationTests.ready(match,true));a.waitType(MessageType.MATCH_START);b.waitType(MessageType.MATCH_START);
            ok(b,send(alice,"Chúc may mắn!","battle"));a.waitType(MessageType.CHAT_MSG);
            // Logout third account: sending offline is durable, not a global broadcast.
            ok(c,new Message(MessageType.LOGOUT_REQ));ok(b,send(third,"Khi nào online thì chơi nhé","offline"));c.login("chat_charlie");check(rows(history(c,bob,0)).size()==1,"offline recipient retrieves message on next login");
            for(int i=0;i<27;i++)server.getStorage().saveChat(alice,bob,String.join("",Collections.nCopies(500,"ệ")),"page-"+i);
            Message page=history(b,alice,0);check(rows(page).size()==20&&JsonUtil.getBoolean(page.getData(),"has_more",false),"history is paginated");check(page.toJson().getBytes("UTF-8").length<common.config.GameConfig.MAX_FRAME_BYTES,"maximum Unicode page fits TCP frame");
            @SuppressWarnings("unchecked") Map<String,Object> oldest=(Map<String,Object>)rows(page).get(0);Message earlier=history(b,alice,JsonUtil.getLong(oldest,"message_id",0));check(rows(earlier).size()==11&&!JsonUtil.getBoolean(earlier.getData(),"has_more",true),"older page covers remainder without overlap");
            Set<Long> ids=new HashSet<>();for(Object o:rows(page)){@SuppressWarnings("unchecked") Map<String,Object> row=(Map<String,Object>)o;check(ids.add(JsonUtil.getLong(row,"message_id",0)),"unique page IDs");}for(Object o:rows(earlier)){@SuppressWarnings("unchecked") Map<String,Object> row=(Map<String,Object>)o;check(ids.add(JsonUtil.getLong(row,"message_id",0)),"no page overlap");}
            // Rate limiting is scoped to sender; reads continue to work.
            boolean limited=false;for(int i=0;i<12;i++){Message r=c.request(send(bob,"ping","rate-"+i));if("CHAT_RATE_LIMIT".equals(r.getErrorCode()))limited=true;}check(limited,"burst flood rejected");history(c,bob,0);
        }finally{server.stop();thread.join(5000);}
        // Restart the actual server, not just an in-memory repository.
        GameServer restored=new GameServer(0,dir);Thread restoredThread=PenaltyTests.start(restored);
        try(SocketPeer a=new SocketPeer(restored.getPort(),true);SocketPeer b=new SocketPeer(restored.getPort(),true)) {
            a.login("chat_alice");b.login("chat_bob");check(rows(history(a,bob,0)).size()==20,"history survives server restart");
            int old=alice;IntegrationTests.ok(a,new Message(MessageType.DELETE_ACCOUNT_REQ).put("password","secret"));
            Message archived=history(b,old,0);check(!JsonUtil.getBoolean(archived.getData(),"available",true),"deleted contact is read-only");check(!archived.toJson().contains("chat_alice"),"history anonymizes deleted account names");
            IntegrationTests.error(b,send(old,"gone","deleted"),"USER_NOT_FOUND");a.login("chat_alice");check(a.userId!=old&&rows(history(a,bob,0)).isEmpty(),"new account cannot inherit old private history");
        }finally{restored.stop();restoredThread.join(5000);}
        System.out.println("PASS chat: private TCP delivery, auth/identity isolation, SQL commit failure, idempotency, Unicode/frame bounds, paging, offline/restart, deletion, game chat and rate limit");
    }
}
