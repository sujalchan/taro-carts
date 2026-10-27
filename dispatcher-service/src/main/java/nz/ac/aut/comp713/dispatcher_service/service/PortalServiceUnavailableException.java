package nz.ac.aut.comp713.dispatcher_service.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** A portal dependency failed; the reason is one of the allowlisted service names. */
public class PortalServiceUnavailableException extends ResponseStatusException {
    public PortalServiceUnavailableException(String reason) {
        super(HttpStatus.SERVICE_UNAVAILABLE, reason);
    }
}
