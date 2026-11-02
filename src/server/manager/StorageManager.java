package server.manager;

import common.model.*;
import common.config.GameConfig;
import common.protocol.JsonUtil;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.LongSupplier;

public class StorageManager {
    private final Path directory;
    private final Path snapshot;
    private SqliteStore database;
    private ResultJournal journal;
    private final Map<String, User> userByUsername = new LinkedHashMap<>();
    private final Map<Integer, User> userById = new LinkedHashMap<>();
    private final Map<String, MatchRecord> matches = new LinkedHashMap<>();
    private final Map<String, MatchRecord> pending = new LinkedHashMap<>();
    private int nextUserId = 1;
    private final LongSupplier clock;

    public StorageManager() { this(Paths.get(System.getProperty("battleship.data_dir", "data"))); }
    public StorageManager(Path directory) { this(directory, System::currentTimeMillis); }
    public StorageManager(Path directory, LongSupplier clock) {
        this.clock = clock;
        this.directory = directory.toAbsolutePath(); this.snapshot = this.directory.resolve("snapshot.json");
        try {
            Files.createDirectories(this.directory);
            database=new SqliteStore(this.directory.resolve("battleship.db"));
            if(database.initialized()) {
                Map<String,Object> root=database.read();loadUsers(requiredList(root,"users"),false);loadMatches(requiredList(root,"matches"));
            } else if (Files.exists(snapshot)) {
                Map<String,Object> root = JsonUtil.parseObject(read(snapshot));
                if (JsonUtil.requireInt(root, "version") != 1) throw new IOException("Unsupported snapshot version");
                loadUsers(requiredList(root, "users"), false);
                loadMatches(requiredList(root, "matches"));
                writeSnapshot(userMaps(),matchMaps());
            } else {
                Path users = this.directory.resolve("users.json");
                Path oldMatches = this.directory.resolve("matches.json");
                if (Files.exists(users)) loadUsers(JsonUtil.parseList(read(users)), true);
                else if (Files.exists(oldMatches)) throw new IOException("matches.json exists without users.json");
                else initSamples();
                if (Files.exists(oldMatches)) loadMatches(JsonUtil.parseList(read(oldMatches)));
                for (Path source : Arrays.asList(users, oldMatches)) {
                    if (Files.exists(source)) Files.copy(source, this.directory.resolve(source.getFileName() + ".legacy.bak"), StandardCopyOption.REPLACE_EXISTING);
                }
                writeSnapshot(userMaps(), matchMaps());
            }
            journal = new ResultJournal(this.directory.resolve("pending-results"));
            recoverPendingResults();
        } catch (Exception e) { throw new IllegalStateException("Storage initialization failed; source files preserved: " + e.getMessage(), e); }
    }
    private void recoverPendingResults() throws IOException {
        for (Map<String,Object> payload : journal.pending()) {
            String id=JsonUtil.getString(payload,"matchId","");
            if (matches.containsKey(id)) { clearJournal(id); continue; }
            loadMatches(Collections.<Object>singletonList(payload));
            MatchRecord record=matches.remove(id);
            pending.put(id,record);
        }
        retryPending();
    }
    protected void writeJournal(MatchRecord record) throws IOException {
        journal.stage(record.getMatchId(),JsonUtil.parseObject(JsonUtil.toJson(record)));
    }
    protected void completeJournal(String matchId) throws IOException { journal.complete(matchId); }
    private void clearJournal(String matchId) {
        try { completeJournal(matchId); }
        catch (IOException e) { System.err.println("[Journal] Committed result retained for later cleanup: " + matchId); }
    }
    private static String read(Path path) throws IOException { return new String(Files.readAllBytes(path), StandardCharsets.UTF_8); }
    private static List<Object> requiredList(Map<String,Object> root, String key) {
        List<Object> list = JsonUtil.getList(root,key);
        if (list == null) throw new IllegalArgumentException("Missing " + key); return list;
    }
    @SuppressWarnings("unchecked")
    private void loadUsers(List<Object> list, boolean legacy) {
        for (Object object : list) {
            if (!(object instanceof Map)) throw new IllegalArgumentException("Invalid account record");
            Map<String,Object> m = (Map<String,Object>) object;
            User u = new User(); u.setUserId(JsonUtil.requireInt(m,"userId"));
            String name = JsonUtil.getString(m,"username","").trim().toLowerCase(Locale.ROOT);
            if (name.isEmpty() || u.getUserId() <= 0 || userById.containsKey(u.getUserId()) || userByUsername.containsKey(name)) throw new IllegalArgumentException("Duplicate/invalid account");
            u.setUsername(name);u.setDeleted(JsonUtil.getBoolean(m,"deleted",false));
            String password = JsonUtil.getString(m,legacy ? "password" : "passwordHash",null);
            if (password == null) throw new IllegalArgumentException("Missing credential");
            u.setPassword(legacy ? PasswordHasher.hash(password) : password);
            if (!legacy && !u.isDeleted() && !password.startsWith("pbkdf2-sha256$v1$")) throw new IllegalArgumentException("Invalid credential format");
            u.setTotalPoints(JsonUtil.requireInt(m,"totalPoints")); u.setTotalWins(JsonUtil.requireInt(m,"totalWins")); u.setTotalGames(JsonUtil.requireInt(m,"totalGames"));
            u.setMatchmakingBlockedUntil(longValue(m,"matchmakingBlockedUntil"));
            if(u.getMatchmakingBlockedUntil()<0)throw new IllegalArgumentException("Invalid matchmaking penalty");
            if (u.getTotalPoints() < 0 || u.getTotalWins() < 0 || u.getTotalGames() < u.getTotalWins()) throw new IllegalArgumentException("Invalid statistics");
            userById.put(u.getUserId(),u); if(!u.isDeleted())userByUsername.put(name,u); nextUserId=Math.max(nextUserId,u.getUserId()+1);
        }
    }
    @SuppressWarnings("unchecked")
    private void loadMatches(List<Object> list) {
        for (Object object : list) {
            if (!(object instanceof Map)) throw new IllegalArgumentException("Invalid match record");
            Map<String,Object> m = (Map<String,Object>) object;
            MatchRecord r = new MatchRecord();
            r.setMatchId(JsonUtil.getString(m,"matchId",""));
            if (r.getMatchId().isEmpty() || matches.containsKey(r.getMatchId())) throw new IllegalArgumentException("Duplicate/invalid matchId");
            r.setPlayer1Id(JsonUtil.requireInt(m,"player1Id")); r.setPlayer2Id(JsonUtil.requireInt(m,"player2Id"));
            r.setPlayer1Name(JsonUtil.getString(m,"player1Name","")); r.setPlayer2Name(JsonUtil.getString(m,"player2Name",""));
            r.setWinnerId(JsonUtil.requireInt(m,"winnerId")); r.setWinnerName(JsonUtil.getString(m,"winnerName",""));
            if (!userById.containsKey(r.getPlayer1Id()) || !userById.containsKey(r.getPlayer2Id()) || r.getPlayer1Id()==r.getPlayer2Id() || (r.getWinnerId()!=r.getPlayer1Id() && r.getWinnerId()!=r.getPlayer2Id())) throw new IllegalArgumentException("Conflicting match record");
            r.setLoserId(JsonUtil.getInt(m,"loserId",r.getWinnerId()==r.getPlayer1Id()?r.getPlayer2Id():r.getPlayer1Id()));
            if(r.getLoserId()!=(r.getWinnerId()==r.getPlayer1Id()?r.getPlayer2Id():r.getPlayer1Id()))throw new IllegalArgumentException("Conflicting loser in match record");
            r.setLoserName(JsonUtil.getString(m,"loserName",userById.get(r.getLoserId()).getUsername()));
            r.setDurationSeconds(longValue(m,"durationSeconds")); r.setTimestamp(longValue(m,"timestamp"));
            r.setStartedAt(longValue(m,"startedAt")); r.setEndedAt(longValue(m,"endedAt"));
            r.setPlayer1Shots(JsonUtil.getInt(m,"player1Shots",0)); r.setPlayer2Shots(JsonUtil.getInt(m,"player2Shots",0));
            r.setPlayer1Points(JsonUtil.getInt(m,"player1Points",r.getWinnerId()==r.getPlayer1Id()?3:0)); r.setPlayer2Points(JsonUtil.getInt(m,"player2Points",r.getWinnerId()==r.getPlayer2Id()?3:0));
            r.setReason(JsonUtil.getString(m,"reason","LEGACY_UNKNOWN")); r.setLegacy(!m.containsKey("startedAt") || JsonUtil.getBoolean(m,"legacy",false));
            r.setMatchmakingBlockedUntil(longValue(m,"matchmakingBlockedUntil"));
            r.setPlayer1Ships(fleetList(m,"player1Ships"));r.setPlayer2Ships(fleetList(m,"player2Ships"));r.setShotLog(shotList(m,"shotLog"));
            matches.put(r.getMatchId(),r);
        }
    }
    @SuppressWarnings("unchecked")
    private static List<Map<String,Object>> fleetList(Map<String,Object> m,String key) {
        List<Map<String,Object>> fleet=new ArrayList<>();
        List<Object> raw=JsonUtil.getList(m,key);
        if(raw==null)return fleet;
        for(Object item:raw) {
            if(!(item instanceof Map))continue;
            Map<String,Object> ship=(Map<String,Object>)item;
            String id=JsonUtil.getString(ship,"id","");int size=JsonUtil.getInt(ship,"size",0);
            List<Map<String,Object>> coords=cells(JsonUtil.getList(ship,"coordinates"));
            if(id.isEmpty() || id.length()>64 || size<2 || size>5 || coords.size()!=size)continue;
            Map<String,Object> entry=new LinkedHashMap<>();
            entry.put("id",id);entry.put("size",size);entry.put("coordinates",coords);fleet.add(entry);
        }
        return fleet;
    }
    @SuppressWarnings("unchecked")
    private static List<Map<String,Object>> cells(List<Object> raw) {
        List<Map<String,Object>> list=new ArrayList<>();
        if(raw==null)return list;
        for(Object item:raw) {
            if(!(item instanceof Map))continue;
            Map<String,Object> cell=(Map<String,Object>)item;
            Object x=cell.get("x"),y=cell.get("y");
            if(!(x instanceof Number) || !(y instanceof Number))continue;
            int cx=((Number)x).intValue(),cy=((Number)y).intValue();
            if(cx<0 || cx>9 || cy<0 || cy>9)continue;
            Map<String,Object> point=new LinkedHashMap<>();point.put("x",cx);point.put("y",cy);list.add(point);
        }
        return list;
    }
    @SuppressWarnings("unchecked")
    private static List<Map<String,Object>> shotList(Map<String,Object> m,String key) {
        List<Map<String,Object>> log=new ArrayList<>();
        List<Object> raw=JsonUtil.getList(m,key);
        if(raw==null)return log;
        for(Object item:raw) {
            if(!(item instanceof Map))continue;
            Map<String,Object> shot=(Map<String,Object>)item;
            Object x=shot.get("x"),y=shot.get("y");
            if(!(x instanceof Number) || !(y instanceof Number))continue;
            Map<String,Object> entry=new LinkedHashMap<>();
            entry.put("turn_id",JsonUtil.getInt(shot,"turn_id",0));
            entry.put("shooter_id",JsonUtil.getInt(shot,"shooter_id",0));
            entry.put("x",((Number)x).intValue());entry.put("y",((Number)y).intValue());
            entry.put("result",JsonUtil.getString(shot,"result","MISS"));
            List<Map<String,Object>> sunk=cells(JsonUtil.getList(shot,"sunk_ship_coords"));
            if(!sunk.isEmpty()) {entry.put("sunk_ship_coords",sunk);entry.put("sunk_ship_name",JsonUtil.getString(shot,"sunk_ship_name",""));}
            log.add(entry);
        }
        return log;
    }
    private static long longValue(Map<String,Object> m,String key) { Object v=m.get(key); return v instanceof Number?((Number)v).longValue():0; }
    private void initSamples() {
        String[] names={"tuan_duong","ngoc_bao","thanh_hai","dang_khoa","player1","player2"};
        int[] points={18,21,9,15,6,0}, wins={6,7,3,5,2,0}, games={9,9,8,8,4,2};
        for(int i=0;i<names.length;i++) { User u=new User(nextUserId++,names[i],PasswordHasher.hash("123456")); u.setTotalPoints(points[i]);u.setTotalWins(wins[i]);u.setTotalGames(games[i]);userById.put(u.getUserId(),u);userByUsername.put(names[i],u); }
    }
    private Map<String,Object> userMap(User u) {
        Map<String,Object> m=new LinkedHashMap<>();m.put("userId",u.getUserId());m.put("username",u.getUsername());m.put("passwordHash",u.getPassword());m.put("totalPoints",u.getTotalPoints());m.put("totalWins",u.getTotalWins());m.put("totalGames",u.getTotalGames());m.put("matchmakingBlockedUntil",u.getMatchmakingBlockedUntil());m.put("deleted",u.isDeleted()); return m;
    }
    private List<Map<String,Object>> userMaps() { List<Map<String,Object>> list=new ArrayList<>();for(User u:userById.values())list.add(userMap(u));return list; }
    private List<Object> matchMaps() { List<Object> list=new ArrayList<>();for(MatchRecord r:matches.values())list.add(JsonUtil.parseObject(JsonUtil.toJson(r)));return list; }
    protected void writeSnapshot(List<Map<String,Object>> users, List<Object> records) throws IOException {
        database.write(users,records);
    }
    public synchronized boolean commitResult(MatchRecord record) {
        if(matches.containsKey(record.getMatchId())) { clearJournal(record.getMatchId()); return true; }
        MatchRecord queued = pending.get(record.getMatchId());
        if(queued!=null)record=queued;
        User loser = userById.get(record.getLoserId());
        boolean forfeit = "SURRENDER".equals(record.getReason()) || "DISCONNECTED".equals(record.getReason());
        int loserPoints = forfeit ? -Math.min(GameConfig.FORFEIT_PENALTY_POINTS,loser.getTotalPoints()) : GameConfig.LOSE_POINTS;
        if(forfeit && record.getMatchmakingBlockedUntil()==0)
            record.setMatchmakingBlockedUntil((record.getEndedAt()>0?record.getEndedAt():clock.getAsLong())+GameConfig.MATCHMAKING_PENALTY_MS);
        record.setPlayer1Points(record.getWinnerId()==record.getPlayer1Id()?GameConfig.WIN_POINTS:loserPoints);
        record.setPlayer2Points(record.getWinnerId()==record.getPlayer2Id()?GameConfig.WIN_POINTS:loserPoints);
        pending.putIfAbsent(record.getMatchId(),record);
        try { writeJournal(record); }
        catch (IOException e) { System.err.println("[Journal] Result remains in RAM, not yet durable: " + record.getMatchId()); return false; }
        List<Map<String,Object>> users=userMaps();
        for(Map<String,Object> m:users) {
            int id=(Integer)m.get("userId");
            if(id==record.getPlayer1Id() || id==record.getPlayer2Id()) {
                m.put("totalGames",(Integer)m.get("totalGames")+1);
                if(id==record.getWinnerId()) {m.put("totalWins",(Integer)m.get("totalWins")+1);m.put("totalPoints",(Integer)m.get("totalPoints")+GameConfig.WIN_POINTS);}
                else {
                    m.put("totalPoints",(Integer)m.get("totalPoints")+loserPoints);
                    m.put("matchmakingBlockedUntil",Math.max(loser.getMatchmakingBlockedUntil(),record.getMatchmakingBlockedUntil()));
                }
            }
        }
        List<Object> records=matchMaps();records.add(JsonUtil.parseObject(JsonUtil.toJson(record)));
        try { writeSnapshot(users,records); }
        catch(IOException e) { System.err.println("[Storage] Pending result " + record.getMatchId() + ": " + e.getMessage());return false; }
        userById.get(record.getWinnerId()).addWin(GameConfig.WIN_POINTS);loser.addLoss(loserPoints);
        loser.setMatchmakingBlockedUntil(Math.max(loser.getMatchmakingBlockedUntil(),record.getMatchmakingBlockedUntil()));
        matches.put(record.getMatchId(),record);pending.remove(record.getMatchId());clearJournal(record.getMatchId());return true;
    }
    public synchronized void changePassword(User user, String oldPassword, String newPassword) {
        if (user == null || user.isDeleted() || !PasswordHasher.verify(oldPassword, user.getPassword()))
            throw new DomainException("AUTH_FAILED", "Mật khẩu hiện tại không đúng");
        if (newPassword == null || newPassword.trim().isEmpty() || newPassword.length() > 128)
            throw new DomainException("INVALID_MESSAGE", "Mật khẩu mới cần từ 1 đến 128 ký tự, không chỉ gồm khoảng trắng");

        String hash = PasswordHasher.hash(newPassword);
        List<Map<String,Object>> staged = userMaps();
        for (Map<String,Object> account : staged)
            if (((Number) account.get("userId")).intValue() == user.getUserId())
                account.put("passwordHash", hash);
        try {
            writeSnapshot(staged, matchMaps());
        } catch (IOException e) {
            throw new DomainException("STORAGE_ERROR", "Chưa lưu được mật khẩu mới. Hãy thử lại.");
        }
        user.setPassword(hash);
    }

