package nz.ac.aut.comp713.customer_service.service;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
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

import nz.ac.aut.comp713.customer_service.security.AllocationIdentitySigner;

/** Server-to-server allocation calls using one-use assertions, never the browser cookie. */
@Component
@Profile("!legacy-tests")
public class PortalAllocationClient {
    private static final ObjectMapper JSON = new ObjectMapper();
    public record Item(Long taroTypeId, String taroTypeName, Integer quantity, BigDecimal pricePerKg) {}
    public record Allocation(Long id, Long customerId, Long requestedByCustomerId, String customerName,
                             LocalDate weekStart, String deliveryStatus, String paymentStatus,
                             boolean invoiceAvailable, List<Item> allocationItems,
                             Long dispatcherId, String deliveryAddress) {}
    public record TaroType(Long id, String name, String description, BigDecimal standardPrice) {}
    public record RequestItem(Long taroTypeId, BigDecimal quantity, BigDecimal pricePerKg) {}
    private record RequestedItem(Long taroTypeId, BigDecimal quantity, BigDecimal pricePerKg) {}
    private record NewRequest(Long customerId, LocalDate weekStart, List<RequestedItem> allocationItems,
                              String deliveryAddress) {}

    private final RestClient client;
    private final AllocationIdentitySigner signer;

    public PortalAllocationClient(AllocationIdentitySigner signer,
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

    public List<Allocation> list(HttpServletRequest request, Long customerId) {
        String path = "/api/v1/allocations";
        String uri = customerId == null ? path : path + "?customerId=" + customerId;
        return Arrays.asList(readArray(() -> client.get().uri(uri)
                .header("X-Allocation-Identity-Assertion", signer.sign(request, "GET", path))
                .retrieve().body(Allocation[].class)));
    }

    public Allocation detail(HttpServletRequest request, Long id) {
        String path = "/api/v1/allocations/" + id;
        return call(() -> client.get().uri(path)
                .header("X-Allocation-Identity-Assertion", signer.sign(request, "GET", path))
                .retrieve().body(Allocation.class));
    }

    public List<TaroType> taroTypes(HttpServletRequest request) {
        String path = "/api/v1/allocations/taro-types";
        return Arrays.asList(readArray(() -> client.get().uri(path)
                .header("X-Allocation-Identity-Assertion", signer.sign(request, "GET", path))
                .retrieve().body(TaroType[].class)));
    }

    public void create(HttpServletRequest request, Long customerId, LocalDate weekStart,
                       Long taroTypeId, BigDecimal quantity, String address) {
        create(request, customerId, weekStart,
                List.of(new RequestItem(taroTypeId, quantity, null)), address);
    }

    public void create(HttpServletRequest request, Long customerId, LocalDate weekStart,
                       List<RequestItem> items, String address) {
        String path = "/api/v1/allocations/customer-request";
        call(() -> client.post().uri(path)
                .header("X-Allocation-Identity-Assertion", signer.sign(request, "POST", path))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new NewRequest(customerId, weekStart, items.stream()
                        .map(item -> new RequestedItem(item.taroTypeId(), item.quantity(), item.pricePerKg()))
                        .toList(), address))
                .retrieve().toBodilessEntity());
    }

    public void pay(HttpServletRequest request, Long id) {
        String path = "/api/v1/allocations/" + id + "/pay";
        call(() -> client.post().uri(path)
                .header("X-Allocation-Identity-Assertion", signer.sign(request, "POST", path))
                .retrieve().toBodilessEntity());
    }

    public byte[] invoice(HttpServletRequest request, Long id, boolean inline) {
        String path = "/api/v1/allocations/" + id + "/invoice.pdf";
        return call(() -> client.get().uri(path + (inline ? "?inline=true" : ""))
                .header("X-Allocation-Identity-Assertion", signer.sign(request, "GET", path))
                .retrieve().body(byte[].class));
    }

    public void cancel(HttpServletRequest request, Long id) {
        String path = "/api/v1/allocations/" + id + "/customer-request";
        call(() -> client.delete().uri(path)
                .header("X-Allocation-Identity-Assertion", signer.sign(request, "DELETE", path))
                .retrieve().toBodilessEntity());
    }

    private <T> T[] readArray(ExternalCall<T[]> call) {
        T[] result = call(call);
        if (result == null) throw unavailable("Allocation Service is unavailable.");
        return result;
    }

    private <T> T call(ExternalCall<T> call) {
        try {
            T value = call.execute();
            if (value == null) throw unavailable("Allocation Service is unavailable.");
            return value;
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            HttpStatus mapped = switch (status) {
                case 400 -> HttpStatus.BAD_REQUEST;
                case 401 -> HttpStatus.UNAUTHORIZED;
                case 403 -> HttpStatus.FORBIDDEN;
                case 404 -> HttpStatus.NOT_FOUND;
                case 409 -> HttpStatus.CONFLICT;
                default -> HttpStatus.SERVICE_UNAVAILABLE;
            };
            if (mapped == HttpStatus.SERVICE_UNAVAILABLE) {
                throw unavailable(downstreamService(exception));
            }
            throw new ResponseStatusException(mapped);
        } catch (RestClientException exception) {
            throw unavailable("Allocation Service is unavailable.");
        }
    }

    private static ResponseStatusException unavailable(String reason) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, reason);
    }

    private static String downstreamService(RestClientResponseException response) {
        try {
            var body = JSON.readTree(response.getResponseBodyAsString());
            return switch (body.path("code").asText()) {
                case "CUSTOMER_SERVICE_UNAVAILABLE" -> "Customer Service is unavailable.";
                case "DISPATCHER_SERVICE_UNAVAILABLE" -> "Dispatcher Service is unavailable.";
                case "TARO_SERVICE_UNAVAILABLE" -> "Taro Service is unavailable.";
                default -> body.path("error").asText().equalsIgnoreCase("Authentication service unavailable")
                        ? "Login Service is unavailable." : "Allocation Service is unavailable.";
            };
        } catch (Exception ignored) {
            return "Allocation Service is unavailable.";
        }
    }

    @FunctionalInterface
    private interface ExternalCall<T> { T execute(); }
}
