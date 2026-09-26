package nz.ac.aut.comp713.allocation_service.exception;

// raised when a create request tries to set the delivery status
public class InvalidDeliveryStatusException extends RuntimeException {

    public InvalidDeliveryStatusException() {
        super("Delivery status can only be changed after an allocation is created");
    }
}
