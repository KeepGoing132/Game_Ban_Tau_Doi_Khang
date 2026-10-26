package tests;

import common.config.GameConfig;
import common.model.*;
import common.protocol.*;
import server.manager.*;
import server.model.MatchSession;
import server.network.ClientHandler;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class DomainTests {
    static void check(boolean c,String m){TestRunner.check(c,m);}
    static class Capture extends ClientHandler {
        final List<Message> events=new CopyOnWriteArrayList<>();
        Capture(User u,UserManager users,MatchManager manager){super(null,users,null,manager);setUser(u);}
        public void sendMessage(Message m){events.add(Message.fromJson(m.toJson()));}
        public void reply(Message m){sendMessage(m);}
        public boolean isConnected(){return true;}
        public void close(){}
    }
    static class FaultStorage extends StorageManager {
        boolean fail;
        FaultStorage(Path path){super(path);}
        protected void writeSnapshot(List<Map<String,Object>> users,List<Object> matches)throws IOException{
            if(fail)throw new IOException("injected disk failure");super.writeSnapshot(users,matches);
        }
    }
    static List<Ship> ships(){List<Ship> ships=new ArrayList<>();int[] sizes={5,4,3,3,2};for(int y=0;y<5;y++){List<Coordinate> c=new ArrayList<>();for(int x=0;x<sizes[y];x++)c.add(new Coordinate(x,y));ships.add(new Ship("S"+y,sizes[y],c));}return ships;}
    static void expect(String code,Runnable runnable){try{runnable.run();}catch(DomainException e){check(e.code.equals(code),"expected "+code+", got "+e.code);return;}throw new AssertionError("did not reject "+code);}
    static Path empty(String prefix)throws Exception{Path p=Files.createTempDirectory(prefix);Files.write(p.resolve("users.json"),"[]".getBytes(StandardCharsets.UTF_8));return p;}
    public static void run()throws Exception{
        FaultStorage storage=new FaultStorage(empty("battleship-domain-"));User u1=storage.registerUser("one","secret"),u2=storage.registerUser("two","secret");UserManager users=new UserManager(storage);MatchManager manager=new MatchManager(users,storage);ScheduledThreadPoolExecutor scheduler=new ScheduledThreadPoolExecutor(1);
        AtomicLong ms=new AtomicLong(1000000),ns=new AtomicLong(1000000000);
        Capture a=new Capture(u1,users,manager),b=new Capture(u2,users,manager);
        try {
            MatchSession s=new MatchSession("clock-test","room",a,b,users,storage,scheduler,()->{},ms::get,ns::get,new Random(1));
            s.place(a,ships());List<Ship> invalid=ships();invalid.get(0).getCoordinates().set(1,new Coordinate(7,0));expect("INVALID_FLEET",()->s.place(a,invalid));s.ready(a,true);s.place(b,ships());s.ready(b,true);
            check(s.getState()==MatchSession.State.RUNNING,"valid board survives invalid replacement");int starts=(int)a.events.stream().filter(m->m.getType()==MessageType.MATCH_START).count();expect("INVALID_STATE",()->s.ready(a,true));check(starts==(int)a.events.stream().filter(m->m.getType()==MessageType.MATCH_START).count(),"single start");
            Capture current=s.getCurrentPlayer()==u1.getUserId()?a:b;int turn=s.getTurnId();long deadline=s.getDeadlineMs();
            ns.addAndGet(TimeUnit.MILLISECONDS.toNanos(GameConfig.TURN_TIMEOUT_MS)-1);ms.addAndGet(GameConfig.TURN_TIMEOUT_MS-1);
            s.fire(current,new Message(MessageType.FIRE_REQ),turn,new Coordinate(0,0));int changed=s.getTurnId();s.timeout(turn);check(s.getTurnId()==changed,"stale timer inert");
            long nextDeadline=s.getDeadlineMs();expect("ALREADY_SHOT",()->s.fire(current,new Message(MessageType.FIRE_REQ),changed,new Coordinate(0,0)));check(s.getDeadlineMs()==nextDeadline && s.getShotCount()==1,"bad shot does not mutate deadline/count");
            ns.addAndGet(TimeUnit.MILLISECONDS.toNanos(GameConfig.TURN_TIMEOUT_MS));ms.addAndGet(GameConfig.TURN_TIMEOUT_MS);
            expect("TURN_EXPIRED",()->s.fire(current,new Message(MessageType.FIRE_REQ),changed,new Coordinate(1,0)));int after=s.getTurnId();s.timeout(changed);check(s.getTurnId()==after && s.getShotCount()==1,"expired fire + timeout only one transition");
            int beforePoints=u2.getTotalPoints();storage.fail=true;
            ExecutorService race=Executors.newFixedThreadPool(2);CountDownLatch started=new CountDownLatch(2),go=new CountDownLatch(1);
            try{Future<?> one=race.submit(()->{started.countDown();try{go.await();}catch(InterruptedException e){throw new RuntimeException(e);}s.leave(a,"SURRENDER");});Future<?> two=race.submit(()->{started.countDown();try{go.await();}catch(InterruptedException e){throw new RuntimeException(e);}s.leave(a,"DISCONNECTED");});check(started.await(3,TimeUnit.SECONDS),"finish racers ready");go.countDown();one.get(3,TimeUnit.SECONDS);two.get(3,TimeUnit.SECONDS);}finally{race.shutdownNow();}
            check(storage.getPendingCount()==1 && storage.getMatchRecords().isEmpty() && u2.getTotalPoints()==beforePoints,"failed write leaves stats unchanged pending result");StorageManager beforeRetry=new StorageManager(storagePath(storage));check(beforeRetry.getMatchRecords().size()==1 && beforeRetry.findById(u2.getUserId()).getTotalPoints()==beforePoints+3,"restart after failed SQL write recovers durable journal");s.leave(a,"DISCONNECTED");check(storage.getPendingCount()==1,"duplicate finish no pending duplicate");storage.fail=false;check(storage.retryPending().size()==1,"retry commits result");check(u2.getTotalPoints()==beforePoints+3 && storage.getMatchRecords().size()==1,"retry awards once");check(storage.retryPending().isEmpty(),"second retry inert");
            StorageManager restart=new StorageManager(storagePath(storage));check(restart.getMatchRecords().size()==1,"failed-write retry survives restart");
            System.out.println("PASS domain: fake-clock deadline/stale timer/invalid board/once-only finish/storage fault retry");
        } finally {scheduler.shutdownNow();manager.shutdown();}
        Path corrupt=empty("battleship-corrupt-");Files.write(corrupt.resolve("users.json"),"{bad}".getBytes(StandardCharsets.UTF_8));byte[] original=Files.readAllBytes(corrupt.resolve("users.json"));boolean failed=false;try{new StorageManager(corrupt);}catch(IllegalStateException e){failed=true;}check(failed && Arrays.equals(original,Files.readAllBytes(corrupt.resolve("users.json"))) && !Files.exists(corrupt.resolve("snapshot.json")),"corruption fails closed preserves source");
        Path legacy=empty("battleship-legacy-");String legacyUser="[{\"userId\":42,\"username\":\"Legacy\",\"password\":\"oldpass\",\"totalPoints\":12,\"totalWins\":4,\"totalGames\":9}]";legacyUser=legacyUser.substring(0,legacyUser.length()-1)+",{\"userId\":43,\"username\":\"opponent\",\"password\":\"oldpass2\",\"totalPoints\":0,\"totalWins\":0,\"totalGames\":2}]";
        Files.write(legacy.resolve("matches.json"),"[{\"matchId\":\"legacy-match\",\"player1Id\":42,\"player1Name\":\"Legacy\",\"player2Id\":43,\"player2Name\":\"opponent\",\"winnerId\":42,\"winnerName\":\"Legacy\",\"timestamp\":12345,\"durationSeconds\":19}]".getBytes(StandardCharsets.UTF_8));
        Files.write(legacy.resolve("users.json"),legacyUser.getBytes(StandardCharsets.UTF_8));StorageManager imported=new StorageManager(legacy);User account=imported.findByUsername("LEGACY");check(account.getUserId()==42 && account.getTotalPoints()==12 && PasswordHasher.verify("oldpass",account.getPassword()),"legacy migration retains ID/statistics/password");check(legacyUser.equals(new String(Files.readAllBytes(legacy.resolve("users.json")),StandardCharsets.UTF_8)) && Files.exists(legacy.resolve("users.json.legacy.bak")),"legacy source backup unchanged");
        MatchRecord old=imported.getMatchRecords().get(0);check(old.getTimestamp()==12345 && old.getStartedAt()==0 && old.getLegacy(),"legacy timestamp preserved, missing times marked");
        Path conflict=empty("battleship-conflict-");Files.write(conflict.resolve("users.json"),legacyUser.getBytes(StandardCharsets.UTF_8));String wrongLoser=new String(Files.readAllBytes(legacy.resolve("matches.json")),StandardCharsets.UTF_8).replace("\"winnerId\":42","\"winnerId\":42,\"loserId\":42");Files.write(conflict.resolve("matches.json"),wrongLoser.getBytes(StandardCharsets.UTF_8));boolean conflicting=false;try{new StorageManager(conflict);}catch(IllegalStateException e){conflicting=true;}check(conflicting && !Files.exists(conflict.resolve("snapshot.json")),"conflicting legacy match rejected without overwrite");
        StorageManager ranked=new StorageManager(empty("battleship-ranking-"));String[] names={"rank_a","rank_b","rank_c","rank_d","rank_e","rank_f"};int[] points={10,10,10,10,8,0},wins={4,4,4,3,5,0},games={5,5,8,3,5,0};
        for(int i=0;i<names.length;i++){User u=ranked.registerUser(names[i],"secret");u.setTotalPoints(points[i]);u.setTotalWins(wins[i]);u.setTotalGames(games[i]);}
        List<Map<String,Object>> leaderboard=new UserManager(ranked).getLeaderboard();for(int i=0;i<names.length;i++)check(names[i].equals(leaderboard.get(i).get("username")),"ranking criterion/tiebreak "+i);check(((Number)leaderboard.get(5).get("win_rate")).doubleValue()==0,"zero-game win rate");
        System.out.println("PASS migration/corrupt JSON preservation");
    }
    private static Path storagePath(StorageManager storage)throws Exception{java.lang.reflect.Field f=StorageManager.class.getDeclaredField("directory");f.setAccessible(true);return (Path)f.get(storage);}
}
