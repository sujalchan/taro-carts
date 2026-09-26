package nz.ac.aut.comp713.allocation_service.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import nz.ac.aut.comp713.allocation_service.model.DeliveryStatus;

// request DTO used when creating or updating a weekly allocation
public record WeeklyAllocationRequest(

		@NotNull(message = "Customer ID is required") Long customerId,

		@NotNull(message = "Week start is required") LocalDate weekStart,

		@NotEmpty(message = "At least one allocation item is required") List<@Valid AllocationItemRequest> allocationItems,

		// supplied only when updating an existing allocation
		DeliveryStatus deliveryStatus) {

	// creation requests leave the status unset so the service applies its default
	public WeeklyAllocationRequest(Long customerId, LocalDate weekStart,
			List<AllocationItemRequest> allocationItems) {
		this(customerId, weekStart, allocationItems, null);
	}
}
