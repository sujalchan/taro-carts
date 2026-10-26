package nz.ac.aut.comp713.allocation_service.security;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Verifies one-use, route-bound assertions from the two portal services. */
@Component
@Profile("!legacy-tests")
public class IdentityAssertionVerifier {
    public static final String HEADER = "X-Allocation-Identity-Assertion";
    private final byte[] customerKey;
    private final byte[] dispatcherKey;
    private final ConcurrentHashMap<String, Long> usedNonces = new ConcurrentHashMap<>();

    public IdentityAssertionVerifier(
            @Value("${allocation.assertion.customer-secret:}") String customerSecret,
            @Value("${allocation.assertion.dispatcher-secret:}") String dispatcherSecret) {
        if (customerSecret.length() < 32 || dispatcherSecret.length() < 32
                || customerSecret.equals(dispatcherSecret)) {
            throw new IllegalStateException("Distinct customer and dispatcher assertion secrets of at least 32 characters are required");
        }
        customerKey = customerSecret.getBytes(StandardCharsets.UTF_8);
        dispatcherKey = dispatcherSecret.getBytes(StandardCharsets.UTF_8);
    }

    public LoginClient.SessionPrincipal verify(HttpServletRequest request) {
        String assertion = request.getHeader(HEADER);
        if (assertion == null || assertion.length() > 2048 || !privateTransport(request)) return null;
        try {
            String[] token = assertion.split("\\.", -1);
            if (token.length != 3 || !"v1".equals(token[0])) return null;
            String[] fields = new String(Base64.getUrlDecoder().decode(token[1]), StandardCharsets.UTF_8)
                    .split("\\n", -1);
            if (fields.length != 9) return null;
            byte[] key = switch (fields[0]) {
                case "customer" -> customerKey;
                case "dispatcher" -> dispatcherKey;
                default -> null;
            };
            if (key == null || !MessageDigest.isEqual(mac(key, "v1." + token[1]),
                    Base64.getUrlDecoder().decode(token[2]))) return null;
            long accountId = Long.parseLong(fields[1]);
            Long profileId = fields[3].isEmpty() ? null : Long.valueOf(fields[3]);
            long issued = Long.parseLong(fields[4]);
            long expiry = Long.parseLong(fields[5]);
            long now = Instant.now().getEpochSecond();
            if (accountId <= 0 || (profileId != null && profileId <= 0)
                    || issued > now + 5 || expiry <= now || expiry - issued > 60
                    || expiry <= issued || !fields[6].equals(request.getMethod())
                    || !fields[7].equals(request.getRequestURI())
                    || !fields[7].startsWith("/api/v1/allocations")
                    || !fields[8].matches("[a-f0-9-]{36}")) return null;
            String role = fields[2];
            if (!("ADMIN".equals(role) || (profileId != null
                    && (("customer".equals(fields[0]) && "CUSTOMER".equals(role))
                        || ("dispatcher".equals(fields[0]) && "DISPATCHER".equals(role)))))) return null;
            usedNonces.entrySet().removeIf(entry -> entry.getValue() <= now);
            if (usedNonces.putIfAbsent(fields[0] + ":" + fields[8], expiry) != null) return null;
            return new LoginClient.SessionPrincipal(accountId, "", role, profileId, true,
                    Instant.ofEpochSecond(expiry));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static boolean privateTransport(HttpServletRequest request) {
        if (request.isSecure()) return true;
        try {
            return InetAddress.getByName(request.getRemoteAddr()).isLoopbackAddress();
        } catch (Exception exception) {
            return false;
        }
    }

    private static byte[] mac(byte[] key, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception exception) {
            throw new IllegalStateException("HMAC unavailable", exception);
        }
    }
}
