package nz.ac.aut.comp713.dispatcher_service.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Dispatcher representation returned by the API. */
@Schema(description = "Saved dispatcher details.")
public record DispatcherResponse(
        @Schema(description = "Dispatcher ID") Long id,
        @Schema(description = "Dispatcher's display name") String name,
        @Schema(description = "Contact person's name, if provided") String contactName,
        @Schema(description = "Phone number, if provided") String phone,
        @Schema(description = "Validated dispatcher address") String address,
        @Schema(description = "Whether the dispatcher is active") boolean active) {
}
