package nz.ac.aut.comp713.allocation_service.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Standard error payload returned by allocation-service REST endpoints. */
@Schema(description = "Machine-readable API error details.")
public record ApiError(
		@Schema(description = "Stable error code", example = "DISPATCHER_REQUIRED")
		String code,
		@Schema(description = "Human-readable error detail")
		String message,
		@Schema(description = "Request path that produced the error", example = "/api/v1/allocations/24")
		String path) {
}
