package nz.ac.aut.comp713.customer_service.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// request DTO used when creating or updating a taro type
public record TaroTypeRequest(

		@NotBlank(message = "Taro type name is required") String name,

		String description,

		@NotNull(message = "Standard price is required") @DecimalMin(value = "0.0", inclusive = true, message = "Standard price cannot be negative") BigDecimal standardPrice) {
}