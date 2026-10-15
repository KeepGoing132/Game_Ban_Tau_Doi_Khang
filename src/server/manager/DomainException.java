package server.manager;

public class DomainException extends RuntimeException {
    public final String code;
    public final java.util.Map<String,Object> data = new java.util.LinkedHashMap<>();
    public DomainException withData(java.util.Map<String,Object> values) { data.putAll(values); return this; }
    public DomainException(String code) { super(code); this.code = code; }
    public DomainException(String code, String message) { super(message); this.code = code; }
}
