package nz.ac.aut.comp713.allocation_service.client;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import nz.ac.aut.comp713.allocation_service.exception.TaroTypeNotFoundException;
import nz.ac.aut.comp713.allocation_service.exception.TaroServiceUnavailableException;

// HTTP client used by allocation-service to communicate with taro-service
@Component
public class TaroClient {

    private final RestClient restClient;

    // build a RestClient using the configured taro-service base URL
    public TaroClient(
            RestClient.Builder builder,
            @Value("${taro.service.url}") String baseUrl) {

        this.restClient = builder.baseUrl(baseUrl).build();
    }

    // retrieve all taro types from taro-service
    public List<TaroTypeResponse> getTaroTypes() {
        try {
            // call the taro-service taro type endpoint and deserialize the JSON
            // response
            TaroTypeResponse[] taroTypes = restClient.get()
                    .uri("/api/v1/taro-types")
                    .retrieve()
                    .body(TaroTypeResponse[].class);

            if (taroTypes == null) {
                return List.of();
            }

            return Arrays.asList(taroTypes);

        } catch (RestClientException e) {
            throw new TaroServiceUnavailableException();
        }
    }

    // retrieve a taro type from taro-service by ID
    public TaroTypeResponse getTaroType(Long taroTypeId) {
        try {
            TaroTypeResponse taroType = restClient.get()
                    .uri("/api/v1/taro-types/{id}", taroTypeId)
                    .retrieve()
                    .body(TaroTypeResponse.class);
            if (taroType == null) {
                throw new TaroServiceUnavailableException();
            }
            return taroType;

        } catch (HttpClientErrorException.NotFound e) {
            // translate a remote 404 into the allocation-service taro type exception
            throw new TaroTypeNotFoundException(taroTypeId);

        } catch (RestClientException e) {
            throw new TaroServiceUnavailableException();
        }
    }
}
