package tests;

import client.model.FleetPresets;
import client.network.*;
import common.config.GameConfig;
import common.model.*;
import common.protocol.*;
import server.manager.*;
import server.model.MatchSession;
import server.network.GameServer;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.prefs.Preferences;

/** Regression cases for the October completion plan, with isolated accounts/files/processes. */
public final class UpgradeTests {
    private static void check(boolean value,String text) { TestRunner.check(value,text); }

    public static void run() throws Exception {
        passwords(); placementClock(); presets(); history(); latency(); recovery(); pendingResultsOverTcp();
        child("placement",null,0);
        System.out.println("PASS completion: password changes, placement boundary/expiry, presets, history paging/filters, heartbeat latency, crash recovery");
    }

    public static MatchRecord record(StorageManager storage,String id,long time,boolean win) {
        User a=storage.findByUsername("upgrade_a"),b=storage.findByUsername("upgrade_b");
        MatchRecord r=new MatchRecord(); r.setMatchId(id); r.setPlayer1Id(a.getUserId()); r.setPlayer1Name(a.getUsername());
        r.setPlayer2Id(b.getUserId()); r.setPlayer2Name(b.getUsername()); r.setWinnerId((win?a:b).getUserId());
        r.setWinnerName((win?a:b).getUsername()); r.setLoserId((win?b:a).getUserId()); r.setLoserName((win?b:a).getUsername());
        r.setStartedAt(Math.max(0,time-1000)); r.setEndedAt(time); r.setTimestamp(time); r.setReason("ALL_SHIPS_DESTROYED");
        return r;
    }
    private static StorageManager empty(String prefix) throws Exception {
        StorageManager s=new StorageManager(DomainTests.empty(prefix)); s.registerUser("upgrade_a","secret"); s.registerUser("upgrade_b","secret"); return s;
    }

    private static void passwords() throws Exception {
        Path dir=DomainTests.empty("battleship-password-");
        DomainTests.FaultStorage storage=new DomainTests.FaultStorage(dir);
        GameServer server=new GameServer(0,storage); Thread thread=PenaltyTests.start(server);
        try (SocketPeer a=new SocketPeer(server.getPort(),true); SocketPeer b=new SocketPeer(server.getPort(),true)) {
            Message change=new Message(MessageType.CHANGE_PASSWORD_REQ).put("old_password","secret").put("new_password","new-secret");
            IntegrationTests.error(a,change,"UNAUTHENTICATED"); a.login("upgrade_a"); b.login("upgrade_b");
            User user=storage.findByUsername("upgrade_a"); String hash=user.getPassword();
            IntegrationTests.error(a,new Message(MessageType.CHANGE_PASSWORD_REQ).put("old_password","wrong").put("new_password","new-secret"),"AUTH_FAILED");
            IntegrationTests.error(a,new Message(MessageType.CHANGE_PASSWORD_REQ).put("old_password","secret").put("new_password","   "),"INVALID_MESSAGE");
            IntegrationTests.error(a,new Message(MessageType.CHANGE_PASSWORD_REQ).put("old_password","secret").put("new_password",String.join("",Collections.nCopies(129,"a"))),"INVALID_MESSAGE");
            IntegrationTests.ok(a,new Message(MessageType.QUEUE_JOIN_REQ));
            IntegrationTests.error(a,new Message(MessageType.CHANGE_PASSWORD_REQ).put("old_password","secret").put("new_password","new-secret"),"INVALID_STATE");
            IntegrationTests.ok(a,new Message(MessageType.QUEUE_LEAVE_REQ));
            storage.fail=true;
            IntegrationTests.error(a,new Message(MessageType.CHANGE_PASSWORD_REQ).put("old_password","secret").put("new_password","new-secret"),"STORAGE_ERROR");
            check(hash.equals(user.getPassword()),"failed password write keeps current hash"); storage.fail=false;
            Message saved=new Message(MessageType.CHANGE_PASSWORD_REQ).requestId("change-once").put("old_password","secret").put("new_password","new-secret").put("user_id",b.userId);
            IntegrationTests.ok(a,saved); IntegrationTests.ok(a,saved);
            check(PasswordHasher.verify("new-secret",user.getPassword()),"password changed for authenticated sender");
            check(PasswordHasher.verify("secret",storage.findByUsername("upgrade_b").getPassword()),"spoofed target untouched");
            IntegrationTests.ok(a,new Message(MessageType.LOGOUT_REQ));
            IntegrationTests.error(a,new Message(MessageType.LOGIN_REQ).put("username","upgrade_a").put("password","secret"),"AUTH_FAILED");
            IntegrationTests.ok(a,new Message(MessageType.LOGIN_REQ).put("username","upgrade_a").put("password","new-secret"));
            String id=IntegrationTests.queuePair(a,b);
            IntegrationTests.error(a,new Message(MessageType.CHANGE_PASSWORD_REQ).put("old_password","new-secret").put("new_password","again"),"INVALID_STATE");
            IntegrationTests.start(a,b,id);
            IntegrationTests.error(a,new Message(MessageType.CHANGE_PASSWORD_REQ).put("old_password","new-secret").put("new_password","again"),"INVALID_STATE");
            IntegrationTests.ok(a,IntegrationTests.match(MessageType.LEAVE_ROOM_REQ,id));
        } finally { server.stop(); thread.join(5000); }
        check(PasswordHasher.verify("new-secret",new StorageManager(dir).findByUsername("upgrade_a").getPassword()),"password survives restart");
    }

