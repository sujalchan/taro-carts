package nz.ac.aut.comp713.customer_service.exception;

// thrown when a requested customer ID does not exist
public class CustomerNotFoundException extends RuntimeException {

    public CustomerNotFoundException(Long id) {
        super("Customer not found with id: " + id);
    }
}
