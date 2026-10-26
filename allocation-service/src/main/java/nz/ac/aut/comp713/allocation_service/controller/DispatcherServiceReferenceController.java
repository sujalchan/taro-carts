package nz.ac.aut.comp713.allocation_service.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import nz.ac.aut.comp713.allocation_service.client.DispatcherClient;
import nz.ac.aut.comp713.allocation_service.client.DispatcherResponse;
import nz.ac.aut.comp713.allocation_service.dto.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Provides dispatcher data needed by allocation workflows. */
@RestController
@RequestMapping("/api/v1/dispatcher-service-reference")
@Tag(name = "Dispatcher References", description = "Dispatcher data exposed for allocation workflows")
public class DispatcherServiceReferenceController {
    private final DispatcherClient dispatcherClient;

    /** Creates the controller with the dispatcher service client. */
    public DispatcherServiceReferenceController(DispatcherClient dispatcherClient) {
        this.dispatcherClient = dispatcherClient;
    }

    /** Lists dispatchers available for assignment to allocations. */
    @Operation(summary = "List dispatchers for allocations", description = "Returns dispatchers for allocation assignment. Allocation updates must use a valid dispatcher ID for AWAITING_DISPATCH, IN_TRANSIT, and DELIVERED statuses.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dispatcher list retrieved", content = @Content(mediaType = "application/json", schema = @Schema(type = "array", implementation = DispatcherResponse.class))),
            @ApiResponse(responseCode = "503", description = "Dispatcher service is unavailable", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/dispatchers")
    public List<DispatcherResponse> getDispatchers() {
        return dispatcherClient.getDispatchers();
    }
}
