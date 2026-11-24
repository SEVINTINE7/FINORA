package lk.finora.api.common.error;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Catches exceptions thrown in controllers and returns the standard error JSON (see CLAUDE.md).
 * 401 and 403 are not handled here: Spring Security sends those (see SecurityConfig).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Our own errors, e.g. throw ApiException.notFound("Account not found"). */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex, HttpServletRequest request) {
        return build(ex.getStatus(), ex.getCode(), ex.getMessage(), request);
    }

    /** A DTO with @Valid failed its checks (@NotBlank, @Size ...). Lists every bad field. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        List<ErrorResponse.FieldError> fields = new ArrayList<>();
        for (org.springframework.validation.FieldError error : ex.getBindingResult().getFieldErrors()) {
            fields.add(new ErrorResponse.FieldError(error.getField(), error.getDefaultMessage()));
        }
        return ResponseEntity.badRequest()
                .body(ErrorResponse.validation("Request validation failed", request.getRequestURI(), fields));
    }

    /** A URL or query parameter is missing or has the wrong type, e.g. /accounts/abc instead of /accounts/5. */
    @ExceptionHandler({ MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class })
    public ResponseEntity<ErrorResponse> handleBadParameter(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_ERROR,
                "A request parameter is missing or has the wrong type", request);
    }

    /** An uploaded file is over the multipart limit (1 MB, CSV import T12). */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleTooLarge(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return build(HttpStatus.CONTENT_TOO_LARGE, ErrorCodes.FILE_TOO_LARGE, "The file is larger than 1 MB", request);
    }

    /** A file upload endpoint got a request that is not multipart/form-data. */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ErrorResponse> handleBadMultipart(MultipartException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ErrorCodes.BAD_REQUEST, "Expected a multipart file upload", request);
    }

    /** A unique key clashed, e.g. the same CSV rows confirmed twice at the same moment (T12). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleConflict(DataIntegrityViolationException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ErrorCodes.CONFLICT, "This conflicts with data that already exists", request);
    }

    /** The body is empty or is not valid JSON. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleBadJson(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ErrorCodes.BAD_REQUEST, "Request body is missing or not valid JSON", request);
    }

    /** No endpoint exists at this path. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoResourceFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ErrorCodes.NOT_FOUND, "No endpoint at this path", request);
    }

    /** Anything else is a bug. Log it, but never send the details to the app. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleOther(Exception ex, HttpServletRequest request) {
        // Exception messages, stack traces and raw URLs can contain credentials or financial data.
        log.error("Unexpected server error type={}", ex.getClass().getSimpleName());
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodes.INTERNAL_ERROR, "Unexpected server error", request);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message,
            HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.of(status.value(), code, message, request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