    private static void placementClock() throws Exception {
        StorageManager storage=empty("battleship-placement-clock-"); UserManager users=new UserManager(storage);
        MatchManager manager=new MatchManager(users,storage); ScheduledThreadPoolExecutor scheduler=new ScheduledThreadPoolExecutor(1);
        AtomicLong ms=new AtomicLong(100000),ns=new AtomicLong(1000000);
        DomainTests.Capture a=new DomainTests.Capture(storage.findByUsername("upgrade_a"),users,manager),b=new DomainTests.Capture(storage.findByUsername("upgrade_b"),users,manager);
        try {
            MatchSession s=new MatchSession("placement-boundary","room",a,b,users,storage,scheduler,()->{},ms::get,ns::get,new Random(1));
            CountDownLatch expired=new CountDownLatch(1); s.beginPlacement(expired::countDown);
            s.place(a,DomainTests.ships()); s.place(b,DomainTests.ships()); s.ready(a,true);
            long deadline=JsonUtil.getLong(s.placementInfo(new Message(MessageType.MATCH_FOUND)).getData(),"placement_deadline_ms",0);
            s.ready(a,false); s.ready(a,true);
            check(deadline==JsonUtil.getLong(s.placementInfo(new Message(MessageType.MATCH_FOUND)).getData(),"placement_deadline_ms",0),"Ready never extends placement");
            ns.addAndGet(TimeUnit.MILLISECONDS.toNanos(GameConfig.PLACEMENT_TIMEOUT_MS)); ms.addAndGet(GameConfig.PLACEMENT_TIMEOUT_MS);
            DomainTests.expect("PLACEMENT_EXPIRED",()->s.ready(b,true));
            check(expired.await(2,TimeUnit.SECONDS) && s.getState()==MatchSession.State.CANCELLED,"Ready at exact deadline cancels once");
            s.placementTimeout(); check(storage.getMatchRecords().isEmpty() && a.getUser().getTotalGames()==0,"placement expiry has no scoring");
            MatchSession started=new MatchSession("placement-started","room2",a,b,users,storage,scheduler,()->{},ms::get,ns::get,new Random(1));
            started.beginPlacement(()->{throw new AssertionError("old placement callback touched battle");});
            started.place(a,DomainTests.ships()); started.place(b,DomainTests.ships()); started.ready(a,true); started.ready(b,true);
            ns.addAndGet(TimeUnit.MILLISECONDS.toNanos(GameConfig.PLACEMENT_TIMEOUT_MS)); started.placementTimeout();
            check(started.getState()==MatchSession.State.RUNNING,"placement callback inert after match start"); started.cancel();
        } finally { scheduler.shutdownNow(); manager.shutdown(); }
    }

    private static void presets() throws Exception {
        Preferences root=Preferences.userRoot().node("battleship-tests/"+UUID.randomUUID());
        try {
            FleetPresets first=new FleetPresets(root,"host:8888",1);
            for (int slot=0;slot<5;slot++) first.save(slot,"Mẫu "+slot,DomainTests.ships());
            check(first.names().size()==5,"five saved fleet slots");
            first.rename(0,"Hạm đội A");
            check(new FleetPresets(root,"host:8888",1).load(0).size()==5,"fleet persists across instances");
            check(new FleetPresets(root,"host:8888",2).names().isEmpty() && new FleetPresets(root,"other:8888",1).names().isEmpty(),"presets isolated by account and endpoint");
            List<Ship> bad=DomainTests.ships(); bad.get(1).getCoordinates().set(0,new Coordinate(0,0));
            try { first.save(0,"bad",bad); throw new AssertionError("invalid preset accepted"); } catch (IllegalArgumentException expected) {}
            check("Hạm đội A".equals(first.names().get(0)),"bad fleet does not replace saved slot");
            try { first.load(5); throw new AssertionError("sixth preset accepted"); } catch (IllegalArgumentException expected) {}
            first.delete(0); check(first.names().size()==4,"preset deleted");
        } finally { root.removeNode(); root.flush(); }
    }

