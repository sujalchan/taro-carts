package nz.ac.aut.comp713.customer_service.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.CannotAcquireLockException;

import jakarta.servlet.http.HttpServletRequest;
import nz.ac.aut.comp713.customer_service.dto.ApiError;

// converts exceptions thrown by the REST API into consistent JSON error responses
@RestControllerAdvice
public class GlobalExceptionHandler {

	// return 404 when a requested customer does not exist
	@ExceptionHandler(CustomerNotFoundException.class)
	public ResponseEntity<ApiError> handleCustomerNotFound(
			CustomerNotFoundException exception,
			HttpServletRequest request) {

		ApiError error = new ApiError(
				"CUSTOMER_NOT_FOUND",
				exception.getMessage(),
				request.getRequestURI());

		return ResponseEntity
				.status(HttpStatus.NOT_FOUND)
				.body(error);
	}

	// return 409 when a customer conflicts with an existing name
	@ExceptionHandler(CustomerAlreadyExistsException.class)
	public ResponseEntity<ApiError> handleCustomerAlreadyExists(
			CustomerAlreadyExistsException exception,
			HttpServletRequest request) {

		ApiError error = new ApiError(
				"CUSTOMER_ALREADY_EXISTS",
				exception.getMessage(),
				request.getRequestURI());

		return ResponseEntity
				.status(HttpStatus.CONFLICT)
				.body(error);
	}

	// return the first validation message when @Valid request data is invalid
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiError> handleValidationError(
			MethodArgumentNotValidException exception,
			HttpServletRequest request) {

		String message = exception
				.getBindingResult()
				.getFieldErrors()
				.stream()
				.findFirst()
				.map(error -> error.getDefaultMessage())
				.orElse("Validation failed");

		ApiError error = new ApiError(
				"VALIDATION_ERROR",
				message,
				request.getRequestURI());

		return ResponseEntity
				.badRequest()
				.body(error);
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ApiError> handleMissingResource(
			NoResourceFoundException exception,
			HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(new ApiError("RESOURCE_NOT_FOUND", "Resource not found", request.getRequestURI()));
	}

	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<ApiError> handleResponseStatus(ResponseStatusException exception,
			HttpServletRequest request) {
		if (exception.getStatusCode().value() == 503) {
			String reason = exception.getReason();
			String code = switch (reason == null ? "" : reason) {
				case "Allocation Service is unavailable." -> "ALLOCATION_SERVICE_UNAVAILABLE";
				case "Customer Service is unavailable." -> "CUSTOMER_SERVICE_UNAVAILABLE";
				case "Dispatcher Service is unavailable." -> "DISPATCHER_SERVICE_UNAVAILABLE";
				case "Taro Service is unavailable." -> "TARO_SERVICE_UNAVAILABLE";
				case "Login Service is unavailable.",
					"Password settings are temporarily unavailable. Please try again later." -> "LOGIN_SERVICE_UNAVAILABLE";
				default -> "CUSTOMER_SERVICE_UNAVAILABLE";
			};
			String message = switch (code) {
				case "ALLOCATION_SERVICE_UNAVAILABLE" -> "Allocation Service is unavailable.";
				case "DISPATCHER_SERVICE_UNAVAILABLE" -> "Dispatcher Service is unavailable.";
				case "TARO_SERVICE_UNAVAILABLE" -> "Taro Service is unavailable.";
				case "LOGIN_SERVICE_UNAVAILABLE" -> "Login Service is unavailable.";
				default -> "Customer Service is unavailable.";
			};
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
					.body(new ApiError(code, message, request.getRequestURI()));
		}
		String message = exception.getStatusCode().value() == 403 ? "Access denied" : "Request cannot be completed";
		return ResponseEntity.status(exception.getStatusCode())
				.body(new ApiError("REQUEST_DENIED", message, request.getRequestURI()));
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ApiError> handleUnsupportedMethod(HttpRequestMethodNotSupportedException exception,
			HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
				.body(new ApiError("METHOD_NOT_ALLOWED", "Method not allowed", request.getRequestURI()));
	}

	@ExceptionHandler(CannotAcquireLockException.class)
	public ResponseEntity<ApiError> handleBusyDatabase(CannotAcquireLockException exception,
			HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
				.header("Retry-After", "1")
				.body(new ApiError("DATABASE_BUSY", "Profile store is busy; retry the request", request.getRequestURI()));
	}

	// catch any unhandled exception and avoid exposing internal implementation
	// details
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiError> handleGenericException(
			Exception exception,
			HttpServletRequest request) {

		ApiError error = new ApiError(
				"INTERNAL_SERVER_ERROR",
				"An unexpected error occurred",
				request.getRequestURI());

		return ResponseEntity
				.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(error);
	}
}
