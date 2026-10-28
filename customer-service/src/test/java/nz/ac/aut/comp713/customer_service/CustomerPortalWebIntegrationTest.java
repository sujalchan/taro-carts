package nz.ac.aut.comp713.customer_service;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import jakarta.servlet.http.Cookie;
import nz.ac.aut.comp713.customer_service.dto.CustomerResponse;
import nz.ac.aut.comp713.customer_service.security.LoginClient;
import nz.ac.aut.comp713.customer_service.service.CustomerService;
import nz.ac.aut.comp713.customer_service.service.PortalAllocationClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Checks session scope, CSRF, controller actions and rendered customer pages together. */
@SpringBootTest(properties = {
        "spring.profiles.active=portal-integration",
        "service.auth.validation-secret=test-validation-secret-0123456789abcdef0123456789abcdef",
        "service.auth.reference-secret=test-reference-secret-0123456789abcdef0123456789abcdef",
        "allocation.assertion.secret=test-assertion-secret-0123456789abcdef0123456789abcdef"
})
@AutoConfigureMockMvc
class CustomerPortalWebIntegrationTest {
    @Autowired MockMvc mvc;
    @MockitoBean LoginClient login;
    @MockitoBean CustomerService customers;
    @MockitoBean PortalAllocationClient allocations;

    private final PortalAllocationClient.Allocation allocation = new PortalAllocationClient.Allocation(
            21L, 3L, null, "Customer", LocalDate.of(2026, 11, 16), "ALLOCATED", "UNPAID",
            true, List.of(new PortalAllocationClient.Item(9L, "Samoan Taro", 50, BigDecimal.valueOf(5))),
            null, "69 Greenmeadows Avenue");

    @BeforeEach
    void sessions() {
        var expiry = Instant.now().plusSeconds(600);
        when(login.validate("customer-token")).thenReturn(Optional.of(
                new LoginClient.SessionPrincipal(10L, "customer", "CUSTOMER", 3L, true, expiry)));
        when(login.validate("other-token")).thenReturn(Optional.of(
                new LoginClient.SessionPrincipal(11L, "other", "CUSTOMER", 4L, true, expiry)));
        when(customers.getCustomerById(3L)).thenReturn(
                new CustomerResponse(3L, "Customer", "Auckland", "Contact", "1", true));
        when(customers.getCustomerById(4L)).thenReturn(
                new CustomerResponse(4L, "Other", "Auckland", "Contact", "2", true));
        when(allocations.list(any(), eq(3L))).thenReturn(List.of(allocation));
        when(allocations.detail(any(), eq(21L))).thenReturn(allocation);
    }

    @Test
    void customerCanViewOwnAllocationAndPayWithPageCsrfToken() throws Exception {
        mvc.perform(get("/portal/allocations").cookie(new Cookie("SESSION", "customer-token")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("2026-11-16")))
                .andExpect(content().string(containsString("/portal/allocations/21")));

        String html = mvc.perform(get("/portal/allocations/21")
                        .cookie(new Cookie("SESSION", "customer-token")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("69 Greenmeadows Avenue")))
                .andExpect(content().string(containsString("Samoan Taro")))
                .andExpect(content().string(containsString("Mark as paid")))
                .andReturn().getResponse().getContentAsString();
        String csrf = html.split("name=\"csrf\" value=\"", 2)[1].split("\"", 2)[0];
        mvc.perform(post("/portal/allocations/21/pay").param("csrf", csrf)
                        .cookie(new Cookie("SESSION", "customer-token")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/portal/allocations"));
        verify(allocations).pay(any(), eq(21L));
    }

    @Test
    void customerCanSubmitRequestFromRenderedForm() throws Exception {
        when(allocations.taroTypes(any())).thenReturn(List.of(
                new PortalAllocationClient.TaroType(9L, "Samoan Taro", "", BigDecimal.valueOf(5))));
        String html = mvc.perform(get("/portal/allocations/request")
                        .cookie(new Cookie("SESSION", "customer-token")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Samoan Taro")))
                .andReturn().getResponse().getContentAsString();
        String csrf = html.split("name=\"csrf\" value=\"", 2)[1].split("\"", 2)[0];
        mvc.perform(post("/portal/allocations/request").param("csrf", csrf)
                        .param("weekStart", "2026-11-16").param("taroTypeId", "9")
                        .param("quantity", "50").param("deliveryAddress", "  Auckland  ")
                        .cookie(new Cookie("SESSION", "customer-token")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/portal/allocations"));
        verify(allocations).create(any(), eq(3L), eq(LocalDate.of(2026, 11, 16)),
                eq(9L), eq(BigDecimal.valueOf(50)), eq("Auckland"));
    }

    @Test
    void anotherCustomerCannotViewOrPayForThisAllocation() throws Exception {
        mvc.perform(get("/portal/allocations/21").cookie(new Cookie("SESSION", "other-token")))
                .andExpect(status().isForbidden());
        String csrfCookie = mvc.perform(get("/portal/allocations")
                        .cookie(new Cookie("SESSION", "other-token")))
                .andExpect(status().isOk()).andReturn().getResponse().getHeader("Set-Cookie");
        String csrf = csrfCookie.split("CUSTOMER_CSRF=", 2)[1].split(";", 2)[0];
        mvc.perform(post("/portal/allocations/21/pay").param("csrf", csrf)
                        .cookie(new Cookie("SESSION", "other-token")))
                .andExpect(status().isForbidden());
    }
}
