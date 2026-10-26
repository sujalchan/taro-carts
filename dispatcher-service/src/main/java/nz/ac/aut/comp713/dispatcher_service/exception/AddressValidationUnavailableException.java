package nz.ac.aut.comp713.dispatcher_service.exception;

public class AddressValidationUnavailableException extends RuntimeException {
    public AddressValidationUnavailableException() {
        super("Address validation is temporarily unavailable. Please try again later.");
    }
}
