package nz.ac.aut.comp713.allocation_service.exception;

// thrown when taro-service reports that the requested taro type ID does not exist
public class TaroTypeNotFoundException extends RuntimeException {

    public TaroTypeNotFoundException(Long taroTypeId) {
        super("Taro type not found with id: " + taroTypeId);
    }
}