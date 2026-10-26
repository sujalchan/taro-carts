package nz.ac.aut.comp713.dispatcher_service.exception;

public class AddressNotValidException extends RuntimeException {
    public AddressNotValidException() {
        super("Enter a complete New Zealand address that matches Google's address records.");
    }
}
