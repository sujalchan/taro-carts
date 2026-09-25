package nz.ac.aut.comp713.allocation_service.exception;

// thrown when the requested weekly allocation ID does not exist
public class WeeklyAllocationNotFoundException extends RuntimeException {

    public WeeklyAllocationNotFoundException(Long allocationId) {
        super("Weekly allocation not found with id: " + allocationId);
    }
}