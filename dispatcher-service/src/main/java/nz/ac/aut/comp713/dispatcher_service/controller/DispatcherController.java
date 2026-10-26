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
import io.swagger.v3.oas.annotations.headers.Header;
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

    /**
     * Lists dispatchers, optionally filtering across name, contact, phone, and
     * address.
     */
    @Operation(summary = "List dispatchers", description = "Returns active and inactive dispatchers, or filters by a case-insensitive substring in name, contact name, phone number, or address. Search does not filter by active state.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dispatcher list returned", content = @Content(mediaType = "application/json", schema = @Schema(type = "array", implementation = DispatcherResponse.class)))
    })
    @GetMapping
    public List<DispatcherResponse> getAll(
            @Parameter(description = "Optional text to search in dispatcher name, contact, phone, or address", example = "Mere") @RequestParam(required = false) String search) {
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

    /**
     * Validates the request fields and creates a dispatcher with its resource
     * location.
     */
    @Operation(summary = "Create a dispatcher", description = "Creates a dispatcher. Browser forms require an address selected from Google Places suggestions. Dispatcher names must be unique.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Dispatcher created", headers = @Header(name = "Location", description = "URI of the new dispatcher resource", schema = @Schema(type = "string", format = "uri")), content = @Content(mediaType = "application/json", schema = @Schema(implementation = DispatcherResponse.class))),
            @ApiResponse(responseCode = "400", description = "Request data is invalid, including a missing address", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "A dispatcher with the same case- and whitespace-normalized name already exists", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping
    public ResponseEntity<DispatcherResponse> create(@Valid @RequestBody DispatcherRequest request) {
        DispatcherResponse created = dispatcherService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    /** Validates the request fields and updates the dispatcher identified by its ID. */
    @Operation(summary = "Update a dispatcher", description = "Updates the dispatcher's required name and address. Omitted contactName or phone values are cleared; omitted active preserves the current state. Browser forms require a changed address to be selected from Google Places suggestions.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dispatcher updated", content = @Content(mediaType = "application/json", schema = @Schema(implementation = DispatcherResponse.class))),
            @ApiResponse(responseCode = "400", description = "Request data is invalid, including a missing address", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Dispatcher not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Another dispatcher with the same case- and whitespace-normalized name already exists", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @PutMapping("/{id}")
    public DispatcherResponse update(
            @Parameter(description = "Dispatcher ID", required = true, schema = @Schema(type = "integer", format = "int64", minimum = "1")) @PathVariable Long id,
            @Valid @RequestBody DispatcherRequest request) {
        return dispatcherService.update(id, request);
    }
}
