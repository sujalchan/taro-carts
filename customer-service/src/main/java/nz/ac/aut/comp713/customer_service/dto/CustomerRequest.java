package nz.ac.aut.comp713.customer_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

// DTO for customer request
public record CustomerRequest(

		// validation annotation to ensure the name is not blank
		@NotBlank(message = "Customer name is required") String name,

		String contactName,

		@Pattern(regexp = "^$|^\\+?[0-9]{7,15}$", message = "Phone number must contain 7 to 15 digits") String phone,

		Boolean active,
		@Pattern(regexp = "^$|^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
				message = "Account ID must be a UUID") String accountId) {
	public CustomerRequest(String name, String contactName, String phone, Boolean active) {
		this(name, contactName, phone, active, null);
	}
}
