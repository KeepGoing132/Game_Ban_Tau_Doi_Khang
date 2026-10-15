package common.protocol;

import java.util.LinkedHashMap;
import java.util.Map;

public class Message {
    private MessageType type;
    private String status;
    private String message;
    private String requestId;
    private String errorCode;
    private long serverTimeMs;

    public String getRequestId() { return requestId; }
    public Message requestId(String id) { requestId = id; return this; }
    public String getErrorCode() { return errorCode; }
    public Message errorCode(String code) { errorCode = code; return this; }
    public long getServerTimeMs() { return serverTimeMs; }
    public Message serverTime(long time) { serverTimeMs = time; return this; }
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
        if (requestId != null) map.put("request_id", requestId);
        if (errorCode != null) map.put("error_code", errorCode);
        if (serverTimeMs != 0) map.put("server_time_ms", serverTimeMs);
        if (status != null) map.put("status", status);
        if (message != null) map.put("message", message);
        if (data != null && !data.isEmpty()) map.put("data", data);
        return JsonUtil.toJson(map);
    }

    public static Message fromJson(String json) {
        if (json == null || json.trim().isEmpty()) return null;
        Map<String, Object> map = JsonUtil.parseObject(json);
        if (!(map.get("type") instanceof String)) throw new IllegalArgumentException("INVALID_MESSAGE");
        if (map.containsKey("data") && !(map.get("data") instanceof Map)) throw new IllegalArgumentException("INVALID_MESSAGE");
        if (map.containsKey("request_id") && (!(map.get("request_id") instanceof String) || ((String) map.get("request_id")).length() > 128)) throw new IllegalArgumentException("INVALID_MESSAGE");
        Message msg = new Message();
        String typeStr = JsonUtil.getString(map, "type", null);
        if (typeStr != null) {
            try {
                msg.setType(MessageType.valueOf(typeStr));
            } catch (Exception ignored) {
                throw new IllegalArgumentException("UNKNOWN_TYPE");
            }
        }
        msg.setStatus(JsonUtil.getString(map, "status", null));
        msg.setMessage(JsonUtil.getString(map, "message", null));
        msg.requestId(JsonUtil.getString(map, "request_id", null));
        msg.errorCode(JsonUtil.getString(map, "error_code", null));
        if (map.get("server_time_ms") instanceof Number) msg.serverTime(((Number) map.get("server_time_ms")).longValue());
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
