package nz.ac.aut.comp713.allocation_service.dto;

import java.time.LocalDate;
import java.util.List;
import nz.ac.aut.comp713.allocation_service.model.DeliveryStatus;

// response DTO returned when weekly allocation data is sent to clients
public record WeeklyAllocationResponse(
		Long id,
		Long customerId,
		String customerName,
		LocalDate weekStart,
		DeliveryStatus deliveryStatus,
		List<AllocationItemResponse> allocationItems) {
}