    @SuppressWarnings("unchecked")
    private static void history() throws Exception {
        StorageManager storage=empty("battleship-history-pages-");
        for (int i=0;i<65;i++) check(storage.commitResult(record(storage,String.format(Locale.ROOT,"page-%03d",i),100000+i/3,i%2==0)),"history fixture committed");
        UserManager users=new UserManager(storage); MatchManager manager=new MatchManager(users,storage);
        DomainTests.Capture viewer=new DomainTests.Capture(storage.findByUsername("upgrade_a"),users,manager);
        try {
            Map<String,Object> query=new LinkedHashMap<>(); query.put("limit",20);
            Map<String,Object> first=users.matchHistory(viewer,query);
            List<Object> firstRows=(List<Object>)first.get("matches"); check(firstRows.size()==20 && Boolean.TRUE.equals(first.get("has_more")),"first page limited to 20");
            Set<String> seen=new HashSet<>();
            for (Object row:firstRows) seen.add((String)((Map<?,?>)row).get("match_id"));
            storage.commitResult(record(storage,"inserted-late",100005,true));
            Map<String,Object> page=first;
            while (Boolean.TRUE.equals(page.get("has_more"))) {
                query.put("cursor",page.get("next_cursor")); page=users.matchHistory(viewer,query);
                for (Object row:(List<?>)page.get("matches")) check(seen.add((String)((Map<?,?>)row).get("match_id")),"no duplicated row across pages");
            }
            check(seen.size()==65 && !seen.contains("inserted-late"),"all original rows exactly once; late insertion excluded");
            Map<String,Object> filter=new LinkedHashMap<>(); filter.put("limit",50); filter.put("result","WIN"); filter.put("opponent","UPGRADE_B"); filter.put("from_ms",100004L); filter.put("to_ms",100010L);
            List<Map<String,Object>> filtered=(List<Map<String,Object>>)users.matchHistory(viewer,filter).get("matches");
            check(!filtered.isEmpty(),"combined filters find records");
            for (Map<String,Object> row:filtered) check(Boolean.TRUE.equals(row.get("won")) && JsonUtil.getLong(row,"ended_at_ms",0)>=100004 && JsonUtil.getLong(row,"ended_at_ms",0)<=100010,"server applies all filters");
            filter.put("cursor",first.get("next_cursor")); DomainTests.expect("INVALID_MESSAGE",()->users.matchHistory(viewer,filter));
            filter.remove("cursor"); filter.put("opponent","nobody"); check(((List<?>)users.matchHistory(viewer,filter).get("matches")).isEmpty(),"empty history filter");
        } finally { manager.shutdown(); }
    }

    private static void latency() throws Exception {
        try (ServerSocket server=new ServerSocket(0)) {
            ExecutorService worker=Executors.newSingleThreadExecutor(); CountDownLatch measured=new CountDownLatch(1); AtomicLong latency=new AtomicLong();
            SocketClient client=new SocketClient(new ServerListener() {
                public void onConnected() {} public void onDisconnected(String reason) {} public void onMessageReceived(Message m) {}
                public void onLatencyMeasured(long ms) { latency.set(ms); measured.countDown(); }
            });
            Future<?> reply=worker.submit(()->{
                try (Socket socket=server.accept(); LineConnection wire=new LineConnection(socket)) {
                    Message heartbeat=Message.fromJson(wire.readLine());
                    Thread.sleep(120);
                    wire.send(new Message(MessageType.HEARTBEAT_RESP).requestId(heartbeat.getRequestId()));
                    measured.await(3,TimeUnit.SECONDS);
                } catch (Exception e) { throw new RuntimeException(e); }
            });
            try {
                check(client.connect("127.0.0.1",server.getLocalPort()),"latency test connected");
                check(measured.await(4,TimeUnit.SECONDS) && latency.get()>=100,"heartbeat correlated latency includes delayed reply"); reply.get(4,TimeUnit.SECONDS);
            } finally { client.disconnect("test done"); worker.shutdownNow(); }
        }
    }

