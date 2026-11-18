package lk.finora.api.common.error;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Error JSON returned by every endpoint: {@code {"timestamp","status","code","message","path"}}.
 * {@code fields} is present only for {@code VALIDATION_ERROR}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        List<FieldError> fields) {

    public record FieldError(String field, String message) {
    }

    public static ErrorResponse of(int status, String code, String message, String path) {
        return new ErrorResponse(Instant.now(), status, code, message, path, null);
    }

    public static ErrorResponse validation(String message, String path, List<FieldError> fields) {
        return new ErrorResponse(Instant.now(), 400, ErrorCodes.VALIDATION_ERROR, message, path, fields);
    }
}
