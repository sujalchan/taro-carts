package nz.ac.aut.comp713.customer_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// DTO for customer request
public record CustomerRequest(

		// validation annotation to ensure the name is not blank
		@NotBlank(message = "Customer name is required") String name,

		@NotBlank(message = "Customer address is required")
		@Size(max = 500, message = "Customer address must be 500 characters or fewer") String address,

		String contactName,

		@Pattern(regexp = "^$|^\\+?[0-9]{7,15}$", message = "Phone number must contain 7 to 15 digits") String phone,

		Boolean active) {
}
