package nz.ac.aut.comp713.allocation_service.exception;

public class InvalidActingCustomerException extends RuntimeException {
    public InvalidActingCustomerException() {
        super("Selected customer ID must be a positive integer");
    }
}
