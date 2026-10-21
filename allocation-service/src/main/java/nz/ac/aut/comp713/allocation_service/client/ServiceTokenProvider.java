package nz.ac.aut.comp713.allocation_service.client;

import java.time.Instant;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ServiceTokenProvider {
    private final RestClient tokens;
    private final String secret;
    private String accessToken;
    private Instant expiresAt = Instant.EPOCH;

    public ServiceTokenProvider(@Value("${auth.token-uri}") String tokenUri,
            @Value("${auth.service-client-secret}") String secret) {
        this.tokens = RestClient.create(tokenUri);
        this.secret = secret;
    }

    public synchronized String accessToken() {
        if (accessToken != null && Instant.now().isBefore(expiresAt)) return accessToken;
        if (secret.isBlank()) throw new IllegalStateException("Configure auth.service-client-secret");
        @SuppressWarnings("unchecked")
        Map<String, Object> response = tokens.post()
                .headers(headers -> headers.setBasicAuth("allocation-service", secret))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("grant_type=client_credentials&scope=references.read")
                .retrieve().body(Map.class);
        if (response == null || !(response.get("access_token") instanceof String token))
            throw new IllegalStateException("Authentication service did not return an access token");
        accessToken = token;
        Number seconds = (Number) response.getOrDefault("expires_in", 300);
        expiresAt = Instant.now().plusSeconds(Math.max(1, seconds.longValue() - 30));
        return accessToken;
    }
}
