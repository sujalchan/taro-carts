package nz.ac.aut.comp713.customer_service.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import nz.ac.aut.comp713.customer_service.dto.CustomerResponse;
import nz.ac.aut.comp713.customer_service.dto.CustomerRequest;
import nz.ac.aut.comp713.customer_service.security.LoginClient;
import nz.ac.aut.comp713.customer_service.security.SessionAuthenticationFilter;
import nz.ac.aut.comp713.customer_service.service.CustomerService;
import nz.ac.aut.comp713.customer_service.service.PortalAllocationClient;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Native customer portal API; the account profile, never a request ID, determines scope. */
@RestController
@Profile("!legacy-tests")
@RequestMapping("/api/v1/mobile/portal")
public class CustomerMobilePortalController {
    public record ItemRequest(@NotNull Long taroTypeId, @NotNull @DecimalMin("1") BigDecimal quantity) {}
    public record AllocationRequest(@NotNull LocalDate weekStart,
            @NotEmpty List<@Valid ItemRequest> allocationItems,
            @Size(max = 500) String deliveryAddress) {}

    private final CustomerService customers;
    private final PortalAllocationClient allocations;

    public CustomerMobilePortalController(CustomerService customers, PortalAllocationClient allocations) {
        this.customers = customers;
        this.allocations = allocations;
    }

    @GetMapping("/profile")
    public CustomerResponse profile(HttpServletRequest request) {
        var principal = customer(request);
        return customers.getCustomerById(principal.profileId());
    }

    @PutMapping("/profile")
    public CustomerResponse updateProfile(HttpServletRequest request,
                                           @Valid @RequestBody CustomerRequest body) {
        Long ownCustomerId = customer(request).profileId();
        return customers.updateCustomer(ownCustomerId, body);
    }

    @GetMapping("/allocations")
    public List<PortalAllocationClient.Allocation> allocations(HttpServletRequest request) {
        return allocations.list(request, customer(request).profileId());
    }

    @GetMapping("/taro-types")
    public List<PortalAllocationClient.TaroType> taroTypes(HttpServletRequest request) {
        customer(request);
        return allocations.taroTypes(request);
    }

    @PostMapping("/allocations")
    public ResponseEntity<Void> requestAllocation(HttpServletRequest request,
                                                   @Valid @RequestBody AllocationRequest body) {
        Long customerId = customer(request).profileId();
        if (body.allocationItems().stream().anyMatch(item -> item.quantity().stripTrailingZeros().scale() > 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantities must be whole kilograms.");
        }
        allocations.create(request, customerId, body.weekStart(), body.allocationItems().stream()
                .map(item -> new PortalAllocationClient.RequestItem(item.taroTypeId(), item.quantity(), null))
                .toList(), body.deliveryAddress());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/allocations/{id}/pay")
    public ResponseEntity<Void> pay(@PathVariable Long id, HttpServletRequest request) {
        customer(request);
        allocations.pay(request, id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/allocations/{id}/request")
    public ResponseEntity<Void> cancel(@PathVariable Long id, HttpServletRequest request) {
        customer(request);
        allocations.cancel(request, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/allocations/{id}/invoice.pdf")
    public ResponseEntity<byte[]> invoice(@PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean inline, HttpServletRequest request) {
        customer(request);
        byte[] pdf = allocations.invoice(request, id, inline);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                                .filename("allocation-" + id + ".pdf").build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store").body(pdf);
    }

    private LoginClient.SessionPrincipal customer(HttpServletRequest request) {
        Object value = request.getAttribute(SessionAuthenticationFilter.PRINCIPAL_ATTRIBUTE);
        if (value instanceof LoginClient.SessionPrincipal principal && principal.active()
                && "CUSTOMER".equals(principal.role()) && principal.profileId() != null) return principal;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
}
