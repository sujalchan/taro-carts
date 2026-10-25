package nz.ac.aut.comp713.allocation_service.exception;

public class CustomerPaymentOwnershipException extends RuntimeException {
    public CustomerPaymentOwnershipException() {
        super("This allocation does not belong to the selected customer");
    }
}
