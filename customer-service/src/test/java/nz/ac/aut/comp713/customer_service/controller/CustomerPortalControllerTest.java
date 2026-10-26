package nz.ac.aut.comp713.customer_service.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.server.ResponseStatusException;

import nz.ac.aut.comp713.customer_service.dto.CustomerResponse;
import nz.ac.aut.comp713.customer_service.security.LoginClient;
import nz.ac.aut.comp713.customer_service.security.SessionAuthenticationFilter;
import nz.ac.aut.comp713.customer_service.service.CustomerService;
import nz.ac.aut.comp713.customer_service.service.PortalAllocationClient;

class CustomerPortalControllerTest {
    private final CustomerService customers = mock(CustomerService.class);
    private final PortalAllocationClient allocations = mock(PortalAllocationClient.class);
    private final CustomerPortalController controller = new CustomerPortalController(customers, allocations);

    @Test
    void customerListUsesSessionProfileAndRejectsForgedSelection() {
        var request = request("CUSTOMER", 7L);
        when(customers.getCustomerById(7L)).thenReturn(
                new CustomerResponse(7L, "Island Foods", "Auckland", null, null, true));
        when(allocations.list(request, 7L)).thenReturn(List.of());

        assertThat(controller.list(null, request, new ExtendedModelMap())).isEqualTo("customer-portal");
        verify(allocations).list(request, 7L);
        assertThatThrownBy(() -> controller.list(8L, request, new ExtendedModelMap()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void adminMustSelectCustomerBeforeFetchingAllocations() {
        var request = request("ADMIN", null);
        assertThat(controller.list(null, request, new ExtendedModelMap())).isEqualTo("customer-portal");
        verifyNoInteractions(allocations);
    }

    @Test
    void foreignAndMissingAllocationsLookTheSameInPortal() {
        var request = request("CUSTOMER", 7L);
        when(customers.getCustomerById(7L)).thenReturn(
                new CustomerResponse(7L, "Island Foods", "Auckland", null, null, true));
        when(allocations.detail(request, 99L))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> controller.detail(99L, null, request, new ExtendedModelMap()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void ownInvoiceCanBeViewedInlineOrDownloaded() {
        var request = request("CUSTOMER", 7L);
        when(customers.getCustomerById(7L)).thenReturn(
                new CustomerResponse(7L, "Island Foods", "Auckland", null, null, true));
        when(allocations.detail(request, 11L)).thenReturn(new PortalAllocationClient.Allocation(
                11L, 7L, 7L, "Island Foods", java.time.LocalDate.now(), "DELIVERED", "UNPAID",
                true, List.of(), 3L, "Auckland"));
        when(allocations.invoice(request, 11L, true)).thenReturn(new byte[] {1, 2});
        when(allocations.invoice(request, 11L, false)).thenReturn(new byte[] {1, 2});
        assertThat(controller.invoice(11L, null, true, request, new ExtendedModelMap())
                .getHeaders().getFirst("Content-Disposition")).contains("inline");
        assertThat(controller.invoice(11L, null, false, request, new ExtendedModelMap())
                .getHeaders().getFirst("Content-Disposition")).contains("attachment");
    }

    private MockHttpServletRequest request(String role, Long profileId) {
        var request = new MockHttpServletRequest();
        request.setAttribute(SessionAuthenticationFilter.PRINCIPAL_ATTRIBUTE,
                new LoginClient.SessionPrincipal(2L, "user", role, profileId, true,
                        Instant.now().plusSeconds(60)));
        return request;
    }
}
