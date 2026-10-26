package nz.ac.aut.comp713.allocation_service.exception;

public class DispatcherServiceUnavailableException extends RuntimeException {
    public DispatcherServiceUnavailableException() {
        super("Dispatcher service is unavailable.");
    }
}
