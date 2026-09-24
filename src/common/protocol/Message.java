package common.protocol;

import java.util.LinkedHashMap;
import java.util.Map;

public class Message {
    private MessageType type;
    private String status;
    private String message;
    private Map<String, Object> data = new LinkedHashMap<>();

    public Message() {
    }

    public Message(MessageType type) {
        this.type = type;
        this.status = "SUCCESS";
    }

    public Message(MessageType type, String status, String message) {
        this.type = type;
        this.status = status;
        this.message = message;
    }

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data != null ? data : new LinkedHashMap<>();
    }

    public Message put(String key, Object val) {
        if (this.data == null) {
            this.data = new LinkedHashMap<>();
        }
        this.data.put(key, val);
        return this;
    }

    public static Message success(MessageType type, String msg) {
        return new Message(type, "SUCCESS", msg);
    }

    public static Message error(MessageType type, String msg) {
        return new Message(type, "ERROR", msg);
    }

    public String toJson() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", type != null ? type.name() : "");
        if (status != null) map.put("status", status);
        if (message != null) map.put("message", message);
        if (data != null && !data.isEmpty()) map.put("data", data);
        return JsonUtil.toJson(map);
    }

    public static Message fromJson(String json) {
        if (json == null || json.trim().isEmpty()) return null;
        Map<String, Object> map = JsonUtil.parseObject(json);
        Message msg = new Message();
        String typeStr = JsonUtil.getString(map, "type", null);
        if (typeStr != null) {
            try {
                msg.setType(MessageType.valueOf(typeStr));
            } catch (Exception ignored) {
            }
        }
        msg.setStatus(JsonUtil.getString(map, "status", null));
        msg.setMessage(JsonUtil.getString(map, "message", null));
        Map<String, Object> dataMap = JsonUtil.getMap(map, "data");
        if (dataMap != null) {
            msg.setData(dataMap);
        }
        return msg;
    }

    @Override
    public String toString() {
        return toJson();
    }
}
