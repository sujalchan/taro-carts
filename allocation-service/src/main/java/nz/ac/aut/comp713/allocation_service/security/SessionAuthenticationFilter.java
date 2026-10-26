package nz.ac.aut.comp713.allocation_service.security;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Set;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/** Fail-closed authentication boundary for browser and API requests. */
@Component
@Profile("!legacy-tests")
public class SessionAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger AUDIT = LoggerFactory.getLogger("security.audit");

    public static final String PRINCIPAL_ATTRIBUTE = SessionAuthenticationFilter.class.getName() + ".principal";
    public static final String CSRF_ATTRIBUTE = SessionAuthenticationFilter.class.getName() + ".csrf";
    private static final String REFERENCE_ROOT = "";
    private static final String PROVISIONING_ROOT = "";
    private static final String CSRF_COOKIE = "ALLOCATION_CSRF";
    private static final Set<String> LANDING_PATHS = Set.of("/allocations", "/allocations-client.html");

    private final LoginClient login;
    private final String loginPageUrl;
    private final byte[] csrfKey;
    private final byte[] referenceDigest;
    @Autowired(required = false)
    private IdentityAssertionVerifier assertionVerifier;

    @Value("${service.auth.cookie-secure:false}")
    private boolean secureCookie;

    public SessionAuthenticationFilter(LoginClient login,
            @Value("${service.auth.login-url:http://localhost:8085}") String loginUrl,
            @Value("${service.auth.validation-secret:}") String validationSecret,
            @Value("${service.auth.reference-secret:}") String referenceSecret) {
        this.login = login;
        this.loginPageUrl = loginUrl.replaceAll("/+$", "") + "/login";
        if (validationSecret.length() < 32) {
            throw new IllegalStateException("Session validation secret must contain at least 32 characters");
        }
        if (!REFERENCE_ROOT.isEmpty() && referenceSecret.length() < 32) {
            throw new IllegalStateException("Allocation reference secret must contain at least 32 characters");
        }
        this.csrfKey = validationSecret.getBytes(StandardCharsets.UTF_8);
        this.referenceDigest = referenceSecret.isEmpty() ? null : sha256(referenceSecret);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        response.setHeader("Vary", "Cookie");

        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.equals("/error") || path.equals("/api/v1/google-places/config")
                || (!PROVISIONING_ROOT.isEmpty() && path.startsWith(PROVISIONING_ROOT))
                || isAuthenticatedReferenceRequest(request, path)) {
            chain.doFilter(request, response);
            return;
        }

        if (request.getHeader(IdentityAssertionVerifier.HEADER) != null) {
            LoginClient.SessionPrincipal assertionPrincipal = assertionVerifier == null
                    ? null : assertionVerifier.verify(request);
            if (assertionPrincipal == null) {
                deny(request, response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid identity assertion");
                return;
            }
            request.setAttribute(PRINCIPAL_ATTRIBUTE, assertionPrincipal);
            chain.doFilter(request, response);
            return;
        }

        String sessionToken = cookieValue(request, "SESSION");
        if (sessionToken == null) {
            deny(request, response, HttpServletResponse.SC_UNAUTHORIZED, "Sign in required");
            return;
        }

        LoginClient.SessionPrincipal principal;
        try {
            var validated = login.validate(sessionToken);
            if (validated.isEmpty()) {
                deny(request, response, HttpServletResponse.SC_UNAUTHORIZED, "Session is invalid or expired");
                return;
            }
            principal = validated.get();
        } catch (LoginClient.UnavailableException exception) {
            response.setHeader("Retry-After", "1");
            deny(request, response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Authentication service unavailable");
            return;
        }
        request.setAttribute(PRINCIPAL_ATTRIBUTE, principal);

        // Only the allocation API is role-aware. The existing management pages,
        // reference APIs and static management clients remain admin-only.
        if (!"ADMIN".equals(principal.role()) && !path.startsWith("/api/v1/allocations")) {
            deny(request, response, HttpServletResponse.SC_FORBIDDEN, "Access denied");
            return;
        }

        String expectedCsrf = csrf(sessionToken);
        request.setAttribute(CSRF_ATTRIBUTE, expectedCsrf);
        if (isSafe(request.getMethod())) {
            response.addHeader("Set-Cookie", CSRF_COOKIE + "=" + expectedCsrf
                    + "; Path=/; SameSite=Lax" + (request.isSecure() || secureCookie ? "; Secure" : ""));
        } else {
            String supplied = request.getHeader("X-CSRF-Token");
            if (supplied == null) supplied = request.getParameter("csrf");
            if (supplied == null || !MessageDigest.isEqual(
                    expectedCsrf.getBytes(StandardCharsets.US_ASCII),
                    supplied.getBytes(StandardCharsets.US_ASCII))) {
                deny(request, response, HttpServletResponse.SC_FORBIDDEN, "CSRF token required");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private boolean isAuthenticatedReferenceRequest(HttpServletRequest request, String path) {
        if (REFERENCE_ROOT.isEmpty() || !"GET".equals(request.getMethod())
                || !(path.equals(REFERENCE_ROOT) || path.matches(REFERENCE_ROOT + "/[0-9]+"))
                || referenceDigest == null) return false;
        String provided = request.getHeader("X-Allocation-Reference-Key");
        if (provided == null || !isLoopback(request.getRemoteAddr())) return false;
        return MessageDigest.isEqual(referenceDigest, sha256(provided));
    }

    private static boolean isLoopback(String address) {
        try {
            return InetAddress.getByName(address).isLoopbackAddress();
        } catch (UnknownHostException exception) {
            return false;
        }
    }

    private static String cookieValue(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }

    private static boolean isSafe(String method) {
        return "GET".equals(method) || "HEAD".equals(method);
    }

    private String csrf(String token) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(csrfKey, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not calculate CSRF token", exception);
        }
    }

    private static byte[] sha256(String value) {
        try {
            return java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private String loginRedirect(HttpServletRequest request) {
        String path = request.getRequestURI();
        if ("localhost".equalsIgnoreCase(request.getServerName()) && LANDING_PATHS.contains(path)) {
            String target = "http://localhost:" + request.getServerPort() + path;
            return loginPageUrl + "?returnTo=" + URLEncoder.encode(target, StandardCharsets.UTF_8);
        }
        return loginPageUrl;
    }

    private void deny(HttpServletRequest request, HttpServletResponse response,
                             int status, String message) throws IOException {
        AUDIT.warn("event=authorization.denied service=allocation status={} method={} path={}",
                status, request.getMethod(), request.getRequestURI());
        if (status == HttpServletResponse.SC_UNAUTHORIZED && "GET".equals(request.getMethod())
                && !request.getRequestURI().startsWith("/api/")
                && !request.getRequestURI().startsWith("/internal/")
                && !request.getRequestURI().matches(".*\\.(js|css|png|svg|ico|map)$")) {
            response.sendRedirect(loginRedirect(request));
            return;
        }
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("text/html")
                && !request.getRequestURI().startsWith("/api/")
                && !request.getRequestURI().startsWith("/internal/")
                && !request.getRequestURI().matches(".*\\.(js|css|png|svg|ico|map)$")) {
            response.sendError(status);
            return;
        }
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}

