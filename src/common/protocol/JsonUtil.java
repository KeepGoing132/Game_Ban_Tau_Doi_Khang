package common.protocol;

import java.util.*;

/**
 * Tiện ích xử lý JSON thuần Java (Zero-dependency), an toàn, tốc độ cao.
 * Hỗ trợ parse Object, Array, String, Number, Boolean, Null và serialize.
 */
public class JsonUtil {

    // ==========================================
    // SERIALIZER
    // ==========================================

    public static String toJson(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof String) {
            return "\"" + escapeString((String) obj) + "\"";
        }
        if (obj instanceof Number || obj instanceof Boolean) {
            return obj.toString();
        }
        if (obj instanceof Enum) {
            return "\"" + ((Enum<?>) obj).name() + "\"";
        }
        if (obj instanceof Map) {
            StringBuilder sb = new StringBuilder("{");
            Map<?, ?> map = (Map<?, ?>) obj;
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) sb.append(",");
                first = false;
                sb.append("\"").append(escapeString(String.valueOf(entry.getKey()))).append("\":");
                sb.append(toJson(entry.getValue()));
            }
            sb.append("}");
            return sb.toString();
        }
        if (obj instanceof Collection) {
            StringBuilder sb = new StringBuilder("[");
            Collection<?> col = (Collection<?>) obj;
            boolean first = true;
            for (Object item : col) {
                if (!first) sb.append(",");
                first = false;
                sb.append(toJson(item));
            }
            sb.append("]");
            return sb.toString();
        }
        if (obj.getClass().isArray()) {
            StringBuilder sb = new StringBuilder("[");
            int len = java.lang.reflect.Array.getLength(obj);
            for (int i = 0; i < len; i++) {
                if (i > 0) sb.append(",");
                sb.append(toJson(java.lang.reflect.Array.get(obj, i)));
            }
            sb.append("]");
            return sb.toString();
        }

        // Với Java POJO thông thường, dùng reflection đọc getters hoặc fields
        return pojoToJson(obj);
    }

    private static String pojoToJson(Object obj) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        java.lang.reflect.Field[] fields = obj.getClass().getDeclaredFields();
        for (java.lang.reflect.Field field : fields) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            try {
                Object val = field.get(obj);
                if (!first) sb.append(",");
                first = false;
                sb.append("\"").append(field.getName()).append("\":").append(toJson(val));
            } catch (IllegalAccessException ignored) {
            }
        }
        sb.append("}");
        return sb.toString();
    }

    private static String escapeString(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < ' ') {
                        String t = "000" + Integer.toHexString(c);
                        sb.append("\\u").append(t.substring(t.length() - 4));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    // ==========================================
    // PARSER
    // ==========================================

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String json) {
        if (json == null) return new LinkedHashMap<>();
        Object parsed = parse(json);
        if (parsed instanceof Map) {
            return (Map<String, Object>) parsed;
        }
        throw new IllegalArgumentException("Expected JSON object");
    }

    @SuppressWarnings("unchecked")
    public static List<Object> parseList(String json) {
        if (json == null) return new ArrayList<>();
        Object parsed = parse(json);
        if (parsed instanceof List) {
            return (List<Object>) parsed;
        }
        throw new IllegalArgumentException("Expected JSON array");
    }

    public static Object parse(String json) {
        if (json == null) return null;
        if (json.isEmpty()) return null;
        JsonParser parser = new JsonParser(json);
        Object value = parser.parseValue();
        parser.skipWhitespace();
        if (parser.idx != json.length()) throw new IllegalArgumentException("Trailing JSON data");
        return value;
    }

    private static class JsonParser {
        private final String src;
        private int idx = 0;
        private int depth = 0;

        JsonParser(String src) {
            this.src = src;
        }

        private void skipWhitespace() {
            while (idx < src.length() && " \t\r\n".indexOf(src.charAt(idx)) >= 0) {
                idx++;
            }
        }

        private char peek() {
            skipWhitespace();
            if (idx >= src.length()) return '\0';
            return src.charAt(idx);
        }

        private char next() {
            skipWhitespace();
            if (idx >= src.length()) return '\0';
            return src.charAt(idx++);
        }

        Object parseValue() {
            if (++depth > 64) throw new IllegalArgumentException("JSON nesting too deep");
            try {
            char c = peek();
            if (c == '{') return parseObject();
            if (c == '[') return parseArray();
            if (c == '"') return parseString();
            if (c == 't' || c == 'f') return parseBoolean();
            if (c == 'n') return parseNull();
            if (c == '-' || Character.isDigit(c)) return parseNumber();
            throw new IllegalArgumentException("Ký tự JSON không hợp lệ tại vị trí " + idx + ": " + c);
            } finally { depth--; }
        }

        Map<String, Object> parseObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            next(); // Bỏ '{'
            skipWhitespace();
            if (peek() == '}') {
                next();
                return map;
            }

            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                if (next() != ':') {
                    throw new IllegalArgumentException("Mong đợi ':' sau key tại vị trí " + idx);
                }
                Object val = parseValue();
                if (map.containsKey(key)) throw new IllegalArgumentException("Duplicate JSON key");
                map.put(key, val);
                skipWhitespace();
                char c = next();
                if (c == '}') break;
                if (c != ',') {
                    throw new IllegalArgumentException("Mong đợi ',' hoặc '}' tại vị trí " + idx);
                }
            }
            return map;
        }

        List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            next(); // Bỏ '['
            skipWhitespace();
            if (peek() == ']') {
                next();
                return list;
            }

            while (true) {
                Object val = parseValue();
                list.add(val);
                skipWhitespace();
                char c = next();
                if (c == ']') break;
                if (c != ',') {
                    throw new IllegalArgumentException("Mong đợi ',' hoặc ']' tại vị trí " + idx);
                }
            }
            return list;
        }

        String parseString() {
            if (next() != '"') throw new IllegalArgumentException("Mong đợi '\"' tại vị trí " + idx);
            StringBuilder sb = new StringBuilder();
            while (idx < src.length()) {
                char c = src.charAt(idx++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    if (idx >= src.length()) break;
                    char esc = src.charAt(idx++);
                    switch (esc) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'u':
                            if (idx + 4 <= src.length()) {
                                String hex = src.substring(idx, idx + 4);
                                idx += 4;
                                sb.append((char) Integer.parseInt(hex, 16));
                            } else throw new IllegalArgumentException("Invalid unicode escape");
                            break;
                        default: throw new IllegalArgumentException("Invalid escape");
                    }
                } else {
                    if (c < 32) throw new IllegalArgumentException("Unescaped control character");
                    sb.append(c);
                }
            }
            throw new IllegalArgumentException("Chuỗi không đóng dấu ngoặc kép");
        }

        Boolean parseBoolean() {
            if (src.startsWith("true", idx)) {
                idx += 4;
                return Boolean.TRUE;
            }
            if (src.startsWith("false", idx)) {
                idx += 5;
                return Boolean.FALSE;
            }
            throw new IllegalArgumentException("Boolean không hợp lệ tại vị trí " + idx);
        }

        Object parseNull() {
            if (src.startsWith("null", idx)) {
                idx += 4;
                return null;
            }
            throw new IllegalArgumentException("Null không hợp lệ tại vị trí " + idx);
        }

        Number parseNumber() {
            int start = idx;
            if (peek() == '-') idx++;
            while (idx < src.length() && (Character.isDigit(src.charAt(idx)) || src.charAt(idx) == '.' || src.charAt(idx) == 'e' || src.charAt(idx) == 'E' || src.charAt(idx) == '+' || src.charAt(idx) == '-')) {
                idx++;
            }
            String numStr = src.substring(start, idx);
            if (!numStr.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")) throw new IllegalArgumentException("Invalid number");
            if (numStr.contains(".") || numStr.contains("e") || numStr.contains("E")) {
                double value = Double.parseDouble(numStr);
                if (Double.isInfinite(value) || Double.isNaN(value)) throw new IllegalArgumentException("Invalid number");
                return value;
            }
            try {
                return Integer.parseInt(numStr);
            } catch (NumberFormatException e) {
                return Long.parseLong(numStr);
            }
        }
    }

    // ==========================================
    // HELPER METHODS
    // ==========================================

    public static String getString(Map<String, Object> map, String key, String def) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) return def;
        return String.valueOf(map.get(key));
    }

    public static int getInt(Map<String, Object> map, String key, int def) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) return def;
        Object val = map.get(key);
        if (val instanceof Number) return ((Number) val).intValue();
        try {
            return Integer.parseInt(String.valueOf(val));
        } catch (Exception e) {
            return def;
        }
    }

    public static double getDouble(Map<String, Object> map, String key, double def) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) return def;
        Object val = map.get(key);
        if (val instanceof Number) return ((Number) val).doubleValue();
        try {
            return Double.parseDouble(String.valueOf(val));
        } catch (Exception e) {
            return def;
        }
    }

    public static long getLong(Map<String,Object> map,String key,long def) {
        Object value=map==null?null:map.get(key);
        return value instanceof Number?((Number)value).longValue():def;
    }

    public static boolean getBoolean(Map<String, Object> map, String key, boolean def) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) return def;
        Object val = map.get(key);
        if (val instanceof Boolean) return (Boolean) val;
        return Boolean.parseBoolean(String.valueOf(val));
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> getMap(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key)) return null;
        Object val = map.get(key);
        if (val instanceof Map) return (Map<String, Object>) val;
        return null;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> getList(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key)) return null;
        Object val = map.get(key);
        if (val instanceof List) return (List<Object>) val;
        return null;
    }
    public static int requireInt(Map<String, Object> map, String key) {
        Object val = map == null ? null : map.get(key);
        if (!(val instanceof Integer) && !(val instanceof Long)) throw new IllegalArgumentException("INVALID_MESSAGE");
        long n = ((Number) val).longValue();
        if (n < Integer.MIN_VALUE || n > Integer.MAX_VALUE) throw new IllegalArgumentException("INVALID_MESSAGE");
        return (int) n;
    }

}
