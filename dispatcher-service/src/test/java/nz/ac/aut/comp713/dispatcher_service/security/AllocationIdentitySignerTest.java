package nz.ac.aut.comp713.dispatcher_service.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class AllocationIdentitySignerTest {
    @Test
    void signsOnlyAValidatedDispatcherOrAdminForAnAllocationRoute() {
        var signer = new AllocationIdentitySigner("dispatcher-assertion-key-0123456789abcdef0123456789");
        var request = new MockHttpServletRequest();
        request.setAttribute(SessionAuthenticationFilter.PRINCIPAL_ATTRIBUTE,
                new LoginClient.SessionPrincipal(4L, "dispatcher", "DISPATCHER", 9L, true,
                        Instant.now().plusSeconds(120)));
        String token = signer.sign(request, "GET", "/api/v1/allocations/dispatcher/me");
        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
        assertThat(payload.split("\n")).contains("dispatcher", "4", "DISPATCHER", "9", "GET", "/api/v1/allocations/dispatcher/me");
        assertThrows(IllegalArgumentException.class, () -> signer.sign(request, "GET", "/api/v1/dispatchers"));
    }
}
