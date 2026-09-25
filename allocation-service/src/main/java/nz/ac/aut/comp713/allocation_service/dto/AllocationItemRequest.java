package nz.ac.aut.comp713.allocation_service.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// request DTO representing one taro item within a weekly allocation
public record AllocationItemRequest(
        @NotNull(message = "Taro type ID is required") Long taroTypeId,

        @NotNull(message = "Quantity is required") @Positive(message = "Quantity must be greater than zero") BigDecimal quantity,

        @DecimalMin(value = "0.00", inclusive = true, message = "Price per kg cannot be negative") BigDecimal pricePerKg) {
}