package nz.ac.aut.comp713.dispatcher_service.service;

import java.net.URI;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import nz.ac.aut.comp713.dispatcher_service.security.AllocationIdentitySigner;

/** Loads assigned deliveries without forwarding the browser session to allocation-service. */
@Component
@Profile("!legacy-tests")
public class PortalDeliveryClient {
    public record Item(Long id, Long taroTypeId, String taroTypeName, Integer quantity, BigDecimal pricePerKg) {}
    public record Delivery(Long id, Long customerId, String customerName, LocalDate weekStart,
                           String deliveryStatus, String paymentStatus, Long dispatcherId,
                           String deliveryAddress, boolean invoiceAvailable,
                           List<Item> allocationItems) {}
    private record StatusUpdate(String deliveryStatus) {}

    private final RestClient client;
    private final AllocationIdentitySigner signer;

    public PortalDeliveryClient(AllocationIdentitySigner signer,
            @Value("${allocation.service.url:http://localhost:8082}") String baseUrl,
            @Value("${service.auth.connect-timeout:PT2S}") Duration connectTimeout,
            @Value("${service.auth.read-timeout:PT3S}") Duration readTimeout) {
        URI uri = URI.create(baseUrl);
        if (!("http".equalsIgnoreCase(uri.getScheme()) && "localhost".equalsIgnoreCase(uri.getHost()))
                && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Allocation URL must use local HTTP or HTTPS");
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.signer = signer;
    }

    public List<Delivery> list(HttpServletRequest request, Long selectedDispatcherId) {
        String path = selectedDispatcherId == null ? "/api/v1/allocations/dispatcher/me"
                : "/api/v1/allocations/dispatcher/" + selectedDispatcherId;
        Delivery[] deliveries = call(() -> client.get().uri(path)
                .header("X-Allocation-Identity-Assertion", signer.sign(request, "GET", path))
                .retrieve().body(Delivery[].class));
        return Arrays.asList(deliveries);
    }

    public void advance(HttpServletRequest request, Long id, String nextStatus) {
        String path = "/api/v1/allocations/" + id + "/delivery-status";
        call(() -> client.put().uri(path)
                .header("X-Allocation-Identity-Assertion", signer.sign(request, "PUT", path))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new StatusUpdate(nextStatus)).retrieve().toBodilessEntity());
    }

    private <T> T call(ExternalCall<T> call) {
        try {
            T value = call.execute();
            if (value == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
            return value;
        } catch (RestClientResponseException exception) {
            HttpStatus mapped = switch (exception.getStatusCode().value()) {
                case 400 -> HttpStatus.BAD_REQUEST;
                case 401 -> HttpStatus.UNAUTHORIZED;
                case 403 -> HttpStatus.FORBIDDEN;
                case 404 -> HttpStatus.NOT_FOUND;
                case 409 -> HttpStatus.CONFLICT;
                default -> HttpStatus.SERVICE_UNAVAILABLE;
            };
            throw new ResponseStatusException(mapped);
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    @FunctionalInterface
    private interface ExternalCall<T> { T execute(); }
}
