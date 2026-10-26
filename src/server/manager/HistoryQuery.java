package server.manager;

import common.protocol.JsonUtil;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Validated filters and keyset cursor, scoped to one account and one set of filters. */
public final class HistoryQuery {
    public final int limit;
    public final String result, opponent;
    public final long from, to, snapshot, beforeTime;
    public final String beforeId;
    private final String scope;

    public HistoryQuery(int target, Map<String,Object> data) {
        int requestedLimit=data.containsKey("limit")?JsonUtil.requireInt(data,"limit"):20;
        limit = Math.max(1,Math.min(50,requestedLimit<=0?20:requestedLimit));
        result = string(data,"result","ALL").toUpperCase(Locale.ROOT);
        opponent = string(data,"opponent","").trim().toLowerCase(Locale.ROOT);
        from = number(data,"from_ms",0); to = number(data,"to_ms",Long.MAX_VALUE);
        if ((!"ALL".equals(result) && !"WIN".equals(result) && !"LOSS".equals(result)) || opponent.length()>64 || from>to)
            throw new DomainException("INVALID_MESSAGE","Bộ lọc lịch sử không hợp lệ");
        Map<String,Object> filters = new LinkedHashMap<>();
        filters.put("user",target); filters.put("result",result); filters.put("opponent",opponent); filters.put("from",from); filters.put("to",to);
        scope = JsonUtil.toJson(filters);
        String cursor = string(data,"cursor","");
        long sequence=0,time=Long.MAX_VALUE; String id="";
        if (!cursor.isEmpty()) {
            try {
                if (cursor.length()>2048) throw new IllegalArgumentException();
                Map<String,Object> c = JsonUtil.parseObject(new String(Base64.getUrlDecoder().decode(cursor),StandardCharsets.UTF_8));
                if (!scope.equals(c.get("scope"))) throw new IllegalArgumentException();
                sequence=number(c,"snapshot",0); time=number(c,"time",0); id=string(c,"id","");
                if (sequence<=0 || id.isEmpty() || id.length()>128) throw new IllegalArgumentException();
            } catch (RuntimeException e) { throw new DomainException("INVALID_MESSAGE","Trang lịch sử không hợp lệ. Hãy tải lại từ đầu."); }
        }
        snapshot=sequence; beforeTime=time; beforeId=id;
    }
    public String cursor(long sequence,long time,String id) {
        Map<String,Object> value = new LinkedHashMap<>();
        value.put("scope",scope); value.put("snapshot",sequence); value.put("time",time); value.put("id",id);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(JsonUtil.toJson(value).getBytes(StandardCharsets.UTF_8));
    }
    private static String string(Map<String,Object> data,String key,String fallback) {
        if (!data.containsKey(key)) return fallback;
        if (!(data.get(key) instanceof String)) throw new DomainException("INVALID_MESSAGE","Bộ lọc không hợp lệ");
        return (String)data.get(key);
    }
    private static long number(Map<String,Object> data,String key,long fallback) {
        if (!data.containsKey(key)) return fallback;
        Object value=data.get(key);
        if (!(value instanceof Long) && !(value instanceof Integer)) throw new DomainException("INVALID_MESSAGE","Thời gian/trang không hợp lệ");
        long n=((Number)value).longValue();
        if (n<0) throw new DomainException("INVALID_MESSAGE","Thời gian không hợp lệ");
        return n;
    }
}
