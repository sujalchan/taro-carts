package nz.ac.aut.comp713.taro_service.dto;

// API error response DTO
public record ApiError(
		String code,
		String message,
		String path) {
}
