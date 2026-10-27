package nz.ac.aut.comp713.dispatcher_service.controller;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import nz.ac.aut.comp713.dispatcher_service.security.LoginClient;
import nz.ac.aut.comp713.dispatcher_service.security.SessionAuthenticationFilter;
import nz.ac.aut.comp713.dispatcher_service.exception.DispatcherNotFoundException;
import nz.ac.aut.comp713.dispatcher_service.service.DispatcherService;
import nz.ac.aut.comp713.dispatcher_service.service.PortalDeliveryClient;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Native dispatcher portal API; delivery actions are scoped to the signed-in dispatcher. */
@RestController
@Profile("!legacy-tests")
@RequestMapping("/api/v1/mobile/portal")
public class DispatcherMobilePortalController {
    public record DeliveryStatusRequest(@NotBlank String nextStatus) {}
    public record DispatcherProfile(Long id, String name, boolean active) {}

    private final PortalDeliveryClient deliveries;
    private final DispatcherService dispatchers;

    public DispatcherMobilePortalController(PortalDeliveryClient deliveries, DispatcherService dispatchers) {
        this.deliveries = deliveries;
        this.dispatchers = dispatchers;
    }

    @GetMapping("/profile")
    public DispatcherProfile profile(HttpServletRequest request) {
        var principal = dispatcher(request);
        try {
            var profile = dispatchers.getById(principal.profileId());
            return new DispatcherProfile(profile.id(), profile.name(), profile.active());
        } catch (DispatcherNotFoundException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping("/deliveries")
    public List<PortalDeliveryClient.Delivery> deliveries(HttpServletRequest request) {
        dispatcher(request);
        return deliveries.list(request, null);
    }

    @PutMapping("/deliveries/{id}/status")
    public ResponseEntity<Void> update(@PathVariable Long id,
            @Valid @RequestBody DeliveryStatusRequest body, HttpServletRequest request) {
        dispatcher(request);
        if (!("IN_TRANSIT".equals(body.nextStatus()) || "DELIVERED".equals(body.nextStatus()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        boolean assigned = deliveries.list(request, null).stream()
                .anyMatch(item -> id.equals(item.id()));
        if (!assigned) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        deliveries.advance(request, id, body.nextStatus());
        return ResponseEntity.noContent().build();
    }

    public record LocationRequest(Double latitude, Double longitude) {}

    @GetMapping("/deliveries/{id}/locations")
    public List<PortalDeliveryClient.LocationPoint> locations(@PathVariable Long id,
            HttpServletRequest request) {
        dispatcher(request);
        return deliveries.locations(request, id);
    }

    @PostMapping("/deliveries/{id}/locations")
    public PortalDeliveryClient.LocationPoint saveLocation(@PathVariable Long id,
            @Valid @RequestBody LocationRequest body, HttpServletRequest request) {
        dispatcher(request);
        if (body.latitude() == null || body.longitude() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        return deliveries.saveLocation(request, id, body.latitude(), body.longitude());
    }

    private LoginClient.SessionPrincipal dispatcher(HttpServletRequest request) {
        Object value = request.getAttribute(SessionAuthenticationFilter.PRINCIPAL_ATTRIBUTE);
        if (value instanceof LoginClient.SessionPrincipal principal && principal.active()
                && "DISPATCHER".equals(principal.role()) && principal.profileId() != null) return principal;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
}
