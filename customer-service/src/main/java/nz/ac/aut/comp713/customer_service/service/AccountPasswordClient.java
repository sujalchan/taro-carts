package nz.ac.aut.comp713.customer_service.service;

import java.net.URI;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** Forwards a CSRF-protected portal password change to login-service. */
@Component
@Profile("!legacy-tests")
public class AccountPasswordClient {
    private record PasswordChange(String sessionToken, String currentPassword, String newPassword,
                                  String confirmPassword) {}

    private final RestClient client;
    private final String loginUrl;

    public AccountPasswordClient(@Value("${service.auth.login-url:http://localhost:8085}") String baseUrl,
                                 @Value("${service.auth.connect-timeout:PT2S}") Duration connectTimeout,
                                 @Value("${service.auth.read-timeout:PT3S}") Duration readTimeout) {
        URI uri = URI.create(baseUrl);
        if (!("http".equalsIgnoreCase(uri.getScheme()) && "localhost".equalsIgnoreCase(uri.getHost()))
                && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Login-service URL must use local HTTP or HTTPS");
        }
        this.loginUrl = baseUrl.replaceAll("/+$", "");
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    public String loginUrl() {
        return loginUrl;
    }

    public void change(String sessionToken, String currentPassword, String newPassword, String confirmation) {
        try {
            client.post().uri("/api/v1/account/password").contentType(MediaType.APPLICATION_JSON)
                    .body(new PasswordChange(sessionToken, currentPassword, newPassword, confirmation))
                    .retrieve().toBodilessEntity();
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            if (status == 400) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Password was not changed. Check your current password, use 12 to 72 characters, and make sure the new entries match.");
            }
            if (status == 401) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Your session has expired. Sign in again before changing your password.");
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Password settings are temporarily unavailable. Please try again later.");
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Password settings are temporarily unavailable. Please try again later.");
        }
    }
}
