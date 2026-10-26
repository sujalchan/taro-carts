package nz.ac.aut.comp713.allocation_service.exception;

public class InvalidDispatcherTransitionException extends RuntimeException {
    public InvalidDispatcherTransitionException() {
        super("Delivery status can only advance from awaiting dispatch to in transit to delivered");
    }

    public InvalidDispatcherTransitionException(String message) {
        super(message);
    }
}
