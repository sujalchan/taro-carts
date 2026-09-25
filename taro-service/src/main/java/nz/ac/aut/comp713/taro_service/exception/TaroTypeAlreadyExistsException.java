package nz.ac.aut.comp713.taro_service.exception;

// thrown when a taro type is created or renamed to a name that already exists
public class TaroTypeAlreadyExistsException extends RuntimeException {

    public TaroTypeAlreadyExistsException(String name) {
        super("Taro type '" + name + "' already exists.");
    }
}