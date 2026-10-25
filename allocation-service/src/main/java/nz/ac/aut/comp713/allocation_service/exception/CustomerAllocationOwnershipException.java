package nz.ac.aut.comp713.allocation_service.exception;

public class CustomerAllocationOwnershipException extends RuntimeException {
    public CustomerAllocationOwnershipException() {
        super("This allocation was not requested by the selected customer");
    }
}
