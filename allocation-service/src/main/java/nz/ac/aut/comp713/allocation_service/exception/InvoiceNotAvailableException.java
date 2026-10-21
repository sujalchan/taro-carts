package nz.ac.aut.comp713.allocation_service.exception;

public class InvoiceNotAvailableException extends RuntimeException {
    public InvoiceNotAvailableException(Long allocationId) {
        super("Invoice is not available for allocation " + allocationId);
    }
}
