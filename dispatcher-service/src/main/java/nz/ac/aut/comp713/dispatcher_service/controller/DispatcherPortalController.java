package nz.ac.aut.comp713.dispatcher_service.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Validator;
import jakarta.validation.Validation;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
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
import nz.ac.aut.comp713.dispatcher_service.dto.DispatcherRequest;
import nz.ac.aut.comp713.dispatcher_service.exception.DispatcherAlreadyExistsException;
import nz.ac.aut.comp713.dispatcher_service.exception.DispatcherNotFoundException;
import nz.ac.aut.comp713.dispatcher_service.service.DispatcherService;
import nz.ac.aut.comp713.dispatcher_service.service.AccountPasswordClient;
import nz.ac.aut.comp713.dispatcher_service.service.PortalDeliveryClient;

/** Assigned-delivery view and the two permitted dispatcher transitions. */
@Controller
@Profile("!legacy-tests")
public class DispatcherPortalController {
    private final DispatcherService dispatchers;
    private final PortalDeliveryClient deliveries;
    private final Validator validator;
    private final AccountPasswordClient passwords;

    @Autowired
    public DispatcherPortalController(DispatcherService dispatchers, PortalDeliveryClient deliveries,
                                      Validator validator, AccountPasswordClient passwords) {
        this.dispatchers = dispatchers;
        this.deliveries = deliveries;
        this.validator = validator;
        this.passwords = passwords;
    }

    public DispatcherPortalController(DispatcherService dispatchers, PortalDeliveryClient deliveries) {
        this.dispatchers = dispatchers;
        this.deliveries = deliveries;
        this.validator = Validation.buildDefaultValidatorFactory().getValidator();
        this.passwords = null;
    }

    @GetMapping("/portal/profile")
    public String profile(HttpServletRequest request, Model model) {
        var principal = dispatcherPrincipal(request);
        var profile = dispatchers.getById(principal.profileId());
        addProfileValues(model, profile.name(), profile.contactName(), profile.phone(), profile.address());
        model.addAttribute("username", principal.username());
        return "dispatcher-profile";
    }

    @PostMapping("/portal/profile")
    public String updateProfile(@RequestParam(required = false) String name,
                                @RequestParam(required = false) String contactName,
                                @RequestParam(required = false) String phone,
                                @RequestParam(required = false) String address,
                                HttpServletRequest request, Model model) {
        var principal = dispatcherPrincipal(request);
        var update = new DispatcherRequest(name, contactName, phone, address);
        var errors = validator.validate(update).stream().map(violation -> violation.getMessage()).toList();
        if (!errors.isEmpty()) {
            addProfileValues(model, name, contactName, phone, address);
            model.addAttribute("username", principal.username());
            model.addAttribute("errors", errors);
            return "dispatcher-profile";
        }
        try {
            dispatchers.update(principal.profileId(), update);
        } catch (DispatcherAlreadyExistsException exception) {
            addProfileValues(model, name, contactName, phone, address);
            model.addAttribute("username", principal.username());
            model.addAttribute("errors", List.of(exception.getMessage()));
            return "dispatcher-profile";
        }
        return "redirect:/portal/deliveries";
    }

    @PostMapping("/portal/password")
    public String updatePassword(@RequestParam String currentPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 @org.springframework.web.bind.annotation.CookieValue(value = "SESSION", required = false)
                                         String sessionToken,
                                 HttpServletRequest request, Model model) {
        var principal = dispatcherPrincipal(request);
        try {
            passwords.change(sessionToken, currentPassword, newPassword, confirmPassword);
            return "redirect:" + passwords.loginUrl() + "/login?passwordChanged=1";
        } catch (ResponseStatusException exception) {
            var profile = dispatchers.getById(principal.profileId());
            addProfileValues(model, profile.name(), profile.contactName(), profile.phone(), profile.address());
            model.addAttribute("username", principal.username());
            model.addAttribute("passwordError", exception.getReason());
            return "dispatcher-profile";
        }
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

    private LoginClient.SessionPrincipal dispatcherPrincipal(HttpServletRequest request) {
        var principal = principal(request);
        if (!"DISPATCHER".equals(principal.role()) || principal.profileId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return principal;
    }

    private void addProfileValues(Model model, String name, String contactName, String phone, String address) {
        model.addAttribute("name", name);
        model.addAttribute("contactName", contactName);
        model.addAttribute("phone", phone);
        model.addAttribute("address", address);
    }
}
