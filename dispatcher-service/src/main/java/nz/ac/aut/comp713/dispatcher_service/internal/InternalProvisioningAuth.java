package nz.ac.aut.comp713.dispatcher_service.internal;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** This credential is not shared with customer-service. */
@Component
final class InternalProvisioningAuth {

    private final byte[] expectedDigest;

    InternalProvisioningAuth(@Value("${dispatcher.internal.provisioning-secret:}") String secret) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("LOGIN_DISPATCHER_PROVISIONING_SECRET must contain at least 32 characters");
        }
        expectedDigest = sha256(secret);
    }

    void verify(HttpServletRequest request, String providedSecret) {
        boolean loopback;
        try {
            loopback = InetAddress.getByName(request.getRemoteAddr()).isLoopbackAddress();
        } catch (UnknownHostException exception) {
            loopback = false;
        }
        if (!loopback || !MessageDigest.isEqual(expectedDigest,
                sha256(providedSecret == null ? "" : providedSecret))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
