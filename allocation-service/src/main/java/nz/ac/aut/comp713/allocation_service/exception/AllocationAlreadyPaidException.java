package nz.ac.aut.comp713.allocation_service.exception;

public class AllocationAlreadyPaidException extends RuntimeException {
    public AllocationAlreadyPaidException() {
        super("This allocation is already paid");
    }
}
