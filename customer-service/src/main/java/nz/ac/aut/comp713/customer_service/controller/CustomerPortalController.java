package nz.ac.aut.comp713.customer_service.controller;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import nz.ac.aut.comp713.customer_service.security.LoginClient;
import nz.ac.aut.comp713.customer_service.security.SessionAuthenticationFilter;
import nz.ac.aut.comp713.customer_service.exception.CustomerNotFoundException;
import nz.ac.aut.comp713.customer_service.service.CustomerService;
import nz.ac.aut.comp713.customer_service.service.PortalAllocationClient;

/** Role-specific customer pages. Browser IDs select an admin view but never identify the acting customer. */
@Controller
@Profile("!legacy-tests")
public class CustomerPortalController {
    private final CustomerService customers;
    private final PortalAllocationClient allocations;

    public CustomerPortalController(CustomerService customers, PortalAllocationClient allocations) {
        this.customers = customers;
        this.allocations = allocations;
    }

    @GetMapping("/portal/allocations")
    public String list(@RequestParam(required = false) Long customerId,
                       HttpServletRequest request, Model model) {
        Long selected = scope(request, customerId, model);
        if (selected != null) model.addAttribute("allocations", allocations.list(request, selected));
        return "customer-portal";
    }

    @GetMapping("/portal/allocations/request")
    public String requestForm(@RequestParam(required = false) Long customerId,
                              HttpServletRequest request, Model model) {
        Long selected = scope(request, customerId, model);
        if (selected == null) return "customer-portal";
        model.addAttribute("taroTypes", allocations.taroTypes(request));
        model.addAttribute("weekStart", LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)));
        return "customer-request";
    }

    @PostMapping("/portal/allocations/request")
    public String submit(@RequestParam(required = false) Long customerId,
                         @RequestParam(required = false) LocalDate weekStart,
                         @RequestParam(required = false) Long taroTypeId,
                         @RequestParam(required = false) BigDecimal quantity,
                         @RequestParam(required = false) String deliveryAddress,
                         HttpServletRequest request, Model model) {
        Long selected = scope(request, customerId, model);
        if (selected == null) return "customer-portal";
        if (weekStart == null || taroTypeId == null || taroTypeId <= 0 || quantity == null
                || quantity.signum() <= 0 || quantity.stripTrailingZeros().scale() > 0
                || deliveryAddress != null && deliveryAddress.length() > 500) {
            return requestError(request, model, weekStart, "Check the date, taro type, whole-kilogram quantity and address.");
        }
        try {
            allocations.create(request, selected, weekStart, taroTypeId, quantity,
                    deliveryAddress == null || deliveryAddress.isBlank() ? null : deliveryAddress.trim());
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value() == 400 || exception.getStatusCode().value() == 409) {
                return requestError(request, model, weekStart,
                        "That request could not be saved. Check the details or choose a different week.");
            }
            throw exception;
        }
        return redirect(selected, principal(request));
    }

    @GetMapping("/portal/allocations/{id}")
    public String detail(@PathVariable Long id, @RequestParam(required = false) Long customerId,
                         HttpServletRequest request, Model model) {
        Long selected = scope(request, customerId, model);
        if (selected == null) return "customer-portal";
        var allocation = visibleAllocation(request, id);
        if (!selected.equals(allocation.customerId())) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        model.addAttribute("allocation", allocation);
        return "customer-allocation";
    }

    @GetMapping("/portal/allocations/{id}/invoice.pdf")
    public ResponseEntity<byte[]> invoice(@PathVariable Long id,
            @RequestParam(required = false) Long customerId,
            @RequestParam(defaultValue = "false") boolean inline,
            HttpServletRequest request, Model model) {
        Long selected = scopedAllocation(request, customerId, id, model);
        if (selected == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        byte[] pdf;
        try {
            pdf = allocations.invoice(request, id, inline);
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value() == 403 || exception.getStatusCode().value() == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            }
            throw exception;
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                                .filename("allocation-" + id + ".pdf").build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(pdf);
    }

    @PostMapping("/portal/allocations/{id}/pay")
    public String pay(@PathVariable Long id, @RequestParam(required = false) Long customerId,
                      HttpServletRequest request, Model model) {
        Long selected = scopedAllocation(request, customerId, id, model);
        if (selected == null) return "customer-portal";
        allocations.pay(request, id);
        return redirect(selected, principal(request));
    }

    @PostMapping("/portal/allocations/{id}/cancel")
    public String cancel(@PathVariable Long id, @RequestParam(required = false) Long customerId,
                         HttpServletRequest request, Model model) {
        Long selected = scopedAllocation(request, customerId, id, model);
        if (selected == null) return "customer-portal";
        allocations.cancel(request, id);
        return redirect(selected, principal(request));
    }

    private Long scopedAllocation(HttpServletRequest request, Long customerId, Long id, Model model) {
        Long selected = scope(request, customerId, model);
        if (selected != null && !selected.equals(visibleAllocation(request, id).customerId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return selected;
    }

    private PortalAllocationClient.Allocation visibleAllocation(HttpServletRequest request, Long id) {
        try {
            return allocations.detail(request, id);
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value() == 403 || exception.getStatusCode().value() == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            }
            throw exception;
        }
    }

    private Long scope(HttpServletRequest request, Long customerId, Model model) {
        LoginClient.SessionPrincipal principal = principal(request);
        boolean admin = "ADMIN".equals(principal.role());
        model.addAttribute("admin", admin);
        model.addAttribute("username", principal.username());
        if (admin) {
            model.addAttribute("customers", customers.getAllCustomers(null));
            if (customerId == null) return null;
            if (customerId <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
            model.addAttribute("selectedCustomer", findCustomer(customerId));
            model.addAttribute("customerId", customerId);
            return customerId;
        }
        if (!"CUSTOMER".equals(principal.role()) || principal.profileId() == null
                || customerId != null && !customerId.equals(principal.profileId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        model.addAttribute("selectedCustomer", findCustomer(principal.profileId()));
        return principal.profileId();
    }

    private Object findCustomer(Long id) {
        try {
            return customers.getCustomerById(id);
        } catch (CustomerNotFoundException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    private LoginClient.SessionPrincipal principal(HttpServletRequest request) {
        Object value = request.getAttribute(SessionAuthenticationFilter.PRINCIPAL_ATTRIBUTE);
        if (value instanceof LoginClient.SessionPrincipal principal && principal.active()) return principal;
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }

    private String requestError(HttpServletRequest request, Model model, LocalDate weekStart, String message) {
        model.addAttribute("error", message);
        model.addAttribute("weekStart", weekStart);
        model.addAttribute("taroTypes", allocations.taroTypes(request));
        return "customer-request";
    }

    private String redirect(Long customerId, LoginClient.SessionPrincipal principal) {
        return "redirect:/portal/allocations" + ("ADMIN".equals(principal.role())
                ? "?customerId=" + customerId : "");
    }
}
