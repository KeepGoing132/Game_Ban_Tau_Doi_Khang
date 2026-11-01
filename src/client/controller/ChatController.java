package client.controller;

import client.ui.ChatPanel;
import common.protocol.*;
import javax.swing.*;
import java.util.*;
import java.util.function.Consumer;

/** Session-scoped chat state, shared by the lobby, deployment and battle windows. */
public final class ChatController implements ChatPanel.Actions {
    /** One conversation: draft, request state, unread counter and the messages loaded so far. */
    private static final class Conversation {
        final int id;
        final TreeMap<Long, Map<String, Object>> messages = new TreeMap<>();
        String name;
        String draft = "";
        String error = "";
        String retryBody = "";
        String retryId = "";
        boolean online;
        boolean available = true;
        boolean loading;
        boolean loaded;
        boolean more;
        boolean pending;
        int unread;

        Conversation(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    /** A request in flight: which peer it targeted and the body it carried. */
    private static final class Request {
        final int peer;
        final String body;

        Request(int peer, String body) {
            this.peer = peer;
            this.body = body;
        }
    }

    private final int ownId;
    private final Consumer<Message> send;
    private final Map<Integer, Conversation> conversations = new LinkedHashMap<>();
    private final Map<String, Request> pending = new HashMap<>();
    private final ChatPanel panel;
    private int selected = -1;

    public ChatController(int ownId, Consumer<Message> send) {
        this.ownId = ownId;
        this.send = send;
        this.panel = new ChatPanel(this);
    }

    public void attach(JFrame frame) {
        panel.attach(frame);
    }

    public void dispose() {
        panel.detach();
        pending.clear();
        conversations.clear();
    }

    public void load() {
        send.accept(new Message(MessageType.CHAT_CONVERSATIONS_REQ));
    }

    /** Finds the conversation of one peer, creating it on first use. */
    private Conversation peer(int id, String name) {
        Conversation conversation = conversations.get(id);
        if (conversation == null) {
            conversation = new Conversation(id, name);
            conversations.put(id, conversation);
        } else if (name != null && !name.isEmpty()) {
            conversation.name = name;
        }
        return conversation;
    }

    /** Opens the window for one peer, e.g. from the lobby buttons. */
    public void open(int id, String name) {
        if (id <= 0 || id == ownId) {
            return;
        }
        peer(id, name);
        select(id);
        panel.setExpanded(true);
        panel.focusInput();
    }

    /** Points the collapsed window at the opponent when a match starts. */
    public void opponent(int id, String name) {
        if (id <= 0) {
            return;
        }
        peer(id, name).online = true;
        select(id);
        panel.setExpanded(false);
        refresh(false);
    }

    public void online(List<Object> list) {
        for (Conversation conversation : conversations.values()) {
            conversation.online = false;
        }
        if (list != null) {
            for (Object item : list) {
                Map<String, Object> entry = asMap(item);
                if (entry == null) {
                    continue;
                }
                int id = JsonUtil.getInt(entry, "user_id", 0);
                if (id <= 0 || id == ownId) {
                    continue;
                }
                Conversation conversation = peer(id, JsonUtil.getString(entry, "username", ""));
                conversation.online = true;
                conversation.available = true;
            }
        }
        refresh(false);
    }

    @Override
    public void select(int id) {
        if (selected > 0) {
            conversations.get(selected).draft = panel.draft();
        }
        selected = id;
        Conversation conversation = conversations.get(id);
        if (conversation == null) {
            return;
        }
        panel.setDraft(conversation.draft);
        if (panel.isExpanded()) {
            conversation.unread = 0;
        }
        if (!conversation.loaded && !conversation.loading) {
            history(conversation, 0);
        }
        refresh(true);
    }

    @Override
    public void opened() {
        Conversation conversation = conversations.get(selected);
        if (conversation == null && !conversations.isEmpty()) {
            select(conversations.keySet().iterator().next());
            conversation = conversations.get(selected);
        }
        if (conversation != null) {
            conversation.unread = 0;
            if (!conversation.loaded && !conversation.loading) {
                history(conversation, 0);
            }
        }
        refresh(true);
    }

    private void history(Conversation conversation, long before) {
        conversation.loading = true;
        conversation.error = "";
        String requestId = UUID.randomUUID().toString();
        pending.put(requestId, new Request(conversation.id, null));
        send.accept(new Message(MessageType.CHAT_HISTORY_REQ)
                .requestId(requestId)
                .put("peer_id", conversation.id)
                .put("before_id", before));
    }

    @Override
    public void older() {
        Conversation conversation = conversations.get(selected);
        if (conversation == null || conversation.loading || !conversation.more || conversation.messages.isEmpty()) {
            return;
        }
        history(conversation, conversation.messages.firstKey());
        refresh(false);
    }

    @Override
    public void send(String text) {
        Conversation conversation = conversations.get(selected);
        String body = text.trim();
        if (conversation == null || !conversation.available || conversation.pending || body.isEmpty()) {
            return;
        }
        conversation.draft = text;
        if (!conversation.retryBody.equals(body)) {
            // a retry of the same text reuses the client message id, so the server never stores it twice
            conversation.retryBody = body;
            conversation.retryId = UUID.randomUUID().toString();
        }
        conversation.pending = true;
        conversation.error = "";
        String requestId = UUID.randomUUID().toString();
        pending.put(requestId, new Request(conversation.id, body));
        send.accept(new Message(MessageType.CHAT_SEND_REQ)
                .requestId(requestId)
                .put("recipient_id", conversation.id)
                .put("body", body)
                .put("client_message_id", conversation.retryId));
        refresh(false);
    }

    /** Files one received/stored message; returns true when it had not been seen before. */
    private boolean add(Map<String, Object> message, boolean incoming) {
        int sender = JsonUtil.getInt(message, "sender_id", -1);
        int recipient = JsonUtil.getInt(message, "recipient_id", -1);
        if (sender != ownId && recipient != ownId) {
            return false;
        }
        int peerId = sender == ownId ? recipient : sender;
        String name = JsonUtil.getString(message, sender == ownId ? "recipient_name" : "sender_name", "");
        Conversation conversation = peer(peerId, name);
        long messageId = JsonUtil.getLong(message, "message_id", 0);
        boolean fresh = conversation.messages.put(messageId, message) == null;
        if (fresh && incoming && (selected != peerId || !panel.isExpanded())) {
            conversation.unread++;
        }
        return fresh;
    }

    /** Handles every chat message; returns false for message types chat does not own. */
    public boolean handle(Message msg) {
        MessageType type = msg.getType();
        if (type != MessageType.CHAT_SEND_RESP && type != MessageType.CHAT_MSG
                && type != MessageType.CHAT_HISTORY_RESP && type != MessageType.CHAT_CONVERSATIONS_RESP) {
            return false;
        }
        Request request = pending.remove(msg.getRequestId());
        if ("ERROR".equals(msg.getStatus())) {
            if (request != null) {
                Conversation conversation = conversations.get(request.peer);
                if (request.body == null) {
                    conversation.loading = false;
                } else {
                    conversation.pending = false;
                }
                conversation.error = msg.getMessage();
                if ("USER_NOT_FOUND".equals(msg.getErrorCode())) {
                    conversation.available = false;
                }
            }
            refresh(false);
            return true;
        }
        if (type == MessageType.CHAT_CONVERSATIONS_RESP) {
            List<Object> list = JsonUtil.getList(msg.getData(), "conversations");
            if (list != null) {
                for (Object item : list) {
                    Map<String, Object> entry = asMap(item);
                    if (entry == null) {
                        continue;
                    }
                    Conversation conversation = peer(JsonUtil.getInt(entry, "user_id", 0), JsonUtil.getString(entry, "username", ""));
                    conversation.online = JsonUtil.getBoolean(entry, "online", false);
                    conversation.available = JsonUtil.getBoolean(entry, "available", true);
                }
            }
        } else if (type == MessageType.CHAT_HISTORY_RESP) {
            int peerId = JsonUtil.getInt(msg.getData(), "peer_id", -1);
            Conversation conversation = peer(peerId, JsonUtil.getString(msg.getData(), "peer_name", ""));
            conversation.loading = false;
            conversation.loaded = true;
            conversation.more = JsonUtil.getBoolean(msg.getData(), "has_more", false);
            conversation.available = JsonUtil.getBoolean(msg.getData(), "available", true);
            List<Object> rows = JsonUtil.getList(msg.getData(), "messages");
            if (rows != null) {
                for (Object row : rows) {
                    Map<String, Object> entry = asMap(row);
                    if (entry != null) {
                        add(entry, false);
                    }
                }
            }
        } else {
            Map<String, Object> chat = JsonUtil.getMap(msg.getData(), "chat");
            if (chat != null) {
                add(chat, type == MessageType.CHAT_MSG);
            }
            if (type == MessageType.CHAT_SEND_RESP && request != null) {
                Conversation conversation = conversations.get(request.peer);
                conversation.pending = false;
                conversation.error = "";
                if (selected == conversation.id) {
                    conversation.draft = panel.draft();
                }
                if (conversation.draft.trim().equals(request.body)) {
                    conversation.draft = "";
                }
                conversation.retryBody = "";
                conversation.retryId = "";
                if (selected == conversation.id) {
                    panel.setDraft(conversation.draft);
                }
            }
        }
        boolean prepend = type == MessageType.CHAT_HISTORY_RESP
                && JsonUtil.getInt(msg.getData(), "peer_id", -1) == selected
                && JsonUtil.getLong(msg.getData(), "before_id", 0) > 0;
        refresh(type == MessageType.CHAT_SEND_RESP, prepend);
        return true;
    }

    private void refresh(boolean scrollDown) {
        refresh(scrollDown, false);
    }

    /** Pushes the whole chat state into the panel: contacts, header, composer and messages. */
    private void refresh(boolean scrollDown, boolean prepend) {
        List<ChatPanel.Peer> choices = new ArrayList<>();
        int unread = 0;
        for (Conversation conversation : conversations.values()) {
            unread += conversation.unread;
            String marker = conversation.online ? "● " : "○ ";
            String badge = conversation.unread > 0 ? " (" + conversation.unread + ")" : "";
            choices.add(new ChatPanel.Peer(conversation.id, marker + conversation.name + badge));
        }
        panel.setContacts(choices, selected);
        Conversation conversation = conversations.get(selected);
        panel.setHeader(conversation == null ? "Tin nhắn" : "Chat · " + conversation.name, unread);
        if (conversation == null) {
            panel.setComposer(false, "Chọn người để trò chuyện", false);
            return;
        }
        String status;
        if (!conversation.error.isEmpty()) {
            status = conversation.error;
        } else if (conversation.pending) {
            status = "Đang gửi…";
        } else if (!conversation.available) {
            status = "Tài khoản đã xóa";
        } else if (conversation.online) {
            status = "Trực tuyến";
        } else {
            status = "Ngoại tuyến · Vẫn có thể gửi tin";
        }
        panel.setComposer(conversation.available && !conversation.pending, status, !conversation.error.isEmpty());
        panel.showMessages(conversation.messages.values(), ownId, conversation.more, conversation.loading, scrollDown, prepend);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object item) {
        return item instanceof Map ? (Map<String, Object>) item : null;
    }
}
