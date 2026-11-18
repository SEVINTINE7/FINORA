package lk.finora.api.common.error;

import org.springframework.http.HttpStatus;

/**
 * Throw from services to return the standard error JSON with a given code and HTTP status.
 * Another user's resource must use {@link #notFound(String)} (404, never 403).
 */
public class ApiException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public ApiException(String code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public static ApiException notFound(String message) {
        return new ApiException(ErrorCodes.NOT_FOUND, HttpStatus.NOT_FOUND, message);
    }

    public static ApiException badRequest(String message) {
        return new ApiException(ErrorCodes.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, message);
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
