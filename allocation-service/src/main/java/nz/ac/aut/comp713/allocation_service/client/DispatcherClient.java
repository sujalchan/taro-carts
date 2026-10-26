package nz.ac.aut.comp713.allocation_service.client;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import nz.ac.aut.comp713.allocation_service.exception.DispatcherNotFoundException;
import nz.ac.aut.comp713.allocation_service.exception.DispatcherServiceUnavailableException;

@Component
public class DispatcherClient {
    private final RestClient restClient;

    @Value("${allocation.reference.dispatcher-secret:}")
    private String referenceSecret = "";

    @PostConstruct
    void requireReferenceSecret() {
        if (referenceSecret.length() < 32) throw new IllegalStateException("ALLOCATION_DISPATCHER_REFERENCE_SECRET must contain at least 32 characters");
    }

    public DispatcherClient(RestClient.Builder builder, @Value("${dispatcher.service.url}") String baseUrl) {
        URI uri = URI.create(baseUrl);
        if (!("http".equalsIgnoreCase(uri.getScheme()) && "localhost".equalsIgnoreCase(uri.getHost()))
                && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Dispatcher service URL must use local HTTP or HTTPS");
        }
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    public List<DispatcherResponse> getDispatchers() {
        try {
            DispatcherResponse[] dispatchers = restClient.get()
                    .uri("/internal/v1/allocation-reference/dispatchers")
                    .headers(headers -> headers.set("X-Allocation-Reference-Key", referenceSecret))
                    .retrieve()
                    .body(DispatcherResponse[].class);
            return dispatchers == null ? List.of() : Arrays.asList(dispatchers);
        } catch (RestClientException exception) {
            throw new DispatcherServiceUnavailableException();
        }
    }

    public DispatcherResponse getDispatcher(Long dispatcherId) {
        try {
            DispatcherResponse dispatcher = restClient.get()
                    .uri("/internal/v1/allocation-reference/dispatchers/{id}", dispatcherId)
                    .headers(headers -> headers.set("X-Allocation-Reference-Key", referenceSecret))
                    .retrieve()
                    .body(DispatcherResponse.class);
            if (dispatcher == null) throw new DispatcherServiceUnavailableException();
            return dispatcher;
        } catch (HttpClientErrorException.NotFound exception) {
            throw new DispatcherNotFoundException(dispatcherId);
        } catch (RestClientException exception) {
            throw new DispatcherServiceUnavailableException();
        }
    }
}