    public synchronized void deleteAccount(User user,String password) {
        if(user==null || user.isDeleted() || !PasswordHasher.verify(password,user.getPassword()))throw new DomainException("AUTH_FAILED","Mật khẩu không đúng");
        for(MatchRecord r:pending.values())if(r.getPlayer1Id()==user.getUserId() || r.getPlayer2Id()==user.getUserId())throw new DomainException("STORAGE_ERROR","Hãy chờ lưu kết quả trước khi xóa tài khoản");
        String alias="[deleted_"+user.getUserId()+"]";
        List<Map<String,Object>> staged=userMaps();for(Map<String,Object> m:staged)if(((Number)m.get("userId")).intValue()==user.getUserId()){m.put("username",alias);m.put("passwordHash","");m.put("deleted",true);}
        List<Object> records=matchMaps();
        for(Object o:records){@SuppressWarnings("unchecked") Map<String,Object> m=(Map<String,Object>)o;for(String role:Arrays.asList("player1","player2","winner","loser"))if(((Number)m.get(role+"Id")).intValue()==user.getUserId())m.put(role+"Name",alias);}
        try{writeSnapshot(staged,records);}catch(IOException e){throw new DomainException("STORAGE_ERROR","Không thể xóa tài khoản. Vui lòng thử lại.");}
        userByUsername.remove(user.getUsername());user.setUsername(alias);user.setPassword("");user.setDeleted(true);
        matches.clear();loadMatches(records);
    }
    public synchronized List<MatchRecord> retryPending() {
        List<MatchRecord> saved=new ArrayList<>();for(MatchRecord r:new ArrayList<>(pending.values()))if(commitResult(r))saved.add(r);return saved;
    }
    public synchronized void saveUsers() { try {writeSnapshot(userMaps(),matchMaps());}catch(IOException e){throw new DomainException("STORAGE_ERROR",e.getMessage());} }
    public synchronized User findByUsername(String name) {return name==null?null:userByUsername.get(name.trim().toLowerCase(Locale.ROOT));}
    public synchronized User findById(int id) {return userById.get(id);}
    private User chatAccount(int id,boolean allowDeleted) {
        User u=userById.get(id);
        if(u==null || id<=0 || (!allowDeleted && u.isDeleted()))throw new DomainException("USER_NOT_FOUND","Tài khoản không còn tồn tại");
        return u;
    }
    public synchronized Map<String,Object> saveChat(int sender,int recipient,String body,String clientId) {
        chatAccount(sender,false);chatAccount(recipient,false);
        if(sender==recipient)throw new DomainException("INVALID_MESSAGE","Hãy chọn một người chơi khác");
        if(body==null || body.trim().isEmpty() || body.length()>500 || clientId==null || !clientId.matches("[A-Za-z0-9_-]{1,64}"))throw new DomainException("INVALID_MESSAGE","Tin nhắn cần từ 1 đến 500 ký tự");
        for(int i=0;i<body.length();i++){char c=body.charAt(i);if((Character.isISOControl(c)&&c!='\n'&&c!='\t') || (Character.isHighSurrogate(c) && (i+1>=body.length() || !Character.isLowSurrogate(body.charAt(++i)))) || Character.isLowSurrogate(c))throw new DomainException("INVALID_MESSAGE","Tin nhắn chứa ký tự không hợp lệ");}
        try{return database.saveChat(sender,recipient,body.trim(),clientId,clock.getAsLong());}
        catch(java.sql.SQLException e){throw new DomainException("STORAGE_ERROR","Chưa lưu được tin nhắn. Hãy thử gửi lại.");}
    }
    public synchronized Map<String,Object> chatHistory(int user,int peer,long before) {
        chatAccount(user,false);User target=chatAccount(peer,true);
        if(user==peer || before<0)throw new DomainException("INVALID_MESSAGE","Cuộc trò chuyện không hợp lệ");
        try {
            List<Map<String,Object>> rows=database.chatHistory(user,peer,before);boolean more=rows.size()>20;if(more)rows.remove(rows.size()-1);Collections.reverse(rows);
            Map<String,Object> result=new LinkedHashMap<>();result.put("peer_id",peer);result.put("peer_name",target.isDeleted()?"Tài khoản đã xóa":target.getUsername());result.put("available",!target.isDeleted());result.put("messages",rows);result.put("has_more",more);result.put("before_id",before);return result;
        }catch(java.sql.SQLException e){throw new DomainException("STORAGE_ERROR","Không tải được lịch sử trò chuyện");}
    }
    public synchronized List<Map<String,Object>> chatConversations(int user) {
        chatAccount(user,false);
        try{return database.chatConversations(user);}catch(java.sql.SQLException e){throw new DomainException("STORAGE_ERROR","Không tải được danh sách trò chuyện");}
    }
    public synchronized User registerUser(String name,String password) {
        name=name.trim().toLowerCase(Locale.ROOT);
        if(userByUsername.containsKey(name))throw new DomainException("INVALID_STATE");
        User u=new User(nextUserId,name,PasswordHasher.hash(password));
        List<Map<String,Object>> users=userMaps();users.add(userMap(u));
        try {writeSnapshot(users,matchMaps());}catch(IOException e){throw new DomainException("STORAGE_ERROR",e.getMessage());}
        nextUserId++;userByUsername.put(name,u);userById.put(u.getUserId(),u);return u;
    }
    public synchronized List<User> getAllUsers() {List<User> active=new ArrayList<>();for(User u:userById.values())if(!u.isDeleted())active.add(u);return active;}
    public synchronized List<MatchRecord> getMatchRecords() {return new ArrayList<>(matches.values());}
    public synchronized MatchRecord findMatch(String matchId) {return matchId==null?null:matches.get(matchId);}
    public synchronized int getPendingCount() {return pending.size();}
    public synchronized Map<String,Object> historyPage(int userId, HistoryQuery query) {
        try { return database.history(userId,query); }
        catch (java.sql.SQLException e) { throw new DomainException("STORAGE_ERROR","Không tải được lịch sử. Hãy thử lại."); }
    }

