package lk.finora.api.common.error;

/** Machine-readable values for the {@code code} field of the error JSON. */
public final class ErrorCodes {

    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String BAD_REQUEST = "BAD_REQUEST";
    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String CONFLICT = "CONFLICT";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    // Auth (T05)
    public static final String EMAIL_TAKEN = "EMAIL_TAKEN";
    public static final String UNDER_18 = "UNDER_18";
    public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
    public static final String ACCOUNT_DISABLED = "ACCOUNT_DISABLED";
    public static final String INVALID_REFRESH_TOKEN = "INVALID_REFRESH_TOKEN";

    // Records (T09)
    public static final String IN_USE = "IN_USE";

    // CSV import (T12)
    public static final String FILE_TOO_LARGE = "FILE_TOO_LARGE";

    private ErrorCodes() {
    }
}
