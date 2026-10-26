package nz.ac.aut.comp713.allocation_service.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import nz.ac.aut.comp713.allocation_service.model.DeliveryStatus;
import nz.ac.aut.comp713.allocation_service.model.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/** Request body for creating a weekly allocation or updating its workflow state. */
@Schema(description = "Allocation details. dispatcherId is required when updating to AWAITING_DISPATCH, IN_TRANSIT, or DELIVERED.")
public record WeeklyAllocationRequest(

		@Schema(description = "ID of the customer receiving the allocation", example = "12", minimum = "1")
		@NotNull(message = "Customer ID is required") Long customerId,

		@Schema(description = "Date within the allocation week; normalized to that week's Monday", example = "2026-10-26")
		@NotNull(message = "Week start is required") LocalDate weekStart,

		@Schema(description = "One or more requested taro types and quantities")
		@NotEmpty(message = "At least one allocation item is required") List<@Valid AllocationItemRequest> allocationItems,

		@Schema(description = "Delivery workflow status; supplied when updating an existing allocation")
		DeliveryStatus deliveryStatus,

		@Schema(description = "Payment status; supplied when updating an existing allocation")
		PaymentStatus paymentStatus,

		@Schema(description = "ID of an existing dispatcher. Required for AWAITING_DISPATCH, IN_TRANSIT, or DELIVERED delivery status.", example = "4", minimum = "1")
		Long dispatcherId,

		@Schema(description = "Address for this delivery; defaults to the customer's address when omitted", example = "12 Market Street, Auckland")
		@Size(max = 500, message = "Delivery address must be 500 characters or fewer") String deliveryAddress) {

	/** Creates a new-allocation request with workflow defaults left to the service. */
	/** Creates an allocation request with explicit workflow statuses and no dispatcher. */
	public WeeklyAllocationRequest(Long customerId, LocalDate weekStart,
			List<AllocationItemRequest> allocationItems) {
		this(customerId, weekStart, allocationItems, null, null, null, null);
	}

	public WeeklyAllocationRequest(Long customerId, LocalDate weekStart,
			List<AllocationItemRequest> allocationItems, DeliveryStatus deliveryStatus, PaymentStatus paymentStatus) {
		this(customerId, weekStart, allocationItems, deliveryStatus, paymentStatus, null, null);
	}

	public WeeklyAllocationRequest(Long customerId, LocalDate weekStart,
			List<AllocationItemRequest> allocationItems, DeliveryStatus deliveryStatus,
			PaymentStatus paymentStatus, Long dispatcherId) {
		this(customerId, weekStart, allocationItems, deliveryStatus, paymentStatus, dispatcherId, null);
	}
}
