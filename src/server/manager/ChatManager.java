package server.manager;

import common.protocol.*;
import server.network.ClientHandler;
import java.util.*;

/** Private messages: identity comes exclusively from the authenticated socket. */
public final class ChatManager {
    private final UserManager users;
    private final StorageManager storage;
    public ChatManager(UserManager users,StorageManager storage){this.users=users;this.storage=storage;}
    public void send(ClientHandler sender,Message request) {
        synchronized(users.getRegistryLock()) {
        Map<String,Object> d=request.getData();int target=JsonUtil.requireInt(d,"recipient_id");
        if(!(d.get("body") instanceof String) || !(d.get("client_message_id") instanceof String))throw new DomainException("INVALID_MESSAGE","Tin nhắn không hợp lệ");
        if(!sender.allowChat())throw new DomainException("CHAT_RATE_LIMIT","Bạn gửi quá nhanh. Hãy chờ vài giây.");
        Map<String,Object> message=storage.saveChat(sender.getUser().getUserId(),target,(String)d.get("body"),(String)d.get("client_message_id"));
        boolean created=Boolean.TRUE.equals(message.remove("_created"));
        sender.reply(new Message(MessageType.CHAT_SEND_RESP).put("chat",message));
        // Deliver only after SQLite commits. Retried sends never produce duplicate events.
        ClientHandler recipient=users.getClientById(target);
        if(created && recipient!=null)recipient.sendMessage(new Message(MessageType.CHAT_MSG).put("chat",message));
        }
    }
    public void history(ClientHandler sender,Message request) {
        Map<String,Object> d=request.getData();long before=0;
        if(d.containsKey("before_id")){Object raw=d.get("before_id");if(!(raw instanceof Number) || ((Number)raw).doubleValue()!=((Number)raw).longValue() || ((Number)raw).longValue()<0)throw new DomainException("INVALID_MESSAGE");before=((Number)raw).longValue();}
        Message response=new Message(MessageType.CHAT_HISTORY_RESP);response.setData(storage.chatHistory(sender.getUser().getUserId(),JsonUtil.requireInt(d,"peer_id"),before));sender.reply(response);
    }
    public void conversations(ClientHandler sender) {
        List<Map<String,Object>> list=storage.chatConversations(sender.getUser().getUserId());
        for(Map<String,Object> peer:list)peer.put("online",users.getClientById(((Number)peer.get("user_id")).intValue())!=null);
        sender.reply(new Message(MessageType.CHAT_CONVERSATIONS_RESP).put("conversations",list));
    }
}
