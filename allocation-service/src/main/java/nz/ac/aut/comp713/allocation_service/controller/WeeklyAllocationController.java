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

import nz.ac.aut.comp713.allocation_service.dto.ApiError;
import nz.ac.aut.comp713.allocation_service.exception.InvalidCustomerFilterException;
import nz.ac.aut.comp713.allocation_service.exception.InvalidActingCustomerException;

// REST controller for weekly allocation API requests
@RestController
@RequestMapping("/api/v1/allocations")
public class WeeklyAllocationController {

	private final WeeklyAllocationService weeklyAllocationService;

	// inject the service containing weekly allocation business logic
	public WeeklyAllocationController(WeeklyAllocationService weeklyAllocationService) {
		this.weeklyAllocationService = weeklyAllocationService;
	}

	@Operation(summary = "Retrieve weekly allocations", description = "Optionally filter allocations by customer ID.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Weekly allocations retrieved successfully"),
			@ApiResponse(responseCode = "400", description = "Customer ID filter must be a positive integer", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "503", description = "Customer or taro service is unavailable", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
	})
	// return all weekly allocations, including customer and taro type details
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

	@Operation(summary = "Retrieve a weekly allocation by ID")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Weekly allocation retrieved successfully"),
			@ApiResponse(responseCode = "404", description = "Weekly allocation not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "503", description = "Customer or taro service is unavailable", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
	})
	// retrieve one weekly allocation using the ID from the request path
	@GetMapping("/{id}")
	public ResponseEntity<WeeklyAllocationResponse> getWeeklyAllocationById(@PathVariable Long id) {
		return ResponseEntity.ok(weeklyAllocationService.getWeeklyAllocationById(id));
	}

	@Operation(summary = "Record a customer's allocation as paid")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Allocation marked as paid"),
			@ApiResponse(responseCode = "400", description = "Invalid selected customer ID"),
			@ApiResponse(responseCode = "403", description = "Allocation belongs to another customer"),
			@ApiResponse(responseCode = "404", description = "Allocation not found"),
			@ApiResponse(responseCode = "409", description = "Allocation cannot be paid in its current status or is already paid")
	})
	@PostMapping("/{id}/pay")
	public ResponseEntity<WeeklyAllocationResponse> recordCustomerPayment(@PathVariable Long id,
			@RequestParam Long customerId) {
		if (customerId <= 0) {
			throw new InvalidActingCustomerException();
		}
		return ResponseEntity.ok(weeklyAllocationService.recordCustomerPayment(id, customerId));
	}

	@Operation(summary = "Create a weekly allocation")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Weekly allocation created successfully"),
			@ApiResponse(responseCode = "400", description = "Invalid allocation data or duplicate taro type", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "404", description = "Customer or taro type not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "409", description = "Weekly allocation already exists for the customer and week", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "503", description = "Customer or taro service is unavailable", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
	})
	// validate the request body before creating a new weekly allocation
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

	@Operation(summary = "Request a weekly allocation as a customer")
	@PostMapping("/customer-request")
	public ResponseEntity<WeeklyAllocationResponse> createCustomerRequest(
			@Valid @RequestBody WeeklyAllocationRequest request) {
		WeeklyAllocationResponse response = weeklyAllocationService.createCustomerRequest(request);
		return ResponseEntity.created(URI.create("/api/v1/allocations/" + response.id())).body(response);
	}

	@Operation(summary = "Update an existing weekly allocation")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Weekly allocation updated successfully"),
			@ApiResponse(responseCode = "400", description = "Invalid allocation data or duplicate taro type", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "404", description = "Allocation, customer, or taro type not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "409", description = "Another weekly allocation already exists for the customer and week", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "503", description = "Customer or taro service is unavailable", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
	})
	// validate and update the weekly allocation identified by the path ID
	@PutMapping("/{id}")
	public ResponseEntity<WeeklyAllocationResponse> updateWeeklyAllocation(
			@PathVariable Long id,
			@Valid @RequestBody WeeklyAllocationRequest request) {

		return ResponseEntity.ok(
				weeklyAllocationService.updateWeeklyAllocation(
						id,
						request));
	}

	// delete a request only when it was created through the customer-facing API
	@Operation(summary = "Delete a customer's own requested allocation")
	@ApiResponses({
			@ApiResponse(responseCode = "204", description = "Requested allocation deleted"),
			@ApiResponse(responseCode = "403", description = "Allocation was not requested by this customer"),
			@ApiResponse(responseCode = "404", description = "Allocation not found"),
			@ApiResponse(responseCode = "409", description = "Allocation has progressed or has an issued invoice")
	})
	@DeleteMapping("/{id}/customer-request")
	public ResponseEntity<Void> deleteCustomerRequest(@PathVariable Long id,
			@RequestParam Long customerId) {
		if (customerId <= 0) {
			throw new InvalidActingCustomerException();
		}
		weeklyAllocationService.deleteCustomerRequest(id, customerId);
		return ResponseEntity.noContent().build();
	}

	// manager deletion remains available through the existing endpoint
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteWeeklyAllocation(@PathVariable Long id) {
		weeklyAllocationService.deleteWeeklyAllocation(id);

		// return 204 No Content because deletion has no response body
		return ResponseEntity.noContent().build();
	}
}
