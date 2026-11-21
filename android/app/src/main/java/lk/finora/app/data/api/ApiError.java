package lk.finora.app.data.api;

import java.util.List;

public final class ApiError {
    public String timestamp;
    public int status;
    public String code;
    public String message;
    public String path;
    public List<FieldError> fields;

    public static final class FieldError {
        public String field;
        public String message;
    }
}
