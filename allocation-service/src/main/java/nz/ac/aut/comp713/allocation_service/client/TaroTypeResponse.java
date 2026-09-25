package nz.ac.aut.comp713.allocation_service.client;

import java.math.BigDecimal;

// response DTO used to deserialize taro type data returned by taro-service
public record TaroTypeResponse(
		Long id,
		String name,
		String description,
		BigDecimal standardPrice) {
}