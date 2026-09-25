package nz.ac.aut.comp713.allocation_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;
import nz.ac.aut.comp713.allocation_service.dto.ApiError;

@RestControllerAdvice
public class GlobalExceptionHandler {

	// handle validation errors from request dto annotations
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiError> handleValidationException(
			MethodArgumentNotValidException exception,
			HttpServletRequest request) {

		String message = exception.getBindingResult()
				.getFieldErrors()
				.stream()
				.findFirst()
				.map(error -> error.getDefaultMessage())
				.orElse("Invalid request");

		ApiError error = new ApiError(
				"VALIDATION_ERROR",
				message,
				request.getRequestURI());

		return ResponseEntity
				.status(HttpStatus.BAD_REQUEST)
				.body(error);
	}

	// handle a customer that does not exist in customer-service
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

	// handle a taro type that does not exist in customer-service
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

	// handle an allocation that does not exist
	@ExceptionHandler(WeeklyAllocationNotFoundException.class)
	public ResponseEntity<ApiError> handleWeeklyAllocationNotFound(
			WeeklyAllocationNotFoundException exception,
			HttpServletRequest request) {

		ApiError error = new ApiError(
				"WEEKLY_ALLOCATION_NOT_FOUND",
				exception.getMessage(),
				request.getRequestURI());

		return ResponseEntity
				.status(HttpStatus.NOT_FOUND)
				.body(error);
	}

	// handle an allocation that already exists for the customer and week
	@ExceptionHandler(WeeklyAllocationAlreadyExistsException.class)
	public ResponseEntity<ApiError> handleWeeklyAllocationAlreadyExists(
			WeeklyAllocationAlreadyExistsException exception,
			HttpServletRequest request) {

		ApiError error = new ApiError(
				"WEEKLY_ALLOCATION_ALREADY_EXISTS",
				exception.getMessage(),
				request.getRequestURI());

		return ResponseEntity
				.status(HttpStatus.CONFLICT)
				.body(error);
	}

	// handle the same taro type appearing more than once in one allocation
	@ExceptionHandler(DuplicateTaroTypeException.class)
	public ResponseEntity<ApiError> handleDuplicateTaroType(
			DuplicateTaroTypeException exception,
			HttpServletRequest request) {

		ApiError error = new ApiError(
				"DUPLICATE_TARO_TYPE",
				exception.getMessage(),
				request.getRequestURI());

		return ResponseEntity
				.status(HttpStatus.BAD_REQUEST)
				.body(error);
	}

	// handle customer-service being unreachable
	@ExceptionHandler(CustomerServiceUnavailableException.class)
	public ResponseEntity<ApiError> handleCustomerServiceUnavailable(
			CustomerServiceUnavailableException exception,
			HttpServletRequest request) {

		ApiError error = new ApiError(
				"CUSTOMER_SERVICE_UNAVAILABLE",
				exception.getMessage(),
				request.getRequestURI());

		return ResponseEntity
				.status(HttpStatus.SERVICE_UNAVAILABLE)
				.body(error);
	}

	// handle invalid allocation quantity
	@ExceptionHandler(InvalidQuantityException.class)
	public ResponseEntity<ApiError> handleInvalidQuantity(
			InvalidQuantityException exception,
			HttpServletRequest request) {

		ApiError error = new ApiError(
				"INVALID_QUANTITY",
				exception.getMessage(),
				request.getRequestURI());

		return ResponseEntity
				.status(HttpStatus.BAD_REQUEST)
				.body(error);
	}

	// handle any unexpected errors
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiError> handleUnexpectedException(
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