    private static void recovery() throws Exception {
        for (String point:Arrays.asList("before-db","after-db")) {
            Path directory=DomainTests.empty("battleship-crash-"); StorageManager seed=new StorageManager(directory);
            seed.registerUser("upgrade_a","secret"); seed.registerUser("upgrade_b","secret");
            seed.findByUsername("upgrade_a").setTotalPoints(7); seed.saveUsers();
            seed.saveChat(1,2,"Tin nhắn phải còn","crash-chat");
            child(point,directory,"before-db".equals(point)?23:24);
            StorageManager restored=new StorageManager(directory);
            check(restored.getMatchRecords().size()==1 && restored.findByUsername("upgrade_b").getTotalPoints()==3,"crash result recovered");
            check(restored.findByUsername("upgrade_a").getTotalPoints()==4 && restored.findByUsername("upgrade_a").getMatchmakingBlockedUntil()>0,"penalty recovered");
            check(((List<?>)restored.chatHistory(1,2,0).get("messages")).size()==1,"chat survives result recovery");
            StorageManager twice=new StorageManager(directory);
            check(twice.findByUsername("upgrade_b").getTotalPoints()==3 && twice.findByUsername("upgrade_a").getTotalGames()==1,"recovery never awards twice");
            try (java.util.stream.Stream<Path> files=Files.list(directory.resolve("pending-results"))) { check(files.count()==0,"committed journal removed"); }
            Files.write(directory.resolve("pending-results/unpublished.tmp"),new byte[]{'{'});
            check(new StorageManager(directory).getMatchRecords().size()==1,"unpublished partial journal does not hide committed results");
        }
        Path failedDir=DomainTests.empty("battleship-journal-fault-");
        StorageManager noJournal=new StorageManager(failedDir) {
            protected void writeJournal(MatchRecord r)throws IOException { throw new IOException("journal disk fault"); }
        };
        noJournal.registerUser("upgrade_a","secret"); noJournal.registerUser("upgrade_b","secret");
        check(!noJournal.commitResult(record(noJournal,"not-durable",100000,true)) && noJournal.getPendingCount()==1 && noJournal.findByUsername("upgrade_a").getTotalPoints()==0,"journal failure stays pending without false success");
        Path corrupt=DomainTests.empty("battleship-journal-corrupt-"); StorageManager seed=new StorageManager(corrupt);
        seed.registerUser("upgrade_a","secret"); seed.registerUser("upgrade_b","secret"); child("before-db",corrupt,23);
        Path damaged;
        try (java.util.stream.Stream<Path> files=Files.list(corrupt.resolve("pending-results"))) { damaged=files.filter(p->p.toString().endsWith(".json")).findFirst().get(); }
        byte[] broken="{unfinished".getBytes(java.nio.charset.StandardCharsets.UTF_8); Files.write(damaged,broken);
        try { new StorageManager(corrupt); throw new AssertionError("corrupt journal silently ignored"); } catch (IllegalStateException expected) {}
        check(Arrays.equals(broken,Files.readAllBytes(damaged)),"corrupt journal retained for repair");
    }

    private static void pendingResultsOverTcp() throws Exception {
        DomainTests.FaultStorage storage=new DomainTests.FaultStorage(DomainTests.empty("battleship-result-notify-"));
        GameServer server=new GameServer(0,storage); Thread thread=PenaltyTests.start(server);
        try (SocketPeer a=new SocketPeer(server.getPort(),true); SocketPeer b=new SocketPeer(server.getPort(),true)) {
            a.login("pending_a");b.login("pending_b");String match=IntegrationTests.queuePair(a,b);
            Message start=IntegrationTests.start(a,b,match);int turn=JsonUtil.requireInt(start.getData(),"turn_id");
            SocketPeer shooter=JsonUtil.requireInt(start.getData(),"current_turn_player_id")==a.userId?a:b;
            SocketPeer target=shooter==a?b:a;int[] sizes={5,4,3,3,2};int shots=0;
            for (int y=0;y<5;y++) for (int x=0;x<sizes[y];x++) {
                shots++;if(shots==17)storage.fail=true;
                IntegrationTests.ok(shooter,IntegrationTests.match(MessageType.FIRE_REQ,match).put("turn_id",turn).put("x",x).put("y",y));
                if (shots<17) { turn=JsonUtil.requireInt(shooter.waitType(MessageType.TURN_CHANGE).getData(),"turn_id");target.waitType(MessageType.TURN_CHANGE); }
            }
            Message ended=shooter.waitType(MessageType.MATCH_END);target.waitType(MessageType.MATCH_END);
            check(!JsonUtil.getBoolean(ended.getData(),"storage_saved",true) && storage.getPendingCount()==1,"finished result reports pending storage");
            IntegrationTests.error(shooter,IntegrationTests.match(MessageType.REMATCH_REQ,match),"STORAGE_ERROR");
            storage.fail=false;
            Message saved=shooter.waitType(MessageType.RESULT_SAVED_NOTIFY);
            Map<String,Object> own=JsonUtil.getMap(JsonUtil.getMap(saved.getData(),"scores"),String.valueOf(shooter.userId));
            check(JsonUtil.requireInt(own,"points_awarded")==3 && storage.getMatchRecords().size()==1,"retry sends the actual award once over TCP");
        } finally { storage.fail=false;server.stop();thread.join(5000); }
    }

