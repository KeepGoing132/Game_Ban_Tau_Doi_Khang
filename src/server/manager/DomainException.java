package server.manager;

import java.util.LinkedHashMap;
import java.util.Map;

/** Domain failure with a protocol error code and optional extra fields for the client. */
public class DomainException extends RuntimeException {
    public final String code;
    public final Map<String, Object> data = new LinkedHashMap<>();

    public DomainException(String code) {
        super(code);
        this.code = code;
    }

    public DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    /** Adds protocol fields (user id, remaining penalty time, …) to the error response. */
    public DomainException withData(Map<String, Object> values) {
        data.putAll(values);
        return this;
    }
}
