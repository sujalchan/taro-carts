package nz.ac.aut.comp713.customer_service.exception;

// thrown when a requested taro type ID does not exist
public class TaroTypeNotFoundException extends RuntimeException {

    public TaroTypeNotFoundException(Long id) {
        super("Taro type not found with id: " + id);
    }
}
