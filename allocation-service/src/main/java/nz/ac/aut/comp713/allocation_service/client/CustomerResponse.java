package nz.ac.aut.comp713.allocation_service.client;

// response DTO used to deserialize customer data returned by customer-service
public record CustomerResponse(
		Long id,
		String name,
		String contactName,
		String phone,
		Boolean active) {
}
