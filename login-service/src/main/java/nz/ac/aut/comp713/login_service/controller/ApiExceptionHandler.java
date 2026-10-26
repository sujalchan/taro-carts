package nz.ac.aut.comp713.login_service.controller;

import jakarta.servlet.http.HttpServletRequest;
import nz.ac.aut.comp713.login_service.dto.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.converter.HttpMessageNotReadableException;

/** Keeps login-service REST errors in the same code/message/path format as the other services. */
@RestControllerAdvice(annotations = org.springframework.web.bind.annotation.RestController.class)
public class ApiExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleStatus(ResponseStatusException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        return ResponseEntity.status(status)
                .body(new ApiError(codeFor(status), safeReason(exception.getReason(), status), request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception,
                                                      HttpServletRequest request) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst().map(error -> error.getDefaultMessage())
                .filter(value -> value != null && !value.isBlank())
                .orElse("Request validation failed.");
        return ResponseEntity.badRequest()
                .body(new ApiError("VALIDATION_ERROR", message, request.getRequestURI()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException exception,
                                                      HttpServletRequest request) {
        return ResponseEntity.badRequest().body(new ApiError(
                "INVALID_REQUEST", "Request body is missing or invalid.", request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
        if (exception instanceof ErrorResponse error) {
            HttpStatus status = HttpStatus.valueOf(error.getStatusCode().value());
            return ResponseEntity.status(status).body(new ApiError(codeFor(status),
                    safeReason(error.getBody().getDetail(), status), request.getRequestURI()));
        }
        LOGGER.error("API request failed: {}", request.getRequestURI(), exception);
        return ResponseEntity.internalServerError().body(new ApiError(
                "INTERNAL_SERVER_ERROR", "An unexpected error occurred. Please try again.", request.getRequestURI()));
    }

    private static String codeFor(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "BAD_REQUEST";
            case UNAUTHORIZED -> "UNAUTHORIZED";
            case FORBIDDEN -> "FORBIDDEN";
            case NOT_FOUND -> "NOT_FOUND";
            case CONFLICT -> "CONFLICT";
            case TOO_MANY_REQUESTS -> "TOO_MANY_REQUESTS";
            case SERVICE_UNAVAILABLE -> "SERVICE_UNAVAILABLE";
            default -> status.is5xxServerError() ? "INTERNAL_SERVER_ERROR" : "REQUEST_FAILED";
        };
    }

    private static String safeReason(String reason, HttpStatus status) {
        if (reason != null && !reason.isBlank()) return reason;
        return switch (status) {
            case UNAUTHORIZED -> "Sign in required.";
            case FORBIDDEN -> "Access denied.";
            case NOT_FOUND -> "Resource not found.";
            case TOO_MANY_REQUESTS -> "Too many requests. Please wait and try again.";
            case SERVICE_UNAVAILABLE -> "Service temporarily unavailable. Please try again shortly.";
            default -> status.is4xxClientError() ? "The request could not be completed." : "An unexpected error occurred.";
        };
    }
}
