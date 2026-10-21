package nz.ac.aut.comp713.allocation_service.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import nz.ac.aut.comp713.allocation_service.client.CustomerClient;
import nz.ac.aut.comp713.allocation_service.client.CustomerResponse;
import nz.ac.aut.comp713.allocation_service.dto.WeeklyAllocationResponse;

@Component
public class AllocationAccess {
    private final CustomerClient customers;

    public AllocationAccess(CustomerClient customers) {
        this.customers = customers;
    }

    public boolean canRead(WeeklyAllocationResponse allocation, Authentication actor) {
        if (hasRole(actor, "ADMIN")) return true;
        if (hasRole(actor, "DISPATCHER"))
            return actor.getName().equals(allocation.dispatcherAccountId());
        if (hasRole(actor, "CUSTOMER")) {
            CustomerResponse customer = customers.getCustomer(allocation.customerId());
            return actor.getName().equals(customer.accountId());
        }
        return false;
    }

    public void requireRead(WeeklyAllocationResponse allocation, Authentication actor) {
        if (!canRead(allocation, actor)) throw new AccessDeniedException("This order is not available to this account");
    }

    public void requireCreate(Long customerId, Authentication actor) {
        if (hasRole(actor, "ADMIN")) return;
        if (hasRole(actor, "CUSTOMER")) {
            CustomerResponse customer = customers.getCustomer(customerId);
            if (Boolean.TRUE.equals(customer.active())
                    && actor.getName().equals(customer.accountId())) return;
        }
        throw new AccessDeniedException("This customer account cannot create that order");
    }

    private boolean hasRole(Authentication actor, String role) {
        return actor.getAuthorities().stream().anyMatch(authority ->
                authority.getAuthority().equals("ROLE_" + role));
    }
}
