package nz.ac.aut.comp713.dispatcher_service;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import jakarta.servlet.http.Cookie;
import nz.ac.aut.comp713.dispatcher_service.dto.DispatcherResponse;
import nz.ac.aut.comp713.dispatcher_service.security.LoginClient;
import nz.ac.aut.comp713.dispatcher_service.service.DispatcherService;
import nz.ac.aut.comp713.dispatcher_service.service.PortalDeliveryClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Exercises session filtering, portal controller, and rendered browser templates together. */
@SpringBootTest(properties = {
        "spring.profiles.active=portal-integration",
        "service.auth.validation-secret=test-validation-secret-0123456789abcdef0123456789abcdef",
        "service.auth.reference-secret=test-reference-secret-0123456789abcdef0123456789abcdef",
        "allocation.assertion.secret=test-assertion-secret-0123456789abcdef0123456789abcdef"
})
@AutoConfigureMockMvc
class DispatcherPortalWebIntegrationTest {
    @Autowired MockMvc mvc;
    @MockitoBean LoginClient login;
    @MockitoBean DispatcherService dispatchers;
    @MockitoBean PortalDeliveryClient deliveries;

    @BeforeEach
    void sessions() {
        var expiry = Instant.now().plusSeconds(600);
        when(login.validate("driver-token")).thenReturn(Optional.of(
                new LoginClient.SessionPrincipal(10L, "driver", "DISPATCHER", 7L, true, expiry)));
        when(login.validate("admin-token")).thenReturn(Optional.of(
                new LoginClient.SessionPrincipal(1L, "admin", "ADMIN", null, true, expiry)));
        when(dispatchers.getById(7L)).thenReturn(
                new DispatcherResponse(7L, "Driver", null, null, "Auckland", true));
        when(dispatchers.getAll(null)).thenReturn(List.of(
                new DispatcherResponse(7L, "Driver", null, null, "Auckland", true)));
    }

    @Test
    void driverSeesNavigateLinkAndEmbeddedThreePointMap() throws Exception {
        when(deliveries.list(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(List.of(delivery("IN_TRANSIT")));
        when(deliveries.randomStart(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(11L), org.mockito.ArgumentMatchers.eq(false)))
                .thenReturn("16 Putney Way, Manukau");
        when(deliveries.locations(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(11L))).thenReturn(List.of());

        mvc.perform(get("/portal/deliveries").cookie(new Cookie("SESSION", "driver-token")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(">Navigate</a>")))
                .andExpect(content().string(containsString("69 Greenmeadows Avenue")));
        mvc.perform(get("/portal/deliveries/11/locations").param("embedded", "true")
                        .cookie(new Cookie("SESSION", "driver-token")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("embedded-map")))
                .andExpect(content().string(containsString("16 Putney Way, Manukau")))
                .andExpect(content().string(containsString("Delivery driver")))
                .andExpect(content().string(containsString("Estimated midpoint")))
                .andExpect(content().string(containsString("69 Greenmeadows Avenue")));
    }

    @Test
    void deliveredAddressIsHiddenFromDriverButVisibleToAdmin() throws Exception {
        when(deliveries.list(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(List.of(delivery("DELIVERED")));
        when(deliveries.list(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(7L)))
                .thenReturn(List.of(delivery("DELIVERED")));

        mvc.perform(get("/portal/deliveries").cookie(new Cookie("SESSION", "driver-token")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("69 Greenmeadows Avenue"))))
                .andExpect(content().string(not(containsString(">Navigate</a>"))));
        mvc.perform(get("/portal/deliveries").param("dispatcherId", "7")
                        .cookie(new Cookie("SESSION", "admin-token")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("69 Greenmeadows Avenue")));
        mvc.perform(get("/portal/deliveries/11/locations")
                        .cookie(new Cookie("SESSION", "driver-token")))
                .andExpect(status().isNotFound());
    }

    private PortalDeliveryClient.Delivery delivery(String status) {
        return new PortalDeliveryClient.Delivery(11L, 3L, "Customer", LocalDate.of(2026, 11, 16),
                status, "UNPAID", 7L, "69 Greenmeadows Avenue", false, List.of());
    }
}
