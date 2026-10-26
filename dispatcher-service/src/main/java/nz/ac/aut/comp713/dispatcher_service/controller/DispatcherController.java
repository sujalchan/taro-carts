package nz.ac.aut.comp713.dispatcher_service.controller;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import nz.ac.aut.comp713.dispatcher_service.dto.ApiError;
import nz.ac.aut.comp713.dispatcher_service.dto.DispatcherRequest;
import nz.ac.aut.comp713.dispatcher_service.dto.DispatcherResponse;
import nz.ac.aut.comp713.dispatcher_service.service.DispatcherService;

/** REST API for listing, creating, and updating dispatcher records. */
@RestController
@RequestMapping("/api/v1/dispatchers")
@Tag(name = "Dispatchers", description = "Dispatcher records used to manage delivery assignments")
public class DispatcherController {
    private final DispatcherService dispatcherService;

    /** Creates the controller with its dispatcher business service. */
    public DispatcherController(DispatcherService dispatcherService) {
        this.dispatcherService = dispatcherService;
    }

    /** Lists dispatchers, optionally filtering across name, contact, phone, and address. */
    @Operation(summary = "List dispatchers", description = "Returns all dispatchers, or filters by a case-insensitive substring in name, contact name, phone number, or address.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dispatcher list returned", content = @Content(mediaType = "application/json", schema = @Schema(type = "array", implementation = DispatcherResponse.class)))
    })
    @GetMapping
    public List<DispatcherResponse> getAll(
            @Parameter(description = "Optional text to search in dispatcher name, contact, phone, or address", example = "Mere")
            @RequestParam(required = false) String search) {
        return dispatcherService.getAll(search);
    }

    /** Returns one dispatcher by its ID. */
    @Operation(summary = "Get a dispatcher by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dispatcher returned", content = @Content(mediaType = "application/json", schema = @Schema(implementation = DispatcherResponse.class))),
            @ApiResponse(responseCode = "404", description = "Dispatcher not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/{id}")
    public DispatcherResponse getById(
            @Parameter(description = "Dispatcher ID", required = true, schema = @Schema(type = "integer", format = "int64", minimum = "1")) @PathVariable Long id) {
        return dispatcherService.getById(id);
    }

    /** Validates and creates a dispatcher, returning its location and representation. */
    @Operation(summary = "Create a dispatcher", description = "Creates a dispatcher after validating the supplied address against the address validation API. Dispatcher names must be unique.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Dispatcher created", content = @Content(mediaType = "application/json", schema = @Schema(implementation = DispatcherResponse.class))),
            @ApiResponse(responseCode = "400", description = "Request validation failed or the address was not a complete valid match", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "A dispatcher with this name already exists", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "503", description = "Address validation service is unavailable", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping
    public ResponseEntity<DispatcherResponse> create(@Valid @RequestBody DispatcherRequest request) {
        DispatcherResponse created = dispatcherService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    /** Validates and updates the dispatcher identified by the path ID. */
    @Operation(summary = "Update a dispatcher", description = "Replaces the dispatcher's editable details and validates its address. If active is omitted, the current active state is preserved.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dispatcher updated", content = @Content(mediaType = "application/json", schema = @Schema(implementation = DispatcherResponse.class))),
            @ApiResponse(responseCode = "400", description = "Request validation failed or the address was not a complete valid match", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Dispatcher not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Another dispatcher with this name already exists", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "503", description = "Address validation service is unavailable", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @PutMapping("/{id}")
    public DispatcherResponse update(
            @Parameter(description = "Dispatcher ID", required = true, schema = @Schema(type = "integer", format = "int64", minimum = "1")) @PathVariable Long id,
            @Valid @RequestBody DispatcherRequest request) {
        return dispatcherService.update(id, request);
    }
}
