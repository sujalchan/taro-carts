package nz.ac.aut.comp713.allocation_service.exception;

public class InvalidCustomerFilterException extends RuntimeException {

    public InvalidCustomerFilterException() {
        super("Customer filter must be a positive integer customer ID");
    }
}
