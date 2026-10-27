package nz.ac.aut.comp713.login_service.service;

import java.net.URI;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import nz.ac.aut.comp713.login_service.model.Account;
import nz.ac.aut.comp713.login_service.model.AccountRole;
import nz.ac.aut.comp713.login_service.model.RegistrationRequest;
import nz.ac.aut.comp713.login_service.model.RegistrationRole;

/**
 * Calls customer and dispatcher private write endpoints using separate
 * credentials and bounded timeouts.
 */
@Service
public class DomainProvisioningClient {

    /**
     * Profile fields sent to the relevant domain service when a registration is
     * provisioned.
     */
    private record ProfileRequest(String name, String contactName, String phone, String address) {
    }

    /**
     * Desired active flag sent to a domain service during an administrator state
     * change.
     */
    private record ActivationRequest(boolean active) {
    }

    /**
     * Minimal profile state required to verify a domain-service write succeeded.
     */
    public record ProfileResponse(Long id, boolean active) {
    }

    private final RestClient customer;
    private final RestClient dispatcher;
    private final String customerSecret;
    private final String dispatcherSecret;

    public DomainProvisioningClient(
            @Value("${login.customer-service-url:http://localhost:8081}") String customerUrl,
            @Value("${login.dispatcher-service-url:http://localhost:8084}") String dispatcherUrl,
            @Value("${login.customer-provisioning-secret:}") String customerSecret,
            @Value("${login.dispatcher-provisioning-secret:}") String dispatcherSecret,
            @Value("${login.provisioning.connect-timeout:PT2S}") Duration connectTimeout,
            @Value("${login.provisioning.read-timeout:PT3S}") Duration readTimeout) {
        if (customerSecret.length() < 32 || dispatcherSecret.length() < 32) {
            throw new IllegalStateException("Both login provisioning secrets must contain at least 32 characters");
        }
        if (customerSecret.equals(dispatcherSecret)) {
            throw new IllegalStateException("Customer and dispatcher provisioning secrets must be different");
        }
        validateUrl(customerUrl);
        validateUrl(dispatcherUrl);
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        this.customer = RestClient.builder().baseUrl(customerUrl).requestFactory(factory).build();
        this.dispatcher = RestClient.builder().baseUrl(dispatcherUrl).requestFactory(factory).build();
        this.customerSecret = customerSecret;
        this.dispatcherSecret = dispatcherSecret;
    }

    /**
     * Creates the requested domain profile and rejects responses without a positive
     * profile ID.
     */
    public ProfileResponse create(RegistrationRequest request) {
        RestClient client = request.getRole() == RegistrationRole.CUSTOMER ? customer : dispatcher;
        String path = request.getRole() == RegistrationRole.CUSTOMER
                ? "/internal/v1/customers"
                : "/internal/v1/dispatchers";
        ProfileResponse response = client.post().uri(path)
                .header("X-Login-Provisioning-Key", secret(request.getRole()))
                .header("X-Registration-Request-Id", request.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ProfileRequest(request.getName(), request.getContactName(),
                        request.getPhone(), request.getAddress()))
                .retrieve().body(ProfileResponse.class);
        if (response == null || response.id() == null || response.id() <= 0) {
            throw new IllegalStateException("Domain service did not return a profile ID");
        }
        return response;
    }

    /**
     * Changes the linked domain profile state and rejects a response that does not
     * confirm the requested state.
     */
    public void setActive(Account account, boolean active) {
        if (account.getProfileId() == null || account.getRole() == AccountRole.ADMIN) {
            throw new IllegalArgumentException("Account has no domain profile");
        }
        setActive(account.getRole(), account.getProfileId(), active);
    }

    public void ensureInactive(RegistrationRole role, Long profileId) {
        setActive(AccountRole.valueOf(role.name()), profileId, false);
    }

    private void setActive(AccountRole role, Long profileId, boolean active) {
        boolean customerRole = role == AccountRole.CUSTOMER;
        RestClient client = customerRole ? customer : dispatcher;
        String path = (customerRole ? "/internal/v1/customers/" : "/internal/v1/dispatchers/")
                + profileId + "/active";
        ProfileResponse response = client.put().uri(path)
                .header("X-Login-Provisioning-Key", customerRole ? customerSecret : dispatcherSecret)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ActivationRequest(active))
                .retrieve().body(ProfileResponse.class);
        if (response == null || !profileId.equals(response.id()) || response.active() != active) {
            throw new IllegalStateException("Domain service did not confirm activation state");
        }
    }

    private String secret(RegistrationRole role) {
        return role == RegistrationRole.CUSTOMER ? customerSecret : dispatcherSecret;
    }

    private void validateUrl(String value) {
        URI uri = URI.create(value);
        boolean localHttp = "http".equalsIgnoreCase(uri.getScheme())
                && "localhost".equalsIgnoreCase(uri.getHost());
        if (!localHttp && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Provisioning URL must use local HTTP or HTTPS");
        }
    }
}
