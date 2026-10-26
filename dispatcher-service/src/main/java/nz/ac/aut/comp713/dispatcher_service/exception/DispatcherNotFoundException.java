package nz.ac.aut.comp713.dispatcher_service.exception;

public class DispatcherNotFoundException extends RuntimeException {
    public DispatcherNotFoundException(Long id) {
        super("Dispatcher with ID " + id + " was not found.");
    }
}
