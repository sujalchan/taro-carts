package nz.ac.aut.comp713.allocation_service.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import io.swagger.v3.oas.annotations.media.Schema;

/** Request details for one taro type and quantity in an allocation. */
@Schema(description = "One taro type included in a weekly allocation.")
public record AllocationItemRequest(
        @Schema(description = "Existing taro type ID", example = "3", minimum = "1")
        @NotNull(message = "Taro type ID is required") Long taroTypeId,

        @Schema(description = "Requested quantity in kilograms; must be greater than zero", example = "5.0", minimum = "0.01")
        @NotNull(message = "Quantity is required") @Positive(message = "Quantity must be greater than zero") BigDecimal quantity,

        @Schema(description = "Price per kilogram to save; the taro type standard price is used when omitted.", example = "8.50", minimum = "0")
        @DecimalMin(value = "0.00", inclusive = true, message = "Price per kg cannot be negative") BigDecimal pricePerKg) {
}
