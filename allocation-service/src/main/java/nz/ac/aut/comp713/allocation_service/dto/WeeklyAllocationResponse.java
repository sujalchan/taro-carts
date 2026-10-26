package nz.ac.aut.comp713.allocation_service.dto;

import java.time.LocalDate;
import java.util.List;
import nz.ac.aut.comp713.allocation_service.model.DeliveryStatus;
import nz.ac.aut.comp713.allocation_service.model.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/** Allocation data returned by the REST API. */
@Schema(description = "Weekly allocation details, including the assigned dispatcher ID when present.")
public record WeeklyAllocationResponse(
		@Schema(description = "Allocation ID")
		Long id,
		@Schema(description = "Customer ID")
		Long customerId,
		@Schema(description = "Customer ID that submitted the request, or null for a staff-created allocation")
		Long requestedByCustomerId,
		@Schema(description = "Customer display name")
		String customerName,
		@Schema(description = "Monday date of the allocation week")
		LocalDate weekStart,
		@Schema(description = "Current delivery status")
		DeliveryStatus deliveryStatus,
		@Schema(description = "Current payment status")
		PaymentStatus paymentStatus,
		@Schema(description = "Whether an invoice PDF is available")
		boolean invoiceAvailable,
		@Schema(description = "Taro items in this allocation")
		List<AllocationItemResponse> allocationItems,
		@Schema(description = "Assigned dispatcher ID, or null when no dispatcher is assigned")
		Long dispatcherId) {
}
