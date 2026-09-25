package nz.ac.aut.comp713.allocation_service.exception;

public class TaroServiceUnavailableException extends RuntimeException {
    public TaroServiceUnavailableException() {
        super("Taro service is currently unavailable");
    }
}
