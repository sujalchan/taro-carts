package nz.ac.aut.comp713.allocation_service.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import nz.ac.aut.comp713.allocation_service.model.DeliveryStatus;
import nz.ac.aut.comp713.allocation_service.model.PaymentStatus;

/** Customer request: customerId is optional and never used as the acting identity. */
public record CustomerAllocationRequest(
        Long customerId,
        @NotNull(message = "Week start is required") LocalDate weekStart,
        @NotEmpty(message = "At least one allocation item is required")
        List<@Valid AllocationItemRequest> allocationItems,
        DeliveryStatus deliveryStatus,
        PaymentStatus paymentStatus,
        Long dispatcherId,
        @Size(max = 500, message = "Delivery address must be 500 characters or fewer")
        String deliveryAddress) {
    public WeeklyAllocationRequest forCustomer(Long effectiveCustomerId) {
        return new WeeklyAllocationRequest(effectiveCustomerId, weekStart, allocationItems,
                deliveryStatus, paymentStatus, dispatcherId, deliveryAddress);
    }
}
