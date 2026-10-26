package nz.ac.aut.comp713.allocation_service.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class IdentityAssertionVerifierTest {
    private static final String CUSTOMER_KEY = "customer-assertion-key-0123456789abcdef0123456789";
    private static final String DISPATCHER_KEY = "dispatcher-assertion-key-0123456789abcdef0123456789";
    private static final String PATH = "/api/v1/allocations/17";

    @Test
    void acceptsOnlyOnceForTheSignedMethodAndPath() throws Exception {
        IdentityAssertionVerifier verifier = new IdentityAssertionVerifier(CUSTOMER_KEY, DISPATCHER_KEY);
        String token = token("customer", "CUSTOMER", "7", CUSTOMER_KEY, "GET", PATH, 45);
        assertThat(verifier.verify(request("GET", PATH, token)).profileId()).isEqualTo(7L);
        assertThat(verifier.verify(request("GET", PATH, token))).isNull();
        String fresh = token("customer", "CUSTOMER", "7", CUSTOMER_KEY, "GET", PATH, 45);
        assertThat(verifier.verify(request("GET", "/api/v1/allocations/18", fresh))).isNull();
        assertThat(verifier.verify(request("POST", PATH, fresh))).isNull();
        assertThat(verifier.verify(request("GET", PATH, fresh))).isNotNull();
    }

    @Test
    void rejectsExpiredWrongKeyRoleAndNonPrivateTransport() throws Exception {
        IdentityAssertionVerifier verifier = new IdentityAssertionVerifier(CUSTOMER_KEY, DISPATCHER_KEY);
        assertThat(verifier.verify(request("GET", PATH,
                token("customer", "CUSTOMER", "7", CUSTOMER_KEY, "GET", PATH, -1)))).isNull();
        assertThat(verifier.verify(request("GET", PATH,
                token("customer", "CUSTOMER", "7", DISPATCHER_KEY, "GET", PATH, 45)))).isNull();
        assertThat(verifier.verify(request("GET", PATH,
                token("customer", "DISPATCHER", "7", CUSTOMER_KEY, "GET", PATH, 45)))).isNull();
        var remote = request("GET", PATH,
                token("customer", "CUSTOMER", "7", CUSTOMER_KEY, "GET", PATH, 45));
        remote.setRemoteAddr("192.0.2.1");
        assertThat(verifier.verify(remote)).isNull();
    }

    private static MockHttpServletRequest request(String method, String path, String token) {
        var request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr("127.0.0.1");
        request.addHeader(IdentityAssertionVerifier.HEADER, token);
        return request;
    }

    private static String token(String origin, String role, String profileId, String key,
            String method, String path, long lifetime) throws Exception {
        long issued = Instant.now().getEpochSecond();
        String payload = String.join("\n", origin, "5", role, profileId,
                Long.toString(issued), Long.toString(issued + lifetime), method, path,
                UUID.randomUUID().toString());
        String value = "v1." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return value + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.US_ASCII)));
    }
}
