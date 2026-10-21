package nz.ac.aut.comp713.allocation_service.exception;

// raised when a create request tries to set the payment status
public class InvalidPaymentStatusException extends RuntimeException {

    public InvalidPaymentStatusException() {
        super("Payment status can only be changed after an allocation is created");
    }
}
