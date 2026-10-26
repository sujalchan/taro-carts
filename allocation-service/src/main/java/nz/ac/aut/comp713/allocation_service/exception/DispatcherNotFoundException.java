package nz.ac.aut.comp713.allocation_service.exception;

public class DispatcherNotFoundException extends RuntimeException {
    public DispatcherNotFoundException(Long dispatcherId) {
        super("Dispatcher not found with ID: " + dispatcherId);
    }
}
