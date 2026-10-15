package client.controller;

import client.ui.ChatPanel;
import common.protocol.*;
import javax.swing.*;
import java.util.*;
import java.util.function.Consumer;

/** Session-scoped chat state, shared by the lobby, deployment and battle windows. */
public final class ChatController implements ChatPanel.Actions {
    private static final class Conversation {
        final int id;String name,draft="",error="",retryBody="",retryId="";boolean online,available=true,loading,loaded,more,pending;
        int unread;final TreeMap<Long,Map<String,Object>> messages=new TreeMap<>();
        Conversation(int id,String name){this.id=id;this.name=name;}
    }
    private static final class Request {
        final int peer;final String body;Request(int peer,String body){this.peer=peer;this.body=body;}
    }
    private final int ownId;
    private final Consumer<Message> send;
    private final Map<Integer,Conversation> conversations=new LinkedHashMap<>();
    private final Map<String,Request> pending=new HashMap<>();
    private final ChatPanel panel;
    private int selected=-1;
    public ChatController(int ownId,Consumer<Message> send){this.ownId=ownId;this.send=send;panel=new ChatPanel(this);}
    public void attach(JFrame frame){panel.attach(frame);}
    public void dispose(){panel.detach();pending.clear();conversations.clear();}
    public void load(){send.accept(new Message(MessageType.CHAT_CONVERSATIONS_REQ));}
    private Conversation peer(int id,String name){Conversation p=conversations.get(id);if(p==null){p=new Conversation(id,name);conversations.put(id,p);}else if(name!=null&&!name.isEmpty())p.name=name;return p;}
    public void open(int id,String name){if(id<=0||id==ownId)return;peer(id,name);select(id);panel.setExpanded(true);panel.focusInput();}
    public void opponent(int id,String name){if(id<=0)return;peer(id,name).online=true;select(id);panel.setExpanded(false);refresh(false);}
    public void online(List<Object> list) {
        for(Conversation p:conversations.values())p.online=false;
        if(list!=null)for(Object o:list)if(o instanceof Map){@SuppressWarnings("unchecked") Map<String,Object> m=(Map<String,Object>)o;int id=JsonUtil.getInt(m,"user_id",0);if(id>0&&id!=ownId){Conversation p=peer(id,JsonUtil.getString(m,"username",""));p.online=true;p.available=true;}}
        refresh(false);
    }
    @Override public void select(int id){
        if(selected>0)conversations.get(selected).draft=panel.draft();selected=id;Conversation p=conversations.get(id);if(p==null)return;
        panel.setDraft(p.draft);if(panel.isExpanded())p.unread=0;if(!p.loaded&&!p.loading)history(p,0);refresh(true);
    }
    @Override public void opened(){Conversation p=conversations.get(selected);if(p==null&&!conversations.isEmpty()){select(conversations.keySet().iterator().next());p=conversations.get(selected);}if(p!=null){p.unread=0;if(!p.loaded&&!p.loading)history(p,0);}refresh(true);}
    private void history(Conversation p,long before){p.loading=true;p.error="";String id=UUID.randomUUID().toString();pending.put(id,new Request(p.id,null));send.accept(new Message(MessageType.CHAT_HISTORY_REQ).requestId(id).put("peer_id",p.id).put("before_id",before));}
    @Override public void older(){Conversation p=conversations.get(selected);if(p!=null&&!p.loading&&p.more&&!p.messages.isEmpty()){history(p,p.messages.firstKey());refresh(false);}}
    @Override public void send(String text){
        Conversation p=conversations.get(selected);String body=text.trim();if(p==null||!p.available||p.pending||body.isEmpty())return;
        p.draft=text;if(!p.retryBody.equals(body)){p.retryBody=body;p.retryId=UUID.randomUUID().toString();}p.pending=true;p.error="";
        String id=UUID.randomUUID().toString();pending.put(id,new Request(p.id,body));send.accept(new Message(MessageType.CHAT_SEND_REQ).requestId(id).put("recipient_id",p.id).put("body",body).put("client_message_id",p.retryId));refresh(false);
    }
    private boolean add(Map<String,Object> m,boolean incoming) {
        int sender=JsonUtil.getInt(m,"sender_id",-1),recipient=JsonUtil.getInt(m,"recipient_id",-1);if(sender!=ownId&&recipient!=ownId)return false;
        int id=sender==ownId?recipient:sender;Conversation p=peer(id,JsonUtil.getString(m,sender==ownId?"recipient_name":"sender_name",""));long messageId=JsonUtil.getLong(m,"message_id",0);
        boolean fresh=p.messages.put(messageId,m)==null;if(fresh&&incoming && (selected!=id||!panel.isExpanded()))p.unread++;
        return fresh;
    }
    public boolean handle(Message msg) {
        MessageType type=msg.getType();if(type!=MessageType.CHAT_SEND_RESP&&type!=MessageType.CHAT_MSG&&type!=MessageType.CHAT_HISTORY_RESP&&type!=MessageType.CHAT_CONVERSATIONS_RESP)return false;
        Request request=pending.remove(msg.getRequestId());
        if("ERROR".equals(msg.getStatus())){if(request!=null){Conversation p=conversations.get(request.peer);if(request.body==null)p.loading=false;else p.pending=false;p.error=msg.getMessage();if("USER_NOT_FOUND".equals(msg.getErrorCode()))p.available=false;}refresh(false);return true;}
        if(type==MessageType.CHAT_CONVERSATIONS_RESP){
            List<Object> list=JsonUtil.getList(msg.getData(),"conversations");if(list!=null)for(Object o:list){@SuppressWarnings("unchecked") Map<String,Object> m=(Map<String,Object>)o;Conversation p=peer(JsonUtil.getInt(m,"user_id",0),JsonUtil.getString(m,"username",""));p.online=JsonUtil.getBoolean(m,"online",false);p.available=JsonUtil.getBoolean(m,"available",true);}
        }else if(type==MessageType.CHAT_HISTORY_RESP){
            int id=JsonUtil.getInt(msg.getData(),"peer_id",-1);Conversation p=peer(id,JsonUtil.getString(msg.getData(),"peer_name",""));p.loading=false;p.loaded=true;p.more=JsonUtil.getBoolean(msg.getData(),"has_more",false);p.available=JsonUtil.getBoolean(msg.getData(),"available",true);
            List<Object> rows=JsonUtil.getList(msg.getData(),"messages");if(rows!=null)for(Object o:rows){@SuppressWarnings("unchecked") Map<String,Object> m=(Map<String,Object>)o;add(m,false);}
        }else {
            Map<String,Object> m=JsonUtil.getMap(msg.getData(),"chat");if(m!=null)add(m,type==MessageType.CHAT_MSG);
            if(type==MessageType.CHAT_SEND_RESP&&request!=null){Conversation p=conversations.get(request.peer);p.pending=false;p.error="";if(selected==p.id)p.draft=panel.draft();if(p.draft.trim().equals(request.body))p.draft="";p.retryBody="";p.retryId="";if(selected==p.id)panel.setDraft(p.draft);}
        }
        boolean prepend=type==MessageType.CHAT_HISTORY_RESP&&JsonUtil.getInt(msg.getData(),"peer_id",-1)==selected&&JsonUtil.getLong(msg.getData(),"before_id",0)>0;
        refresh(type==MessageType.CHAT_SEND_RESP,prepend);return true;
    }
    private void refresh(boolean scrollDown){refresh(scrollDown,false);}
    private void refresh(boolean scrollDown,boolean prepend){
        List<ChatPanel.Peer> choices=new ArrayList<>();int unread=0;
        for(Conversation p:conversations.values()){unread+=p.unread;choices.add(new ChatPanel.Peer(p.id,(p.online?"● ":"○ ")+p.name+(p.unread>0?" ("+p.unread+")":"")));}
        panel.setContacts(choices,selected);Conversation p=conversations.get(selected);panel.setHeader(p==null?"Tin nhắn":"Chat · "+p.name,unread);
        if(p==null){panel.setComposer(false,"Chọn người để trò chuyện",false);return;}
        String status=!p.error.isEmpty()?p.error:p.pending?"Đang gửi…":!p.available?"Tài khoản đã xóa":p.online?"Trực tuyến":"Ngoại tuyến · Vẫn có thể gửi tin";
        panel.setComposer(p.available&&!p.pending,status,!p.error.isEmpty());panel.showMessages(p.messages.values(),ownId,p.more,p.loading,scrollDown,prepend);
    }
}
