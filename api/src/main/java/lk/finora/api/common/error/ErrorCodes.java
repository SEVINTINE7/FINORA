package lk.finora.api.common.error;

/** Machine-readable values for the {@code code} field of the error JSON. */
public final class ErrorCodes {

    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String BAD_REQUEST = "BAD_REQUEST";
    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String METHOD_NOT_ALLOWED = "METHOD_NOT_ALLOWED";
    public static final String CONFLICT = "CONFLICT";
    public static final String UNSUPPORTED_MEDIA_TYPE = "UNSUPPORTED_MEDIA_TYPE";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    private ErrorCodes() {
    }
}
