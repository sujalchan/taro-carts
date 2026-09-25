package nz.ac.aut.comp713.customer_service.exception;

// thrown when a customer is created or renamed to a name that already exists
public class CustomerAlreadyExistsException extends RuntimeException {

    public CustomerAlreadyExistsException(String name) {
        super("Customer '" + name + "' already exists.");
    }
}
