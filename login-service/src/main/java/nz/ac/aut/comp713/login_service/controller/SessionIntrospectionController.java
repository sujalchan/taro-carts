package nz.ac.aut.comp713.login_service.controller;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import nz.ac.aut.comp713.login_service.service.SessionService;

/** Validates sessions for loopback backend callers holding a configured service credential. */
@RestController
public class SessionIntrospectionController {

    /** Opaque token supplied by a backend service for validation. */
    public record IntrospectionRequest(@NotBlank(message = "Session token is required.") String token) {}
    /** Minimum identity and session state returned to the trusted backend caller. */
    public record SessionPrincipal(Long accountId, String username, String role,
                                   Long profileId, boolean active, Instant expiresAt) {}

    private final SessionService sessions;
    private final List<byte[]> credentialDigests;

    public SessionIntrospectionController(SessionService sessions,
            @Value("${login.validation.customer-secret:}") String customerSecret,
            @Value("${login.validation.dispatcher-secret:}") String dispatcherSecret,
            @Value("${login.validation.allocation-secret:}") String allocationSecret,
            @Value("${login.validation.taro-secret:}") String taroSecret,
            @Value("${login.customer-provisioning-secret:}") String customerProvisioningSecret,
            @Value("${login.dispatcher-provisioning-secret:}") String dispatcherProvisioningSecret) {
        this.sessions = sessions;
        List<String> credentials = List.of(customerSecret, dispatcherSecret, allocationSecret, taroSecret);
        if (credentials.stream().anyMatch(value -> value.length() < 32)
                || credentials.stream().distinct().count() != credentials.size()) {
            throw new IllegalStateException("Four distinct session validation secrets of at least 32 characters are required");
        }
        if (credentials.contains(customerProvisioningSecret)
                || credentials.contains(dispatcherProvisioningSecret)) {
            throw new IllegalStateException("Session validation and provisioning credentials must be different");
        }
        credentialDigests = credentials.stream().map(SessionIntrospectionController::digest).toList();
    }

    /** Returns the minimum identity data required by a trusted service to authorize a request. */
    @PostMapping("/internal/v1/sessions/introspect")
    public ResponseEntity<SessionPrincipal> introspect(
            @RequestHeader(value = "X-Service-Validation-Key", required = false) String credential,
            @Valid @RequestBody IntrospectionRequest request, HttpServletRequest servletRequest) {
        boolean loopback;
        try {
            loopback = InetAddress.getByName(servletRequest.getRemoteAddr()).isLoopbackAddress();
        } catch (UnknownHostException exception) {
            loopback = false;
        }
        byte[] providedDigest = digest(credential == null ? "" : credential);
        boolean trusted = false;
        for (byte[] expected : credentialDigests) trusted |= MessageDigest.isEqual(expected, providedDigest);
        if (!loopback || !trusted) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "This session validation request is not authorized.");
        }

        var session = sessions.validate(request.token())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "This session is invalid or has expired. Ask the user to sign in again."));
        var account = session.getAccount();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new SessionPrincipal(account.getId(), account.getUsername(), account.getRole().name(),
                        account.getProfileId(), account.isActive(), session.getExpiresAt()));
    }

    private static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
