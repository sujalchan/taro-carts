package nz.ac.aut.comp713.allocation_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidInvoiceAmendmentException extends RuntimeException {
    public InvalidInvoiceAmendmentException(String message) {
        super(message);
    }
}
