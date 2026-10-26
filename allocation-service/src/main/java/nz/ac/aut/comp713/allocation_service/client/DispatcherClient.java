package nz.ac.aut.comp713.allocation_service.client;

import java.util.Arrays;
import java.util.List;

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

    public DispatcherClient(RestClient.Builder builder, @Value("${dispatcher.service.url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    public List<DispatcherResponse> getDispatchers() {
        try {
            DispatcherResponse[] dispatchers = restClient.get()
                    .uri("/api/v1/dispatchers")
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
                    .uri("/api/v1/dispatchers/{id}", dispatcherId)
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
