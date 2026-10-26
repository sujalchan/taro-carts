package nz.ac.aut.comp713.dispatcher_service.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Standard error payload returned by dispatcher-service REST endpoints. */
@Schema(description = "Machine-readable API error details.")
public record ApiError(
        @Schema(description = "Stable error code", example = "VALIDATION_ERROR") String code,
        @Schema(description = "Human-readable error detail") String message,
        @Schema(description = "Request path that produced the error", example = "/api/v1/dispatchers") String path) {
}
