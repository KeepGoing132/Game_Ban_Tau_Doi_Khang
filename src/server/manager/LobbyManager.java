package server.manager;

import common.config.GameConfig;
import common.protocol.*;
import server.model.*;
import server.network.ClientHandler;
import java.util.*;
import java.util.concurrent.*;

public class LobbyManager {
    private final UserManager users;
    private final MatchManager matches;
    private final Object lock;
    private final Map<String,Room> rooms=new HashMap<>();
    private final Map<ClientHandler,Room> membership=new HashMap<>();
    private final Deque<ClientHandler> queue=new ArrayDeque<>();
    private final Map<String,Invitation> invitations=new HashMap<>();
    private static final class Invitation {
        final String id=UUID.randomUUID().toString();final ClientHandler sender,receiver;final long expires=System.currentTimeMillis()+GameConfig.INVITE_TIMEOUT_MS;
        ScheduledFuture<?> timeout;
        Invitation(ClientHandler sender,ClientHandler receiver){this.sender=sender;this.receiver=receiver;}
    }
    public LobbyManager(UserManager users,MatchManager matches){this.users=users;this.matches=matches;lock=users.getRegistryLock();matches.setLobby(this);}
    private boolean online(ClientHandler c){return c!=null && c.isConnected() && c.getUser()!=null && users.getClientById(c.getUser().getUserId())==c;}
    private boolean free(ClientHandler c){return online(c) && !membership.containsKey(c) && !queue.contains(c) && matches.getSession(c)==null && "IDLE".equals(c.getUser().getPhase());}
    private void requireFree(ClientHandler c){users.requireCanMatch(c);if(!free(c))throw new DomainException("INVALID_STATE","Bạn đang ở hàng chờ/phòng/trận khác");}
    private String code(){String code;do{code=UUID.randomUUID().toString().replace("-","").substring(0,6).toUpperCase(Locale.ROOT);}while(rooms.containsKey(code));return code;}
    public void startBotMatch(ClientHandler human,String difficulty) {synchronized(lock){
        requireFree(human);server.bot.BotBrain.Difficulty level;
        try{level=server.bot.BotBrain.Difficulty.valueOf(difficulty);}catch(IllegalArgumentException e){throw new DomainException("INVALID_MESSAGE","Chọn mức Dễ, Vừa hoặc Khó");}
        invalidate(human);server.bot.BotClient bot=new server.bot.BotClient(users,matches,level);
        MatchSession session=matches.createSession(human,bot,"PRACTICE_"+UUID.randomUUID());
        human.reply(new Message(MessageType.BOT_MATCH_RESP));
        human.sendMessage(new Message(MessageType.MATCH_FOUND).put("room_id",session.getRoomId()).put("match_id",session.getMatchId()).put("opponent_id",bot.getUser().getUserId()).put("opponent_name",bot.getUser().getUsername()).put("practice",true));
        matches.scheduler().execute(()->bot.prepare(session));
    }}
    public String createRoom(ClientHandler host){synchronized(lock){
        requireFree(host);invalidate(host);
        Room room=new Room(code(),host);rooms.put(room.getRoomCode(),room);membership.put(host,room);users.updatePhase(host,"IN_ROOM");
        host.reply(new Message(MessageType.CREATE_ROOM_RESP).put("room_code",room.getRoomCode()).put("room_id",room.getRoomCode()));return room.getRoomCode();
    }}
    public boolean joinRoom(String code,ClientHandler guest){synchronized(lock){
        users.requireCanMatch(guest);
        Room room=rooms.get(code.trim().toUpperCase(Locale.ROOT));
        if(room==null)throw new DomainException("ROOM_NOT_FOUND","Không tìm thấy mã phòng");
        if(room.getHost()==guest)throw new DomainException("INVALID_STATE","Không thể vào phòng của chính mình");
        if(room.isFull())throw new DomainException("ROOM_FULL","Phòng đã đầy");
        requireFree(guest);if(!online(room.getHost()))throw new DomainException("ROOM_NOT_FOUND");users.requireCanMatch(room.getHost());
        room.setGuest(guest);room.setStatus(Room.RoomStatus.IN_GAME);membership.put(guest,room);
        MatchSession session=matches.createSession(room.getHost(),guest,room.getRoomCode());
        invalidate(guest);invalidate(room.getHost());
        guest.reply(new Message(MessageType.JOIN_ROOM_RESP).put("room_id",room.getRoomCode()).put("match_id",session.getMatchId()));
        Message event=new Message(MessageType.ROOM_USER_JOINED).put("room_code",room.getRoomCode()).put("room_id",room.getRoomCode()).put("match_id",session.getMatchId())
            .put("host_id",room.getHost().getUser().getUserId()).put("host_name",room.getHost().getUser().getUsername()).put("guest_id",guest.getUser().getUserId()).put("guest_name",guest.getUser().getUsername());
        room.getHost().sendMessage(event);guest.sendMessage(event);return true;
    }}
    private void pair(ClientHandler p1,ClientHandler p2,MessageType type,String invitationId){
        users.requireCanMatch(p1);users.requireCanMatch(p2);
        if(p1==p2 || !online(p1) || !online(p2) || membership.containsKey(p1) || membership.containsKey(p2) || matches.getSession(p1)!=null || matches.getSession(p2)!=null)throw new DomainException("INVALID_STATE");
        Room room=new Room(code(),p1);room.setGuest(p2);room.setStatus(Room.RoomStatus.IN_GAME);
        rooms.put(room.getRoomCode(),room);membership.put(p1,room);membership.put(p2,room);
        MatchSession session=matches.createSession(p1,p2,room.getRoomCode());
        invalidate(p1);invalidate(p2);
        for(ClientHandler player:Arrays.asList(p1,p2)) {
            ClientHandler opponent=player==p1?p2:p1;
            Message event=new Message(type).put("room_id",room.getRoomCode()).put("match_id",session.getMatchId()).put("opponent_id",opponent.getUser().getUserId()).put("opponent_name",opponent.getUser().getUsername());
            if(invitationId!=null)event.put("invitation_id",invitationId);
            player.sendMessage(event);
        }
    }
    public void joinMatchmaking(ClientHandler client){synchronized(lock){
        users.requireCanMatch(client);
        if(queue.contains(client)){client.reply(new Message(MessageType.QUEUE_JOIN_RESP));return;}
        requireFree(client);invalidate(client);queue.addLast(client);users.updatePhase(client,"QUEUED");client.reply(new Message(MessageType.QUEUE_JOIN_RESP));
        queue.removeIf(c->!online(c));
        while(queue.size()>=2){ClientHandler p1=queue.removeFirst(),p2=queue.removeFirst();pair(p1,p2,MessageType.MATCH_FOUND,null);}
    }}
    public void removeFromQueue(ClientHandler client){synchronized(lock){if(queue.remove(client) && online(client))users.updatePhase(client,"IDLE");}}
    public void handleInvite(ClientHandler sender,int targetId){synchronized(lock){
        ClientHandler receiver=users.getClientById(targetId);
        if(sender==receiver)throw new DomainException("INVALID_STATE","Không thể tự mời mình");
        requireFree(sender);requireFree(receiver);
        long count=invitations.values().stream().filter(i->i.sender==sender).count();if(count>=16)throw new DomainException("INVALID_STATE","Quá nhiều lời mời đang chờ");
        Invitation invitation=new Invitation(sender,receiver);invitations.put(invitation.id,invitation);
        sender.reply(new Message(MessageType.INVITE_RESP).put("invitation_id",invitation.id).put("expires_at_ms",invitation.expires));
        receiver.sendMessage(new Message(MessageType.INVITE_NOTIFY).put("invitation_id",invitation.id).put("expires_at_ms",invitation.expires).put("inviter_id",sender.getUser().getUserId()).put("inviter_name",sender.getUser().getUsername()).put("inviter_points",sender.getUser().getTotalPoints()));
        invitation.timeout=matches.scheduler().schedule(()->{synchronized(lock){if(invitations.remove(invitation.id,invitation))notifyExpired(invitation);}},GameConfig.INVITE_TIMEOUT_MS,TimeUnit.MILLISECONDS);
    }}
    private void notifyExpired(Invitation i){Message event=Message.error(MessageType.INVITE_RESULT,"Lời mời đã hết hạn hoặc bị hủy").errorCode("INVITE_EXPIRED").put("invitation_id",i.id).put("expires_at_ms",i.expires);i.sender.sendMessage(event);i.receiver.sendMessage(event);}
    private void invalidate(ClientHandler c){
        for(Invitation i:new ArrayList<>(invitations.values()))if(i.sender==c || i.receiver==c){invitations.remove(i.id);if(i.timeout!=null)i.timeout.cancel(false);notifyExpired(i);}
    }
    public void handleInviteResponse(ClientHandler receiver,Message request){synchronized(lock){
        String id=JsonUtil.getString(request.getData(),"invitation_id","");Invitation i=invitations.get(id);
        if(i==null || i.receiver!=receiver || System.currentTimeMillis()>=i.expires)throw new DomainException("INVITE_EXPIRED","Lời mời không tồn tại hoặc đã hết hạn");
        Object accept=request.getData().get("accept");if(!(accept instanceof Boolean))throw new IllegalArgumentException();
        if((Boolean)accept){requireFree(receiver);requireFree(i.sender);}
        invitations.remove(id);i.timeout.cancel(false);
        receiver.reply(new Message(MessageType.INVITE_RESULT).put("invitation_id",id).put("expires_at_ms",i.expires).put("accepted",accept));
        if(!(Boolean)accept){i.sender.sendMessage(new Message(MessageType.INVITE_RESULT,"SUCCESS","Đối thủ từ chối lời mời").put("invitation_id",id).put("accepted",false));return;}
        pair(i.sender,receiver,MessageType.INVITE_RESULT,id);
    }}
    public void leave(ClientHandler client,Message request,String reason){synchronized(lock){
        MatchSession session=matches.getSession(client);Room room=membership.get(client);
        if(session!=null && !session.getMatchId().equals(request.getData().get("match_id")))throw new DomainException("STALE_MATCH");
        if(session==null && room!=null && !room.getRoomCode().equals(request.getData().get("room_id")))throw new DomainException("STALE_MATCH");
        if(session==null && room==null && (request.getData().containsKey("match_id") || request.getData().containsKey("room_id")))throw new DomainException("STALE_MATCH");
        disconnect(client,reason);
        client.reply(new Message(ClientHandler.responseType(request.getType())));
    }}
    public void disconnect(ClientHandler client,String reason){synchronized(lock){
        MatchSession session=matches.getSession(client);
        if(session!=null){session.leave(client,reason);release(session,"Đối thủ đã rời phòng");}
        else {
            Room room=membership.remove(client);if(room!=null){rooms.remove(room.getRoomCode(),room);room.setStatus(Room.RoomStatus.CLOSED);}
        }
        queue.remove(client);invalidate(client);if(online(client))users.updatePhase(client,"IDLE");
    }}
    public void release(MatchSession session,String reason){synchronized(lock){
        if(matches.getSession(session.getPlayer1())!=session && matches.getSession(session.getPlayer2())!=session)return;
        matches.cleanupSession(session);
        for(ClientHandler c:Arrays.asList(session.getPlayer1(),session.getPlayer2())) {
            Room room=membership.get(c);
            if(room!=null && room.getRoomCode().equals(session.getRoomId())){membership.remove(c);rooms.remove(room.getRoomCode(),room);room.setStatus(Room.RoomStatus.CLOSED);}
            if(online(c))users.updatePhase(c,"IDLE");
            c.sendMessage(new Message(MessageType.ROOM_USER_LEFT,"SUCCESS",reason).put("room_id",session.getRoomId()).put("match_id",session.getMatchId()));
        }
    }}
    public Room getRoomByClient(ClientHandler c){synchronized(lock){return membership.get(c);}}
    public void shutdown(){synchronized(lock){for(Invitation i:invitations.values())if(i.timeout!=null)i.timeout.cancel(false);invitations.clear();queue.clear();rooms.clear();membership.clear();}}
}
