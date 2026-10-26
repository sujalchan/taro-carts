package nz.ac.aut.comp713.allocation_service.dto;

import java.math.BigDecimal;
import io.swagger.v3.oas.annotations.media.Schema;

/** One taro item returned as part of a weekly allocation. */
@Schema(description = "Taro item and its saved allocation price.")
public record AllocationItemResponse(
		@Schema(description = "Allocation item ID")
		Long id,
		@Schema(description = "Taro type ID")
		Long taroTypeId,
		@Schema(description = "Taro type display name")
		String taroTypeName,
		@Schema(description = "Quantity in kilograms")
		Integer quantity,
		@Schema(description = "Saved price per kilogram")
		BigDecimal pricePerKg) {
}
