package lk.finora.api.common.error;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

/** Turns every exception raised in a controller into the standard error JSON (see CLAUDE.md). */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String VALIDATION_MESSAGE = "Request validation failed";

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> handleApi(ApiException ex, HttpServletRequest req) {
        return error(ex.getStatus(), ex.getCode(), ex.getMessage(), req);
    }

    /** {@code @Valid @RequestBody} failures (MethodArgumentNotValidException) and form binding failures. */
    @ExceptionHandler(BindException.class)
    ResponseEntity<ErrorResponse> handleBind(BindException ex, HttpServletRequest req) {
        List<ErrorResponse.FieldError> fields = new ArrayList<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fields.add(new ErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()));
        }
        ex.getBindingResult().getGlobalErrors().forEach(ge ->
                fields.add(new ErrorResponse.FieldError(ge.getObjectName(), ge.getDefaultMessage())));
        return validation(fields, req);
    }

    /** Constraint annotations on controller method parameters, e.g. {@code @RequestParam @Min(1)}. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ErrorResponse> handleMethodValidation(HandlerMethodValidationException ex, HttpServletRequest req) {
        List<ErrorResponse.FieldError> fields = new ArrayList<>();
        ex.getParameterValidationResults().forEach(result -> {
            String name = result.getMethodParameter().getParameterName();
            for (MessageSourceResolvable err : result.getResolvableErrors()) {
                String field = err instanceof FieldError fe ? fe.getField() : name;
                fields.add(new ErrorResponse.FieldError(field, err.getDefaultMessage()));
            }
        });
        return validation(fields, req);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ErrorResponse> handleConstraint(ConstraintViolationException ex, HttpServletRequest req) {
        List<ErrorResponse.FieldError> fields = ex.getConstraintViolations().stream()
                .map(v -> new ErrorResponse.FieldError(lastNode(v.getPropertyPath().toString()), v.getMessage()))
                .toList();
        return validation(fields, req);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex,
            HttpServletRequest req) {
        return validation(List.of(new ErrorResponse.FieldError(ex.getParameterName(), "is required")), req);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        return validation(List.of(new ErrorResponse.FieldError(ex.getName(), "has an invalid value")), req);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return error(HttpStatus.BAD_REQUEST, ErrorCodes.BAD_REQUEST, "Request body is missing or not valid JSON", req);
    }

    @ExceptionHandler({ NoResourceFoundException.class, NoHandlerFoundException.class })
    ResponseEntity<ErrorResponse> handleNoRoute(Exception ex, HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, ErrorCodes.NOT_FOUND, "No endpoint at this path", req);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErrorResponse> handleMethod(HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, ErrorCodes.METHOD_NOT_ALLOWED,
                "Method " + ex.getMethod() + " is not supported here", req);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ErrorResponse> handleMediaType(HttpMediaTypeNotSupportedException ex, HttpServletRequest req) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ErrorCodes.UNSUPPORTED_MEDIA_TYPE,
                "Content type must be application/json", req);
    }

    /** Raised by method security inside controllers (T06); filter-level denials go through SecurityConfig. */
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ErrorResponse> handleDenied(AccessDeniedException ex, HttpServletRequest req) {
        return error(HttpStatus.FORBIDDEN, ErrorCodes.FORBIDDEN, "Access denied", req);
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ErrorResponse> handleAuth(AuthenticationException ex, HttpServletRequest req) {
        return error(HttpStatus.UNAUTHORIZED, ErrorCodes.UNAUTHORIZED, "Authentication required", req);
    }

    /** Last resort: never leak exception details to the client. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest req) {
        log.error("Unhandled error on {} {}", req.getMethod(), req.getRequestURI(), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodes.INTERNAL_ERROR, "Unexpected server error", req);
    }

    private static ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message,
            HttpServletRequest req) {
        return ResponseEntity.status(status).body(ErrorResponse.of(status.value(), code, message, req.getRequestURI()));
    }

    private static ResponseEntity<ErrorResponse> validation(List<ErrorResponse.FieldError> fields,
            HttpServletRequest req) {
        return ResponseEntity.badRequest().body(ErrorResponse.validation(VALIDATION_MESSAGE, req.getRequestURI(), fields));
    }

    private static String lastNode(String path) {
        int dot = path.lastIndexOf('.');
        return dot >= 0 ? path.substring(dot + 1) : path;
    }
}
