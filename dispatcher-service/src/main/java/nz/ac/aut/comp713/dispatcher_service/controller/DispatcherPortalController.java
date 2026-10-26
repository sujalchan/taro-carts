package nz.ac.aut.comp713.dispatcher_service.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import nz.ac.aut.comp713.dispatcher_service.security.LoginClient;
import nz.ac.aut.comp713.dispatcher_service.security.SessionAuthenticationFilter;
import nz.ac.aut.comp713.dispatcher_service.exception.DispatcherNotFoundException;
import nz.ac.aut.comp713.dispatcher_service.service.DispatcherService;
import nz.ac.aut.comp713.dispatcher_service.service.PortalDeliveryClient;

/** Assigned-delivery view and the two permitted dispatcher transitions. */
@Controller
@Profile("!legacy-tests")
public class DispatcherPortalController {
    private final DispatcherService dispatchers;
    private final PortalDeliveryClient deliveries;

    public DispatcherPortalController(DispatcherService dispatchers, PortalDeliveryClient deliveries) {
        this.dispatchers = dispatchers;
        this.deliveries = deliveries;
    }

    @GetMapping("/portal/deliveries")
    public String list(@RequestParam(required = false) Long dispatcherId,
                       HttpServletRequest request, Model model) {
        Long selected = scope(request, dispatcherId, model);
        if (selected != null) model.addAttribute("deliveries", deliveries.list(request,
                "ADMIN".equals(principal(request).role()) ? selected : null));
        return "dispatcher-portal";
    }

    @PostMapping("/portal/deliveries/{id}/status")
    public String update(@PathVariable Long id, @RequestParam(required = false) Long dispatcherId,
                         @RequestParam String nextStatus, HttpServletRequest request, Model model) {
        Long selected = scope(request, dispatcherId, model);
        if (selected == null) return "dispatcher-portal";
        if (!("IN_TRANSIT".equals(nextStatus) || "DELIVERED".equals(nextStatus))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        boolean assigned = deliveries.list(request,
                "ADMIN".equals(principal(request).role()) ? selected : null).stream()
                .anyMatch(delivery -> id.equals(delivery.id()) && selected.equals(delivery.dispatcherId()));
        if (!assigned) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        deliveries.advance(request, id, nextStatus);
        return "redirect:/portal/deliveries" + ("ADMIN".equals(principal(request).role())
                ? "?dispatcherId=" + selected : "");
    }

    private Long scope(HttpServletRequest request, Long selected, Model model) {
        LoginClient.SessionPrincipal principal = principal(request);
        boolean admin = "ADMIN".equals(principal.role());
        model.addAttribute("admin", admin);
        model.addAttribute("username", principal.username());
        if (admin) {
            model.addAttribute("dispatchers", dispatchers.getAll(null));
            if (selected == null) return null;
            if (selected <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
            model.addAttribute("selectedDispatcher", findDispatcher(selected));
            model.addAttribute("dispatcherId", selected);
            return selected;
        }
        if (!"DISPATCHER".equals(principal.role()) || principal.profileId() == null
                || selected != null && !selected.equals(principal.profileId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        model.addAttribute("selectedDispatcher", findDispatcher(principal.profileId()));
        return principal.profileId();
    }

    private Object findDispatcher(Long id) {
        try {
            return dispatchers.getById(id);
        } catch (DispatcherNotFoundException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    private LoginClient.SessionPrincipal principal(HttpServletRequest request) {
        Object value = request.getAttribute(SessionAuthenticationFilter.PRINCIPAL_ATTRIBUTE);
        if (value instanceof LoginClient.SessionPrincipal principal && principal.active()) return principal;
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
}
