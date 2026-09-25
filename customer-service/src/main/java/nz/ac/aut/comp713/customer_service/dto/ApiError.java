package nz.ac.aut.comp713.customer_service.dto;

// API error response DTO
public record ApiError(
		String code,
		String message,
		String path) {
}
