package nz.ac.aut.comp713.dispatcher_service.internal;

import java.net.URI;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import nz.ac.aut.comp713.dispatcher_service.dto.DispatcherResponse;

@RestController
@RequestMapping("/internal/v1/dispatchers")
public class InternalDispatcherController {

    public record ActivationRequest(@NotNull Boolean active) {}

    private final InternalProvisioningAuth auth;
    private final InternalDispatcherProvisioning provisioning;

    InternalDispatcherController(InternalProvisioningAuth auth, InternalDispatcherProvisioning provisioning) {
        this.auth = auth;
        this.provisioning = provisioning;
    }

    @PostMapping
    public ResponseEntity<DispatcherResponse> create(
            @RequestHeader(value = "X-Login-Provisioning-Key", required = false) String secret,
            @RequestHeader("X-Registration-Request-Id") Long registrationRequestId,
            HttpServletRequest servletRequest,
            @Valid @RequestBody ProvisionDispatcherRequest request) {
        auth.verify(servletRequest, secret);
        if (registrationRequestId <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        DispatcherResponse created = provisioning.create(registrationRequestId, request);
        return ResponseEntity.created(URI.create("/api/v1/dispatchers/" + created.id())).body(created);
    }

    @PutMapping("/{id}/active")
    public DispatcherResponse setActive(@PathVariable Long id,
                                        @RequestHeader(value = "X-Login-Provisioning-Key", required = false) String secret,
                                        HttpServletRequest servletRequest,
                                        @Valid @RequestBody ActivationRequest request) {
        auth.verify(servletRequest, secret);
        return provisioning.setActive(id, request.active());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> compensate(@PathVariable Long id,
                                           @RequestHeader(value = "X-Login-Provisioning-Key", required = false) String secret,
                                           HttpServletRequest servletRequest) {
        auth.verify(servletRequest, secret);
        if (!provisioning.deleteInactive(id)) throw new ResponseStatusException(HttpStatus.CONFLICT);
        return ResponseEntity.noContent().build();
    }
}
