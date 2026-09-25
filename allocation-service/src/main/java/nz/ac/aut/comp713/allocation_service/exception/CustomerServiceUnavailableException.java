package nz.ac.aut.comp713.allocation_service.exception;

// thrown when allocation-service cannot connect to customer-service
public class CustomerServiceUnavailableException extends RuntimeException {

    public CustomerServiceUnavailableException() {
        super("Customer service is currently unavailable");
    }
}