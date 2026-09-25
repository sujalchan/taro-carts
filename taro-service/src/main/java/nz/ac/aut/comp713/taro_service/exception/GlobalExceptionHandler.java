package nz.ac.aut.comp713.taro_service.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;
import nz.ac.aut.comp713.taro_service.dto.ApiError;

// converts exceptions thrown by the REST API into consistent JSON error responses
@RestControllerAdvice
public class GlobalExceptionHandler {

	// return 404 when a requested taro type does not exist
	@ExceptionHandler(TaroTypeNotFoundException.class)
	public ResponseEntity<ApiError> handleTaroTypeNotFound(
			TaroTypeNotFoundException exception,
			HttpServletRequest request) {

		ApiError error = new ApiError(
				"TARO_TYPE_NOT_FOUND",
				exception.getMessage(),
				request.getRequestURI());

		return ResponseEntity
				.status(HttpStatus.NOT_FOUND)
				.body(error);
	}

	// return 409 when a taro type conflicts with an existing name
	@ExceptionHandler(TaroTypeAlreadyExistsException.class)
	public ResponseEntity<ApiError> handleTaroTypeAlreadyExists(
			TaroTypeAlreadyExistsException exception,
			HttpServletRequest request) {

		ApiError error = new ApiError(
				"TARO_TYPE_ALREADY_EXISTS",
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