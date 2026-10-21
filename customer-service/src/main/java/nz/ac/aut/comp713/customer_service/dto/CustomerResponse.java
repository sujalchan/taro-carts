package nz.ac.aut.comp713.customer_service.dto;

// DTO for customer response
public record CustomerResponse(
		Long id,
		String name,
		String contactName,
		String phone,
		Boolean active,
		String accountId) {
	public CustomerResponse(Long id, String name, String contactName, String phone, Boolean active) {
		this(id, name, contactName, phone, active, null);
	}
}
