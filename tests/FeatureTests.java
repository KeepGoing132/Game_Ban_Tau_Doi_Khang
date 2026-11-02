package tests;

import common.model.*;
import common.protocol.*;
import server.bot.BotBrain;
import server.manager.*;
import server.network.GameServer;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

public final class FeatureTests {
    private static void check(boolean condition,String message){TestRunner.check(condition,message);}
    public static void run()throws Exception {
        algorithms();databaseMigration();
        Path dir=DomainTests.empty("battleship-new-features-");GameServer server=new GameServer(0,dir);Thread thread=PenaltyTests.start(server);
        try(SocketPeer player=new SocketPeer(server.getPort(),true)) {
            IntegrationTests.error(player,new Message(MessageType.DELETE_ACCOUNT_REQ).put("password","secret"),"UNAUTHENTICATED");player.login("training");
            IntegrationTests.error(player,new Message(MessageType.BOT_MATCH_REQ).put("difficulty","IMPOSSIBLE"),"INVALID_MESSAGE");
            for(BotBrain.Difficulty level:BotBrain.Difficulty.values()) {
                player.clear();Message request=new Message(MessageType.BOT_MATCH_REQ).put("difficulty",level.name());IntegrationTests.ok(player,request);
                Message found=player.waitType(MessageType.MATCH_FOUND);String id=(String)found.getData().get("match_id");check(JsonUtil.getBoolean(found.getData(),"practice",false),"bot is explicitly practice");
                IntegrationTests.error(player,new Message(MessageType.DELETE_ACCOUNT_REQ).put("password","secret"),"INVALID_STATE");
                IntegrationTests.ok(player,IntegrationTests.match(MessageType.PLACE_SHIPS_REQ,id).put("ships",IntegrationTests.fleet()));IntegrationTests.ok(player,IntegrationTests.ready(id,true));
                player.waitType(MessageType.MATCH_START);
                // If human moves first, the real turn timeout hands the turn to the bot.
                Message shot=player.waitFor(m->m.getType()==MessageType.FIRE_RESULT && JsonUtil.getInt(m.getData(),"shooter_id",0)<0,common.config.GameConfig.TURN_TIMEOUT_MS+5000);
                Map<String,Object> c=JsonUtil.getMap(shot.getData(),"coordinate");check(JsonUtil.getInt(c,"x",-1)>=0&&JsonUtil.getInt(c,"x",-1)<10,"bot fires legal coordinates through server");
                IntegrationTests.ok(player,IntegrationTests.match(MessageType.SURRENDER_REQ,id));player.waitType(MessageType.MATCH_END);player.waitType(MessageType.ROOM_USER_LEFT);
                User human=server.getStorage().findByUsername("training");check(human.getTotalGames()==0&&human.getTotalPoints()==0&&human.getMatchmakingBlockedUntil()==0,"practice has no ranked stats or penalty");
            }
            User user=server.getStorage().findByUsername("training");String oldHash=user.getPassword();int oldId=user.getUserId();
            IntegrationTests.error(player,new Message(MessageType.DELETE_ACCOUNT_REQ).put("password","wrong"),"AUTH_FAILED");check(server.getStorage().findByUsername("training")!=null,"wrong password cannot delete account");
            IntegrationTests.ok(player,new Message(MessageType.DELETE_ACCOUNT_REQ).put("password","secret").put("user_id",999));
            IntegrationTests.error(player,new Message(MessageType.CREATE_ROOM_REQ),"UNAUTHENTICATED");
            check(server.getStorage().findByUsername("training")==null,"delete logs out and removes username");
            try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+dir.resolve("battleship.db"));PreparedStatement p=c.prepareStatement("SELECT username,password_hash,deleted FROM accounts WHERE user_id=?")) {
                p.setInt(1,oldId);try(ResultSet r=p.executeQuery()){check(r.next()&&r.getBoolean(3)&&r.getString(2).isEmpty()&&!"training".equals(r.getString(1)),"SQL contains only anonymous tombstone, no credentials");}
            }
            check(!new String(Files.readAllBytes(dir.resolve("battleship.db")),java.nio.charset.StandardCharsets.ISO_8859_1).contains(oldHash),"secure_delete removes old hash from freed SQLite pages");
            StorageManager restored=new StorageManager(dir);check(restored.findByUsername("training")==null,"deleted account stays deleted after restart");
            player.login("training");check(player.userId>oldId,"new account never inherits deleted stats or ID");
        }finally{server.stop();thread.join(5000);}
        System.out.println("PASS features: SQLite migration, credential deletion/auth guards/restart, 3 bot levels over TCP, practice cleanup");
    }
    private static void databaseMigration()throws Exception {
        Path dir=DomainTests.empty("battleship-json-to-sql-");
        String hash=PasswordHasher.hash("migration-secret");Map<String,Object> u=new LinkedHashMap<>();u.put("userId",87);u.put("username","migrated");u.put("passwordHash",hash);u.put("totalPoints",8);u.put("totalWins",4);u.put("totalGames",9);u.put("matchmakingBlockedUntil",System.currentTimeMillis()+60000);
        Map<String,Object> snapshot=new LinkedHashMap<>();snapshot.put("version",1);snapshot.put("users",Arrays.asList(u));snapshot.put("matches",Collections.emptyList());byte[] old=JsonUtil.toJson(snapshot).getBytes(java.nio.charset.StandardCharsets.UTF_8);Files.write(dir.resolve("snapshot.json"),old);
        StorageManager storage=new StorageManager(dir);User account=storage.findByUsername("migrated");check(account.getTotalPoints()==8&&account.getUserId()==87&&PasswordHasher.verify("migration-secret",account.getPassword()),"JSON import preserves statistics and hash");check(Arrays.equals(old,Files.readAllBytes(dir.resolve("snapshot.json"))),"source preserved during migration");
        storage.deleteAccount(account,"migration-secret");StorageManager next=new StorageManager(dir);check(next.findByUsername("migrated")==null,"old snapshot does not resurrect deleted account");
    }
    private static void algorithms(){
        int[] total=new int[3];
        for(BotBrain.Difficulty level:BotBrain.Difficulty.values())for(int seed=0;seed<200;seed++) {
            Board board=new Board();board.setShips(BotBrain.fleet(new Random(seed)));check(board.validateAllShips(),"bot fleet valid");BotBrain brain=new BotBrain(level,new Random(seed+700));Set<Coordinate> shots=new HashSet<>();int hits=0;
            while(hits<17){Coordinate c=brain.choose();check(shots.add(c),"bot never repeats a shot");ShotResult result=board.receiveShot(c);if(result!=ShotResult.MISS)hits++;brain.observe(c,result,result==ShotResult.SUNK?board.getShipAt(c).getCoordinates():null);check(shots.size()<=100,"algorithm finishes");}total[level.ordinal()]+=shots.size();
        }
        check(total[1]<total[0]&&total[2]<total[1],"measured difficulty improves EASY > MEDIUM > HARD");
        System.out.println("PASS bot algorithms: 200 seeded fleets per level; average shots "+total[0]/200.0+" / "+total[1]/200.0+" / "+total[2]/200.0);
    }
}
