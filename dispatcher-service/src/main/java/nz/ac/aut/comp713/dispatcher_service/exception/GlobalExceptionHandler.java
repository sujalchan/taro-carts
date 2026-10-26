package nz.ac.aut.comp713.dispatcher_service.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import nz.ac.aut.comp713.dispatcher_service.dto.ApiError;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(DispatcherAlreadyExistsException.class)
    public ResponseEntity<ApiError> duplicate(DispatcherAlreadyExistsException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("DISPATCHER_ALREADY_EXISTS", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(DispatcherNotFoundException.class)
    public ResponseEntity<ApiError> notFound(DispatcherNotFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("DISPATCHER_NOT_FOUND", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream().findFirst()
                .map(error -> error.getDefaultMessage()).orElse("Validation failed");
        return ResponseEntity.badRequest().body(new ApiError("VALIDATION_ERROR", message, request.getRequestURI()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> conflict(DataIntegrityViolationException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("DISPATCHER_ALREADY_EXISTS", "Dispatcher already exists", request.getRequestURI()));
    }

    @ExceptionHandler(CannotAcquireLockException.class)
    public ResponseEntity<ApiError> busyDatabase(CannotAcquireLockException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", "1")
                .body(new ApiError("DATABASE_BUSY", "Profile store is busy; retry the request", request.getRequestURI()));
    }
}
