package nz.ac.aut.comp713.allocation_service.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import nz.ac.aut.comp713.allocation_service.dto.WeeklyAllocationRequest;
import nz.ac.aut.comp713.allocation_service.dto.WeeklyAllocationResponse;
import nz.ac.aut.comp713.allocation_service.service.WeeklyAllocationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import nz.ac.aut.comp713.allocation_service.dto.ApiError;
import nz.ac.aut.comp713.allocation_service.exception.InvalidCustomerFilterException;
import nz.ac.aut.comp713.allocation_service.exception.InvalidActingCustomerException;

/** REST endpoints for creating and managing weekly allocations. */
@RestController
@RequestMapping("/api/v1/allocations")
@Tag(name = "Weekly Allocations", description = "Allocation creation, fulfilment, payment, and customer request operations")
public class WeeklyAllocationController {

	private final WeeklyAllocationService weeklyAllocationService;

	/** Creates the controller with the allocation business service. */
	public WeeklyAllocationController(WeeklyAllocationService weeklyAllocationService) {
		this.weeklyAllocationService = weeklyAllocationService;
	}

	/** Returns all allocations, optionally restricted to one customer. */
	@Operation(summary = "Retrieve weekly allocations", description = "Optionally filter allocations by a positive customer ID. Returned records include dispatcherId when assigned.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Weekly allocations retrieved successfully", content = @Content(mediaType = "application/json", schema = @Schema(type = "array", implementation = WeeklyAllocationResponse.class))),
			@ApiResponse(responseCode = "400", description = "Customer ID filter must be a positive integer", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "503", description = "Customer or taro service is unavailable", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
	})
	@GetMapping
	public ResponseEntity<List<WeeklyAllocationResponse>> getAllWeeklyAllocations(
			@Parameter(description = "Return only allocations for this customer. Must be a positive integer ID.", schema = @Schema(type = "integer", format = "int64", minimum = "1"))
			@RequestParam(required = false) String customerId) {
		Long parsedCustomerId = null;
		if (customerId != null) {
			try {
				parsedCustomerId = Long.valueOf(customerId);
				if (parsedCustomerId <= 0) {
					throw new InvalidCustomerFilterException();
				}
			} catch (NumberFormatException exception) {
				throw new InvalidCustomerFilterException();
			}
		}
		return ResponseEntity.ok(weeklyAllocationService.getWeeklyAllocations(parsedCustomerId));
	}

	/** Returns the allocation identified by the path ID. */
	@Operation(summary = "Retrieve a weekly allocation by ID", description = "Returns allocation details, including dispatcherId when one is assigned.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Weekly allocation retrieved successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = WeeklyAllocationResponse.class))),
			@ApiResponse(responseCode = "404", description = "Weekly allocation not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "503", description = "Customer or taro service is unavailable", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
	})
	@GetMapping("/{id}")
	public ResponseEntity<WeeklyAllocationResponse> getWeeklyAllocationById(
			@Parameter(description = "Allocation identifier", required = true, schema = @Schema(type = "integer", format = "int64", minimum = "1")) @PathVariable Long id) {
		return ResponseEntity.ok(weeklyAllocationService.getWeeklyAllocationById(id));
	}

	/** Marks an allocation as paid for its owning customer. */
	@Operation(summary = "Record a customer's allocation as paid", description = "Marks an eligible allocation as paid after verifying the supplied customer owns it.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Allocation marked as paid", content = @Content(mediaType = "application/json", schema = @Schema(implementation = WeeklyAllocationResponse.class))),
			@ApiResponse(responseCode = "400", description = "Customer ID must be positive", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "403", description = "Allocation belongs to another customer", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "404", description = "Allocation not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "409", description = "Allocation cannot be paid in its current status or is already paid", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
	})
	@PostMapping("/{id}/pay")
	public ResponseEntity<WeeklyAllocationResponse> recordCustomerPayment(
			@Parameter(description = "Allocation identifier", required = true, schema = @Schema(type = "integer", format = "int64", minimum = "1")) @PathVariable Long id,
			@Parameter(description = "Customer claiming payment; must be a positive ID and own the allocation", required = true, schema = @Schema(type = "integer", format = "int64", minimum = "1"))
			@RequestParam Long customerId) {
		if (customerId <= 0) {
			throw new InvalidActingCustomerException();
		}
		return ResponseEntity.ok(weeklyAllocationService.recordCustomerPayment(id, customerId));
	}

	/** Validates and creates a weekly allocation, returning its resource location. */
	@Operation(summary = "Create a weekly allocation", description = "Creates an allocation using customer and taro type IDs. Creation applies the service's default delivery and payment statuses.")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Weekly allocation created successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = WeeklyAllocationResponse.class))),
			@ApiResponse(responseCode = "400", description = "Invalid allocation data, duplicate taro type, or a required dispatcher ID is missing", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "404", description = "Customer or taro type not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "409", description = "Weekly allocation already exists for the customer and week", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "503", description = "Customer or taro service is unavailable", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
	})
	@PostMapping
	public ResponseEntity<WeeklyAllocationResponse> createWeeklyAllocation(
			@Valid @RequestBody WeeklyAllocationRequest request) {

		WeeklyAllocationResponse response = weeklyAllocationService.createWeeklyAllocation(request);

		// build the URI of the newly created allocation for the Location header
		URI location = URI.create("/api/v1/allocations/" + response.id());

		// return 201 Created with the new resource location and response body
		return ResponseEntity
				.created(location)
				.body(response);
	}

	/** Creates an allocation request through the customer-facing API. */
	@Operation(summary = "Request a weekly allocation as a customer", description = "Creates a customer allocation request. Delivery and payment statuses are assigned by the service.")
	@PostMapping("/customer-request")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Customer allocation request created", content = @Content(mediaType = "application/json", schema = @Schema(implementation = WeeklyAllocationResponse.class))),
			@ApiResponse(responseCode = "400", description = "Invalid request data; customer requests cannot set delivery or payment status", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "404", description = "Customer or taro type not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "409", description = "Weekly allocation already exists for the customer and week", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "503", description = "Customer or taro service is unavailable", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
	})
	public ResponseEntity<WeeklyAllocationResponse> createCustomerRequest(
			@Valid @RequestBody WeeklyAllocationRequest request) {
		WeeklyAllocationResponse response = weeklyAllocationService.createCustomerRequest(request);
		return ResponseEntity.created(URI.create("/api/v1/allocations/" + response.id())).body(response);
	}

	/** Validates and updates the allocation identified by the path ID. */
	@Operation(summary = "Update an existing weekly allocation", description = "Updates allocation details and workflow statuses. A valid dispatcherId is required when deliveryStatus is AWAITING_DISPATCH, IN_TRANSIT, or DELIVERED; the ID is validated with dispatcher-service.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Weekly allocation updated successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = WeeklyAllocationResponse.class))),
			@ApiResponse(responseCode = "400", description = "Invalid allocation data or duplicate taro type", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "404", description = "Allocation, customer, taro type, or selected dispatcher not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "409", description = "Another weekly allocation already exists for the customer and week", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "503", description = "Customer, taro, or dispatcher service is unavailable", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
	})
	@PutMapping("/{id}")
	public ResponseEntity<WeeklyAllocationResponse> updateWeeklyAllocation(
			@Parameter(description = "Allocation identifier", required = true, schema = @Schema(type = "integer", format = "int64", minimum = "1")) @PathVariable Long id,
			@Valid @RequestBody WeeklyAllocationRequest request) {

		return ResponseEntity.ok(
				weeklyAllocationService.updateWeeklyAllocation(
						id,
						request));
	}

	// delete a request only when it was created through the customer-facing API
	@Operation(summary = "Delete a customer's own requested allocation", description = "Deletes only an allocation created through the customer request endpoint while it remains eligible for cancellation.")
	@ApiResponses({
			@ApiResponse(responseCode = "204", description = "Requested allocation deleted"),
			@ApiResponse(responseCode = "400", description = "Customer ID must be positive", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "403", description = "Allocation was requested by another customer", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "404", description = "Allocation not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "409", description = "Allocation was not a customer request or has an issued invoice", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
	})
	@DeleteMapping("/{id}/customer-request")
	public ResponseEntity<Void> deleteCustomerRequest(
			@Parameter(description = "Allocation identifier", required = true, schema = @Schema(type = "integer", format = "int64", minimum = "1")) @PathVariable Long id,
			@Parameter(description = "Customer requesting cancellation; must be a positive ID", required = true, schema = @Schema(type = "integer", format = "int64", minimum = "1"))
			@RequestParam Long customerId) {
		if (customerId <= 0) {
			throw new InvalidActingCustomerException();
		}
		weeklyAllocationService.deleteCustomerRequest(id, customerId);
		return ResponseEntity.noContent().build();
	}

	/** Deletes an allocation through the management API. */
	@Operation(summary = "Delete a weekly allocation")
	@ApiResponses({
			@ApiResponse(responseCode = "204", description = "Allocation deleted"),
			@ApiResponse(responseCode = "404", description = "Allocation not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "409", description = "Allocation cannot be deleted because it has an issued invoice", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
	})
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteWeeklyAllocation(
			@Parameter(description = "Allocation identifier", required = true, schema = @Schema(type = "integer", format = "int64", minimum = "1")) @PathVariable Long id) {
		weeklyAllocationService.deleteWeeklyAllocation(id);

		// return 204 No Content because deletion has no response body
		return ResponseEntity.noContent().build();
	}
}
