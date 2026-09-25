package nz.ac.aut.comp713.allocation_service.exception;

// thrown when an allocation quantity is not a positive whole number
public class InvalidQuantityException extends RuntimeException {

    public InvalidQuantityException() {
        super("Quantity must be a positive whole number");
    }
}