    public synchronized List<MatchRecord> historyFor(int userId,final int limit) {
        List<MatchRecord> mine=new ArrayList<>();
        for(MatchRecord r:matches.values())if(r.getPlayer1Id()==userId || r.getPlayer2Id()==userId)mine.add(r);
        Collections.sort(mine,new Comparator<MatchRecord>() {
            public int compare(MatchRecord a,MatchRecord b) {
                long left=a.getEndedAt()>0?a.getEndedAt():a.getTimestamp(), right=b.getEndedAt()>0?b.getEndedAt():b.getTimestamp();
                return Long.compare(right,left);
            }
        });
        return mine.size()>limit?new ArrayList<>(mine.subList(0,limit)):mine;
    }

    public synchronized Map<String,Object> penaltyInfo(User user) {
        long until=user.getMatchmakingBlockedUntil();boolean awaitingSave=false;
        for(MatchRecord r:pending.values())if(r.getLoserId()==user.getUserId() && r.getMatchmakingBlockedUntil()>0) {
            until=Math.max(until,r.getMatchmakingBlockedUntil());awaitingSave=true;
        }
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("matchmaking_blocked_until",until);
        result.put("matchmaking_wait_ms",Math.max(0,until-clock.getAsLong()));
        result.put("penalty_pending",awaitingSave);
        return result;
    }
}
