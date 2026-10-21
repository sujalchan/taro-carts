package nz.ac.aut.comp713.allocation_service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import nz.ac.aut.comp713.allocation_service.client.CustomerClient;
import nz.ac.aut.comp713.allocation_service.client.CustomerResponse;
import nz.ac.aut.comp713.allocation_service.dto.WeeklyAllocationResponse;
import nz.ac.aut.comp713.allocation_service.model.DeliveryStatus;
import nz.ac.aut.comp713.allocation_service.model.PaymentStatus;
import nz.ac.aut.comp713.allocation_service.security.AllocationAccess;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class AllocationAccessTest {
    private final CustomerClient customers = mock(CustomerClient.class);
    private final AllocationAccess access = new AllocationAccess(customers);
    private final WeeklyAllocationResponse order = new WeeklyAllocationResponse(1L, 5L, "Customer",
            LocalDate.of(2026, 10, 19), DeliveryStatus.REQUESTED_ALLOCATION,
            PaymentStatus.UNPAID, "dispatcher-1", List.of());

    @Test
    void administratorCanReadAndCreateAnyOrder() {
        Authentication admin = actor("admin", "ADMIN");
        assertTrue(access.canRead(order, admin));
        access.requireCreate(5L, admin);
    }

    @Test
    void customerCanOnlyUseTheirLinkedActiveCustomer() {
        when(customers.getCustomer(5L)).thenReturn(new CustomerResponse(5L, "Customer", null, null,
                true, "customer-1"));
        Authentication owner = actor("customer-1", "CUSTOMER");
        Authentication other = actor("customer-2", "CUSTOMER");

        assertTrue(access.canRead(order, owner));
        assertFalse(access.canRead(order, other));
        access.requireCreate(5L, owner);
        assertThrows(AccessDeniedException.class, () -> access.requireCreate(5L, other));
        when(customers.getCustomer(5L)).thenReturn(new CustomerResponse(5L, "Customer", null, null,
                false, "customer-1"));
        assertThrows(AccessDeniedException.class, () -> access.requireCreate(5L, owner));
    }

    @Test
    void dispatcherCanReadOnlyAssignedOrder() {
        assertTrue(access.canRead(order, actor("dispatcher-1", "DISPATCHER")));
        assertFalse(access.canRead(order, actor("dispatcher-2", "DISPATCHER")));
    }

    private Authentication actor(String name, String role) {
        return new UsernamePasswordAuthenticationToken(name, "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
}
