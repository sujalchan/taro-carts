package nz.ac.aut.comp713.allocation_service.exception;

public class IssuedInvoiceConflictException extends RuntimeException {
    public IssuedInvoiceConflictException() {
        super("Customer, week, items, and prices cannot change after an invoice is issued");
    }
}
