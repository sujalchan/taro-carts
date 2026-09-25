package nz.ac.aut.comp713.allocation_service.exception;

// thrown when customer-service reports that the requested customer ID does not exist
public class CustomerNotFoundException extends RuntimeException {

    public CustomerNotFoundException(Long customerId) {
        super("Customer not found with id: " + customerId);
    }
}