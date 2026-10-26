package nz.ac.aut.comp713.dispatcher_service.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.server.ResponseStatusException;

import nz.ac.aut.comp713.dispatcher_service.dto.DispatcherResponse;
import nz.ac.aut.comp713.dispatcher_service.security.LoginClient;
import nz.ac.aut.comp713.dispatcher_service.security.SessionAuthenticationFilter;
import nz.ac.aut.comp713.dispatcher_service.service.DispatcherService;
import nz.ac.aut.comp713.dispatcher_service.service.PortalDeliveryClient;

class DispatcherPortalControllerTest {
    private final DispatcherService dispatchers = mock(DispatcherService.class);
    private final PortalDeliveryClient deliveries = mock(PortalDeliveryClient.class);
    private final DispatcherPortalController controller = new DispatcherPortalController(dispatchers, deliveries);

    @Test
    void dispatcherLandingUsesOwnProfileAndRejectsForgedSelection() {
        var request = request("DISPATCHER", 7L);
        when(dispatchers.getById(7L)).thenReturn(
                new DispatcherResponse(7L, "Driver", null, null, "Auckland", true));
        when(deliveries.list(request, null)).thenReturn(List.of());

        assertThat(controller.list(null, request, new ExtendedModelMap())).isEqualTo("dispatcher-portal");
        verify(deliveries).list(request, null);
        assertThatThrownBy(() -> controller.list(8L, request, new ExtendedModelMap()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void adminMustSelectDispatcherAndStatusActionChecksAssignment() {
        var admin = request("ADMIN", null);
        assertThat(controller.list(null, admin, new ExtendedModelMap())).isEqualTo("dispatcher-portal");
        verifyNoInteractions(deliveries);

        when(dispatchers.getById(7L)).thenReturn(
                new DispatcherResponse(7L, "Driver", null, null, "Auckland", true));
        when(deliveries.list(admin, 7L)).thenReturn(List.of(new PortalDeliveryClient.Delivery(
                11L, 3L, "Customer", LocalDate.now(), "AWAITING_DISPATCH", "UNPAID", 7L, "Auckland",
                false, List.of())));
        assertThat(controller.update(11L, 7L, "IN_TRANSIT", admin, new ExtendedModelMap()))
                .isEqualTo("redirect:/portal/deliveries?dispatcherId=7");
        verify(deliveries).advance(admin, 11L, "IN_TRANSIT");
        assertThatThrownBy(() -> controller.update(12L, 7L, "IN_TRANSIT", admin, new ExtendedModelMap()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    private MockHttpServletRequest request(String role, Long profileId) {
        var request = new MockHttpServletRequest();
        request.setAttribute(SessionAuthenticationFilter.PRINCIPAL_ATTRIBUTE,
                new LoginClient.SessionPrincipal(2L, "user", role, profileId, true,
                        Instant.now().plusSeconds(60)));
        return request;
    }
}
