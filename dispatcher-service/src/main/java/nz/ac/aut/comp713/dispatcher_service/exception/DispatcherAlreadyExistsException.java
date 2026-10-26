package nz.ac.aut.comp713.dispatcher_service.exception;

public class DispatcherAlreadyExistsException extends RuntimeException {
    public DispatcherAlreadyExistsException(String name) {
        super("Dispatcher '" + name + "' already exists.");
    }
}
