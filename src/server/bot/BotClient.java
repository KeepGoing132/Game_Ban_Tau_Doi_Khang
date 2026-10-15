package server.bot;

import common.model.*;
import common.protocol.*;
import server.manager.*;
import server.model.MatchSession;
import server.network.ClientHandler;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Virtual connection participating in the same authoritative match state machine. */
public final class BotClient extends ClientHandler {
    private static final AtomicInteger IDS=new AtomicInteger(-100);
    private final MatchManager matches;
    private final UserManager users;
    private final BotBrain.Difficulty difficulty;
    private BotBrain brain;
    private ScheduledFuture<?> shot;
    public BotClient(UserManager users,MatchManager matches,BotBrain.Difficulty difficulty) {
        super(null,users,null,matches);this.users=users;this.matches=matches;this.difficulty=difficulty;
        setUser(new User(IDS.getAndDecrement(),difficulty==BotBrain.Difficulty.EASY?"Máy • Dễ":difficulty==BotBrain.Difficulty.MEDIUM?"Máy • Vừa":"Máy • Khó",""));
    }
    @Override public boolean isConnected(){return true;}
    @Override public void close(){cancelShot();}
    @Override public void reply(Message m){sendMessage(m);}
    @Override public void sendMessage(Message message) {
        if(message==null)return;Message copy=Message.fromJson(message.toJson());
        try{matches.scheduler().execute(()->receive(copy));}catch(RejectedExecutionException ignored){}
    }
    public void prepare(MatchSession session) {
        synchronized(users.getRegistryLock()) {
            if(matches.getSession(this)!=session)return;cancelShot();brain=new BotBrain(difficulty,new Random());
            session.place(this,BotBrain.fleet(new Random()));session.ready(this,true);
        }
    }
    public void cancelShot(){if(shot!=null){shot.cancel(false);shot=null;}}
    private void receive(Message m) {
        synchronized(users.getRegistryLock()) {
            MatchSession session=matches.getSession(this);if(session==null)return;
            if(m.getType()==MessageType.REMATCH_START){prepare(session);return;}
            if(!session.getMatchId().equals(m.getData().get("match_id")))return;
            switch(m.getType()) {
                case FIRE_RESULT:
                    if(brain!=null && JsonUtil.getInt(m.getData(),"shooter_id",0)==getUser().getUserId()) {
                        Map<String,Object> c=JsonUtil.getMap(m.getData(),"coordinate");List<Coordinate> sunk=new ArrayList<>();List<Object> raw=JsonUtil.getList(m.getData(),"sunk_ship_coords");
                        if(raw!=null)for(Object o:raw){@SuppressWarnings("unchecked") Map<String,Object> p=(Map<String,Object>)o;sunk.add(new Coordinate(JsonUtil.getInt(p,"x",0),JsonUtil.getInt(p,"y",0)));}
                        brain.observe(new Coordinate(JsonUtil.getInt(c,"x",0),JsonUtil.getInt(c,"y",0)),ShotResult.valueOf((String)m.getData().get("result")),sunk);
                    }break;
                case MATCH_START:case TURN_CHANGE:
                    cancelShot();int turn=JsonUtil.getInt(m.getData(),"turn_id",0);
                    if(JsonUtil.getInt(m.getData(),"current_turn_player_id",0)==getUser().getUserId()) {
                        long delay=Math.min(1450,Math.max(80,common.config.GameConfig.TURN_TIMEOUT_MS/3));
                        shot=matches.scheduler().schedule(()->{
                            synchronized(users.getRegistryLock()) {
                                if(matches.getSession(this)!=session || session.getState()!=MatchSession.State.RUNNING || session.getTurnId()!=turn || session.getCurrentPlayer()!=getUser().getUserId())return;
                                Coordinate c=brain.choose();try{session.fire(this,new Message(MessageType.FIRE_REQ),turn,c);}catch(DomainException ignored){}
                            }
                        },delay,TimeUnit.MILLISECONDS);
                    }break;
                case REMATCH_NOTIFY:
                    try{matches.handleRematch(this,new Message(MessageType.REMATCH_CONFIRM_REQ).put("match_id",session.getMatchId()),true);}catch(DomainException ignored){}break;
                case MATCH_END:case ROOM_USER_LEFT:cancelShot();break;
                default:break;
            }
        }
    }
}
