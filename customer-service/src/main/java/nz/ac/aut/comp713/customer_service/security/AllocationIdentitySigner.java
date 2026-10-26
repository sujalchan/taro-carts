package nz.ac.aut.comp713.customer_service.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Signs a single downstream allocation request after local session validation. */
@Component
@Profile("!legacy-tests")
public class AllocationIdentitySigner {
    private final byte[] key;

    public AllocationIdentitySigner(@Value("${allocation.assertion.secret:}") String secret) {
        if (secret.length() < 32) throw new IllegalStateException("Customer allocation assertion secret must contain at least 32 characters");
        key = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String sign(HttpServletRequest request, String method, String path) {
        Object attribute = request.getAttribute(SessionAuthenticationFilter.PRINCIPAL_ATTRIBUTE);
        if (!(attribute instanceof LoginClient.SessionPrincipal principal) || !principal.active()
                || !("CUSTOMER".equals(principal.role()) || "ADMIN".equals(principal.role()))
                || (!"ADMIN".equals(principal.role()) && principal.profileId() == null)
                || !path.startsWith("/api/v1/allocations")
                || !("GET".equals(method) || "POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method))) {
            throw new IllegalArgumentException("Cannot sign an unauthorized allocation request");
        }
        long issued = Instant.now().getEpochSecond();
        long expiry = Math.min(issued + 45, principal.expiresAt().getEpochSecond());
        if (expiry <= issued) throw new IllegalArgumentException("Session expired");
        String payload = String.join("\n", "customer", principal.accountId().toString(), principal.role(),
                principal.profileId() == null ? "" : principal.profileId().toString(),
                Long.toString(issued), Long.toString(expiry), method, path, UUID.randomUUID().toString());
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String signed = "v1." + encoded;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return signed + "." + Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(signed.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception exception) {
            throw new IllegalStateException("HMAC unavailable", exception);
        }
    }
}
