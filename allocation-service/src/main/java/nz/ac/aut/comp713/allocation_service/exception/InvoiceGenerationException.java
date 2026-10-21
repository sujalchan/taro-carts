package nz.ac.aut.comp713.allocation_service.exception;

public class InvoiceGenerationException extends RuntimeException {
    public InvoiceGenerationException(Throwable cause) {
        super("Unable to generate invoice PDF", cause);
    }
}
