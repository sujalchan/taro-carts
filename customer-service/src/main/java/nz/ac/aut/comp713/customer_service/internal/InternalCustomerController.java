package nz.ac.aut.comp713.customer_service.internal;

import java.net.URI;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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
import org.springframework.http.HttpStatus;

import nz.ac.aut.comp713.customer_service.dto.CustomerResponse;

@RestController
@RequestMapping("/internal/v1/customers")
public class InternalCustomerController {

    public record ActivationRequest(@NotNull Boolean active) {}

    private final InternalProvisioningAuth auth;
    private final InternalCustomerProvisioning provisioning;

    InternalCustomerController(InternalProvisioningAuth auth, InternalCustomerProvisioning provisioning) {
        this.auth = auth;
        this.provisioning = provisioning;
    }

    @PostMapping
    public ResponseEntity<CustomerResponse> create(
            @RequestHeader(value = "X-Login-Provisioning-Key", required = false) String secret,
            @RequestHeader("X-Registration-Request-Id") Long registrationRequestId,
            HttpServletRequest servletRequest,
            @Valid @RequestBody ProvisionCustomerRequest request) {
        auth.verify(servletRequest, secret);
        if (registrationRequestId <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        CustomerResponse created = provisioning.create(registrationRequestId, request);
        return ResponseEntity.created(URI.create("/api/v1/customers/" + created.id())).body(created);
    }

    @PutMapping("/{id}/active")
    public CustomerResponse setActive(@PathVariable Long id,
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
