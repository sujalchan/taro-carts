package nz.ac.aut.comp713.allocation_service.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SessionAuthenticationFilterTest {

    private static final String TOKEN = "a".repeat(43);
    private static final String VALIDATION_SECRET = "validation-secret-0123456789abcdef0123456789abcdef";
    private static final String REFERENCE_SECRET = "reference-secret-0123456789abcdef0123456789abcdef";

    @Test
    void rejectsMissingInvalidAndUnavailableSessions() throws Exception {
        LoginClient login = mock(LoginClient.class);
        SessionAuthenticationFilter filter = new SessionAuthenticationFilter(login, "http://localhost:8085", VALIDATION_SECRET, REFERENCE_SECRET);

        var missing = response(filter, request("GET", "/api/v1/allocations"));
        assertThat(missing.getStatus()).isEqualTo(401);

        var spoofed = request("GET", "/api/v1/allocations");
        spoofed.addHeader("X-Role", "ADMIN");
        assertThat(response(filter, spoofed).getStatus()).isEqualTo(401);

        when(login.validate(TOKEN)).thenReturn(Optional.empty());
        assertThat(response(filter, authenticated("GET", "/api/v1/allocations")).getStatus()).isEqualTo(401);

        when(login.validate(TOKEN)).thenThrow(new LoginClient.UnavailableException());
        var unavailable = response(filter, authenticated("GET", "/api/v1/allocations"));
        assertThat(unavailable.getStatus()).isEqualTo(503);
        assertThat(unavailable.getHeader("Retry-After")).isEqualTo("1");
    }

    @Test
    void requiresAdminAndCsrfForManagementWrites() throws Exception {
        LoginClient login = mock(LoginClient.class);
        SessionAuthenticationFilter filter = new SessionAuthenticationFilter(login, "http://localhost:8085", VALIDATION_SECRET, REFERENCE_SECRET);
        when(login.validate(TOKEN)).thenReturn(Optional.of(
                new LoginClient.SessionPrincipal(2L, "customer", "CUSTOMER", 7L, true, Instant.now().plusSeconds(60))));
        assertThat(response(filter, authenticated("GET", "/api/v1/allocations")).getStatus()).isEqualTo(200);
        assertThat(response(filter, authenticated("GET", "/allocations")).getStatus()).isEqualTo(403);
        var browser = authenticated("GET", "/allocations");
        browser.addHeader("Accept", "text/html");
        var browserDenied = response(filter, browser);
        assertThat(browserDenied.getStatus()).isEqualTo(403);
        assertThat(browserDenied.getContentType()).isNull();

        when(login.validate(TOKEN)).thenReturn(Optional.of(
                new LoginClient.SessionPrincipal(1L, "admin", "ADMIN", null, true, Instant.now().plusSeconds(60))));
        var page = response(filter, authenticated("GET", "/api/v1/allocations"));
        assertThat(page.getStatus()).isEqualTo(200);
        assertThat(page.getHeader("Cache-Control")).isEqualTo("no-store");
        String csrf = page.getHeader("Set-Cookie").split("[=;]")[1];

        assertThat(response(filter, authenticated("POST", "/api/v1/allocations")).getStatus()).isEqualTo(403);
        var write = authenticated("POST", "/api/v1/allocations");
        write.addHeader("X-CSRF-Token", csrf);
        assertThat(response(filter, write).getStatus()).isEqualTo(200);
    }

    private MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr("127.0.0.1");
        return request;
    }

    private MockHttpServletRequest authenticated(String method, String path) {
        var request = request(method, path);
        request.setCookies(new Cookie("SESSION", TOKEN));
        return request;
    }

    private MockHttpServletResponse response(SessionAuthenticationFilter filter,
                                             MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}

