package server.network;

import common.model.User;
import common.protocol.*;
import server.manager.*;
import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final UserManager users;
    private final LobbyManager lobby;
    private final MatchManager matches;
    private volatile LineConnection connection;
    private volatile User user;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Runnable onClosed;
    private final Map<String, Cached> cache = new LinkedHashMap<>();
    private Message currentRequest;
    private final Deque<Long> chatTimes=new ArrayDeque<>();
    public boolean allowChat() {
        long now=System.nanoTime();while(!chatTimes.isEmpty() && now-chatTimes.peekFirst()>5_000_000_000L)chatTimes.removeFirst();
        if(chatTimes.size()>=10)return false;chatTimes.addLast(now);return true;
    }
    private List<String> directResponses;
    private final ThreadLocal<Boolean> processing = new ThreadLocal<>();
    private static class Cached {
        final String body; final List<String> responses;
        Cached(String body,List<String> responses) {this.body=body;this.responses=responses;}
    }
    public ClientHandler(Socket socket, UserManager users, LobbyManager lobby, MatchManager matches) {
        this(socket,users,lobby,matches,()->{});
    }
    public ClientHandler(Socket socket, UserManager users, LobbyManager lobby, MatchManager matches,Runnable onClosed) {
        this.socket=socket;this.users=users;this.lobby=lobby;this.matches=matches;this.onClosed=onClosed;
    }
    public void run() {
        try {
            connection=new LineConnection(socket);
            String line;
            while(!closed.get() && (line=connection.readLine())!=null) {
                Message request;
                try {request=Message.fromJson(line);if(request==null)throw new IllegalArgumentException("INVALID_MESSAGE");}
                catch(IllegalArgumentException e) {sendMessage(Message.error(MessageType.ERROR_NOTIFY,"JSON không hợp lệ").errorCode("UNKNOWN_TYPE".equals(e.getMessage())?"UNKNOWN_TYPE":"INVALID_MESSAGE"));continue;}
                connection.touch(); process(request);
            }
        } catch(IOException e) { /* EOF, malformed UTF-8 and oversized frames close this connection only. */ }
        finally {close();}
    }
    private void process(Message request) {
        String id=request.getRequestId();
        String body=request.toJson();
        if(user==null && request.getType()!=MessageType.LOGIN_REQ && request.getType()!=MessageType.HEARTBEAT_REQ) {
            sendMessage(Message.error(responseType(request.getType()),"Vui lòng đăng nhập").errorCode("UNAUTHENTICATED").requestId(id));
            return;
        }
        if(id!=null && cache.containsKey(id)) {
            Cached old=cache.get(id);
            if(!old.body.equals(body)) {sendMessage(Message.error(responseType(request.getType()),"request_id đã được dùng cho nội dung khác").errorCode("INVALID_MESSAGE").requestId(id));return;}
            for(String response:old.responses)sendMessage(Message.fromJson(response));return;
        }
        currentRequest=request;directResponses=new ArrayList<>();processing.set(true);
        try {
            if(user==null && request.getType()!=MessageType.LOGIN_REQ && request.getType()!=MessageType.HEARTBEAT_REQ)throw new DomainException("UNAUTHENTICATED","Vui lòng đăng nhập");
            handle(request);
        } catch(DomainException e) {
            Message error=Message.error(responseType(request.getType()),e.getMessage()).errorCode(e.code);
            error.getData().putAll(e.data);reply(error);
        }
        catch(IllegalArgumentException e) {reply(Message.error(responseType(request.getType()),"Dữ liệu yêu cầu không hợp lệ").errorCode("INVALID_MESSAGE"));}
        catch(RuntimeException e) {System.err.println("[Request] " + e.getClass().getSimpleName());reply(Message.error(responseType(request.getType()),"Không thể xử lý yêu cầu").errorCode("INVALID_STATE"));}
        finally {
            if(id!=null) {cache.put(id,new Cached(body,directResponses));while(cache.size()>256)cache.remove(cache.keySet().iterator().next());}
            processing.remove();currentRequest=null;directResponses=null;
        }
    }
    private void handle(Message msg) {
        Map<String,Object> d=msg.getData();
        switch(msg.getType()) {
            case CHAT_SEND_REQ:users.getChat().send(this,msg);break;
            case CHAT_HISTORY_REQ:users.getChat().history(this,msg);break;
            case CHAT_CONVERSATIONS_REQ:users.getChat().conversations(this);break;
            case HEARTBEAT_REQ: reply(new Message(MessageType.HEARTBEAT_RESP));break;
            case LOGIN_REQ:
                if(!(d.get("username") instanceof String) || !(d.get("password") instanceof String))throw new DomainException("AUTH_FAILED");
                User u=users.login((String)d.get("username"),(String)d.get("password"),this);
                Message login=new Message(MessageType.LOGIN_RESP);login.setData(users.publicUser(u));reply(login);
                users.broadcastUserStatus(u);sendMessage(new Message(MessageType.ONLINE_USERS_RESP).put("users",users.getOnlineUsersList()));break;
            case LOGOUT_REQ:
                synchronized(users.getRegistryLock()) {lobby.disconnect(this,"DISCONNECTED");users.logout(this);}
                cache.clear();
                reply(new Message(MessageType.LOGOUT_RESP));break;
            case DELETE_ACCOUNT_REQ:
                if(!(d.get("password") instanceof String))throw new DomainException("AUTH_FAILED");
                synchronized(users.getRegistryLock()){users.deleteAccount(this,(String)d.get("password"));lobby.disconnect(this,"DISCONNECTED");users.logout(this);}
                cache.clear();reply(new Message(MessageType.DELETE_ACCOUNT_RESP));break;
            case BOT_MATCH_REQ:lobby.startBotMatch(this,JsonUtil.getString(d,"difficulty",""));break;
            case GET_ONLINE_USERS_REQ:reply(new Message(MessageType.ONLINE_USERS_RESP).put("users",users.getOnlineUsersList()));break;
            case GET_LEADERBOARD_REQ:reply(new Message(MessageType.LEADERBOARD_RESP).put("leaderboard",users.getLeaderboard()));break;
            case GET_MATCH_HISTORY_REQ:{
                int target=JsonUtil.getInt(d,"target_user_id",0);
                int limit=JsonUtil.getInt(d,"limit",20);
                Map<String,Object> history=users.matchHistory(this,target,limit);
                Message response=new Message(MessageType.MATCH_HISTORY_RESP);
                response.setData(history);reply(response);break;}
            case CREATE_ROOM_REQ: lobby.createRoom(this);break;
            case JOIN_ROOM_REQ: lobby.joinRoom(JsonUtil.getString(d,"room_code",""),this);break;
            case LEAVE_ROOM_REQ:lobby.leave(this,msg,"SURRENDER");break;
            case QUEUE_JOIN_REQ:lobby.joinMatchmaking(this);break;
            case QUEUE_LEAVE_REQ:lobby.removeFromQueue(this);reply(new Message(MessageType.QUEUE_LEAVE_RESP));break;
            case INVITE_REQ:lobby.handleInvite(this,JsonUtil.requireInt(d,"target_user_id"));break;
            case INVITE_RESPOND_REQ:lobby.handleInviteResponse(this,msg);break;
            case PLACE_SHIPS_REQ:matches.handlePlaceShips(this,msg);break;
            case PLAYER_READY_REQ:matches.handleReady(this,msg);break;
            case FIRE_REQ:matches.handleFire(this,msg);break;
            case SURRENDER_REQ:lobby.leave(this,msg,"SURRENDER");break;
            case REMATCH_REQ:matches.handleRematch(this,msg,true);break;
            case REMATCH_CONFIRM_REQ:
                if(!(d.get("accept") instanceof Boolean))throw new IllegalArgumentException();
                matches.handleRematch(this,msg,(Boolean)d.get("accept"));break;
            default:throw new DomainException("UNKNOWN_TYPE","Loại tin này không phải yêu cầu client");
        }
    }
    public static MessageType responseType(MessageType type) {
        switch(type) {
            case CHAT_SEND_REQ:return MessageType.CHAT_SEND_RESP;
            case CHAT_HISTORY_REQ:return MessageType.CHAT_HISTORY_RESP;
            case CHAT_CONVERSATIONS_REQ:return MessageType.CHAT_CONVERSATIONS_RESP;
            case DELETE_ACCOUNT_REQ:return MessageType.DELETE_ACCOUNT_RESP;
            case BOT_MATCH_REQ:return MessageType.BOT_MATCH_RESP;
            case LOGIN_REQ:return MessageType.LOGIN_RESP;
            case LOGOUT_REQ:return MessageType.LOGOUT_RESP;
            case CREATE_ROOM_REQ:return MessageType.CREATE_ROOM_RESP;
            case JOIN_ROOM_REQ:return MessageType.JOIN_ROOM_RESP;
            case LEAVE_ROOM_REQ:return MessageType.LEAVE_ROOM_RESP;
            case QUEUE_JOIN_REQ:return MessageType.QUEUE_JOIN_RESP;
            case QUEUE_LEAVE_REQ:return MessageType.QUEUE_LEAVE_RESP;
            case INVITE_REQ:return MessageType.INVITE_RESP;
            case INVITE_RESPOND_REQ:return MessageType.INVITE_RESULT;
            case PLACE_SHIPS_REQ:return MessageType.PLACE_SHIPS_RESP;
            case PLAYER_READY_REQ:return MessageType.PLAYER_READY_RESP;
            case FIRE_REQ:return MessageType.FIRE_RESULT;
            case SURRENDER_REQ:return MessageType.SURRENDER_RESP;
            case REMATCH_REQ:return MessageType.REMATCH_RESP;
            case REMATCH_CONFIRM_REQ:return MessageType.REMATCH_CONFIRM_RESP;
            case GET_MATCH_HISTORY_REQ:return MessageType.MATCH_HISTORY_RESP;
            default:return MessageType.ERROR_NOTIFY;
        }
    }
    /** Only explicit direct responses echo request_id. Events never enter replay cache. */
    public void reply(Message msg) {
        if(Boolean.TRUE.equals(processing.get()) && currentRequest!=null) {
            msg.requestId(currentRequest.getRequestId());msg.serverTime(System.currentTimeMillis());
            directResponses.add(msg.toJson());
        }
        sendMessage(msg);
    }
    public void sendMessage(Message msg) {
        LineConnection c=connection;
        if(c!=null && !closed.get() && msg!=null) {
            // Freeze event data before returning to the domain lock.
            Message copy=Message.fromJson(msg.toJson());copy.serverTime(System.currentTimeMillis());c.send(copy);
        }
    }
    public void close() {
        if(!closed.compareAndSet(false,true))return;
        LineConnection c=connection;if(c!=null)c.close();else try{socket.close();}catch(IOException ignored){}
        synchronized(users.getRegistryLock()) {lobby.disconnect(this,"DISCONNECTED");users.logout(this);}
        onClosed.run();
    }
    public User getUser(){return user;}
    public void setUser(User user){this.user=user;}
    public boolean isConnected(){return !closed.get() && !socket.isClosed();}
}
