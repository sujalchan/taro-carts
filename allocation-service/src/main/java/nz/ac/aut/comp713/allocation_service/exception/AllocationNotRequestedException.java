package nz.ac.aut.comp713.allocation_service.exception;

public class AllocationNotRequestedException extends RuntimeException {
    public AllocationNotRequestedException() {
        super("Only allocations still in REQUESTED_ALLOCATION can be deleted by a customer");
    }
}
