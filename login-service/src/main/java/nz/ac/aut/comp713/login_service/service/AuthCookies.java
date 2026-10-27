package nz.ac.aut.comp713.login_service.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

/**
 * Issues host-only session and CSRF cookies and compares submitted CSRF tokens
 * in constant time.
 */
@Service
public class AuthCookies {

    public static final String SESSION = "SESSION";
    public static final String CSRF = "LOGIN_CSRF";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final boolean secure;

    public AuthCookies(@Value("${login.cookie.secure:false}") boolean secure) {
        this.secure = secure;
    }

    /**
     * Creates a random form token and writes its HttpOnly cookie; the returned
     * value goes in the form.
     */
    public String issueCsrf(HttpServletResponse response) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        add(response, CSRF, token, Duration.ofHours(2));
        return token;
    }

    /**
     * Requires matching well-formed form and cookie tokens before a browser
     * mutation.
     */
    public boolean validCsrf(String formToken, String cookieToken) {
        if (formToken == null || cookieToken == null
                || !formToken.matches("[A-Za-z0-9_-]{43}") || !cookieToken.matches("[A-Za-z0-9_-]{43}")) {
            return false;
        }
        return MessageDigest.isEqual(formToken.getBytes(StandardCharsets.US_ASCII),
                cookieToken.getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * Sets the opaque session cookie with the server-side session's remaining
     * lifetime.
     */
    public void issueSession(HttpServletResponse response, SessionService.IssuedSession session) {
        add(response, SESSION, session.token(), Duration.between(java.time.Instant.now(), session.expiresAt()));
    }

    /** Expires the session cookie in the browser. */
    public void clearSession(HttpServletResponse response) {
        add(response, SESSION, "", Duration.ZERO);
    }

    /**
     * Expires the form CSRF cookie after a successful sign-out or password change.
     */
    public void clearCsrf(HttpServletResponse response) {
        add(response, CSRF, "", Duration.ZERO);
    }

    private void add(HttpServletResponse response, String name, String value, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
