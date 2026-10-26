package nz.ac.aut.comp713.dispatcher_service.security;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Validates opaque browser sessions with login-service on every request. */
@Component
@Profile("!legacy-tests")
public class LoginClient {

    public record SessionPrincipal(Long accountId, String username, String role,
                                   Long profileId, boolean active, Instant expiresAt) {}
    private record IntrospectionRequest(String token) {}

    public static final class UnavailableException extends RuntimeException {}

    private final RestClient client;
    private final String validationSecret;

    public LoginClient(
            @Value("${service.auth.login-url:http://localhost:8085}") String loginUrl,
            @Value("${service.auth.validation-secret:}") String validationSecret,
            @Value("${service.auth.connect-timeout:PT2S}") Duration connectTimeout,
            @Value("${service.auth.read-timeout:PT3S}") Duration readTimeout) {
        if (validationSecret.length() < 32) {
            throw new IllegalStateException("LOGIN_DISPATCHER_VALIDATION_SECRET must contain at least 32 characters");
        }
        URI uri = URI.create(loginUrl);
        if (!("http".equalsIgnoreCase(uri.getScheme()) && "localhost".equalsIgnoreCase(uri.getHost()))
                && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Login-service URL must use local HTTP or HTTPS");
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        this.client = RestClient.builder().baseUrl(loginUrl).requestFactory(factory).build();
        this.validationSecret = validationSecret;
    }

    public Optional<SessionPrincipal> validate(String token) {
        try {
            SessionPrincipal principal = client.post().uri("/internal/v1/sessions/introspect")
                    .header("X-Service-Validation-Key", validationSecret)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new IntrospectionRequest(token))
                    .retrieve().body(SessionPrincipal.class);
            if (principal == null || principal.accountId() == null || !principal.active()
                    || principal.expiresAt() == null || !principal.expiresAt().isAfter(Instant.now())
                    || !("ADMIN".equals(principal.role()) || "CUSTOMER".equals(principal.role())
                        || "DISPATCHER".equals(principal.role()))
                    || (!"ADMIN".equals(principal.role()) && principal.profileId() == null)) {
                throw new UnavailableException();
            }
            return Optional.of(principal);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 401) return Optional.empty();
            throw new UnavailableException();
        } catch (RuntimeException exception) {
            throw new UnavailableException();
        }
    }
}

