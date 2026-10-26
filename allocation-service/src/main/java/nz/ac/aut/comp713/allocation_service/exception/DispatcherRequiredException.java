package nz.ac.aut.comp713.allocation_service.exception;

public class DispatcherRequiredException extends RuntimeException {
    public DispatcherRequiredException() {
        super("Select a valid dispatcher for this delivery status.");
    }
}
