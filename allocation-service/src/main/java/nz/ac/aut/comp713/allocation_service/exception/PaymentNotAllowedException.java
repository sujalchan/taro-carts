package nz.ac.aut.comp713.allocation_service.exception;

public class PaymentNotAllowedException extends RuntimeException {
    public PaymentNotAllowedException() {
        super("Payment can be recorded only after the allocation is approved and before it is cancelled");
    }
}