    private static void child(String mode,Path directory,int exitCode) throws Exception {
        List<String> args=new ArrayList<>(Arrays.asList(Paths.get(System.getProperty("java.home"),"bin","java").toString(),"-Dbattleship.placement_ms=600","-Dbattleship.heartbeat_ms=200","-Dbattleship.idle_ms=3000","-cp",System.getProperty("java.class.path"),UpgradeTests.class.getName(),mode));
        if (directory!=null) args.add(directory.toString());
        Process process=new ProcessBuilder(args).redirectErrorStream(true).start();
        if (!process.waitFor(20,TimeUnit.SECONDS)) { process.destroyForcibly(); throw new AssertionError("child timeout: "+mode); }
        StringBuilder output=new StringBuilder();
        try (BufferedReader reader=new BufferedReader(new InputStreamReader(process.getInputStream()))) { String line;while((line=reader.readLine())!=null)output.append(line).append('\n'); }
        check(process.exitValue()==exitCode,"child "+mode+" exit "+process.exitValue()+": "+output);
    }

    public static void main(String[] args) throws Exception {
        if ("placement".equals(args[0])) {
            GameServer server=new GameServer(0,DomainTests.empty("battleship-expiry-tcp-")); Thread thread=PenaltyTests.start(server);
            try (SocketPeer a=new SocketPeer(server.getPort(),true); SocketPeer b=new SocketPeer(server.getPort(),true);
                 SocketPeer c=new SocketPeer(server.getPort(),true); SocketPeer d=new SocketPeer(server.getPort(),true)) {
                a.login("upgrade_a"); b.login("upgrade_b"); String id=IntegrationTests.queuePair(a,b);
                c.login("upgrade_c"); d.login("upgrade_d"); String second=IntegrationTests.queuePair(c,d);
                Message battle=IntegrationTests.start(c,d,second);
                Message left=a.waitType(MessageType.ROOM_USER_LEFT); b.waitType(MessageType.ROOM_USER_LEFT);
                check(left.getMessage().contains("Hết thời gian") && server.getStorage().getMatchRecords().isEmpty(),"placement auto-cancels over TCP");
                SocketPeer shooter=JsonUtil.getInt(battle.getData(),"current_turn_player_id",0)==c.userId?c:d;
                IntegrationTests.ok(shooter,IntegrationTests.match(MessageType.FIRE_REQ,second).put("turn_id",JsonUtil.requireInt(battle.getData(),"turn_id")).put("x",9).put("y",9));
                check(!id.equals(IntegrationTests.queuePair(a,b)),"expiry releases both players to pair again");
            } finally { server.stop(); thread.join(5000); }
            return;
        }
        final String mode=args[0];
        StorageManager storage=new StorageManager(Paths.get(args[1])) {
            protected void writeSnapshot(List<Map<String,Object>> users,List<Object> records)throws IOException {
                if ("before-db".equals(mode) && !records.isEmpty()) Runtime.getRuntime().halt(23);
                super.writeSnapshot(users,records);
            }
            protected void completeJournal(String id)throws IOException {
                if ("after-db".equals(mode)) Runtime.getRuntime().halt(24);
                super.completeJournal(id);
            }
        };
        MatchRecord record=record(storage,"crash-match",System.currentTimeMillis(),false); record.setReason("SURRENDER");
        storage.commitResult(record);
        throw new AssertionError("crash hook not reached");
    }
}
