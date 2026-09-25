package nz.ac.aut.comp713.allocation_service.dto;

import java.math.BigDecimal;

// response DTO representing one taro item returned as part of a weekly allocation
public record AllocationItemResponse(
		Long id,
		Long taroTypeId,
		String taroTypeName,
		Integer quantity,
		BigDecimal pricePerKg) {
}
