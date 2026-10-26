package nz.ac.aut.comp713.dispatcher_service.service;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import nz.ac.aut.comp713.dispatcher_service.exception.AddressNotValidException;
import nz.ac.aut.comp713.dispatcher_service.exception.AddressValidationUnavailableException;

@Service
public class GoogleAddressValidationService {
    private final RestClient restClient = RestClient.create();
    private final String serverApiKey;

    public GoogleAddressValidationService(@Value("${google.maps.server-api-key:}") String serverApiKey) {
        this.serverApiKey = serverApiKey;
    }

    public String validate(String submittedAddress) {
        if (serverApiKey == null || serverApiKey.isBlank()) {
            throw new AddressValidationUnavailableException();
        }

        JsonNode response;
        try {
            response = restClient.post()
                    .uri(uri -> uri.scheme("https").host("addressvalidation.googleapis.com")
                            .path("/v1:validateAddress").queryParam("key", serverApiKey).build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("address", Map.of("regionCode", "NZ", "addressLines", List.of(submittedAddress))))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException exception) {
            throw new AddressValidationUnavailableException();
        }

        JsonNode result = response == null ? null : response.path("result");
        JsonNode verdict = result == null ? null : result.path("verdict");
        if (verdict == null || verdict.isMissingNode()) {
            throw new AddressValidationUnavailableException();
        }

        String granularity = verdict.path("validationGranularity").asText();
        boolean completePremise = verdict.path("addressComplete").asBoolean(false)
                && ("PREMISE".equals(granularity) || "SUB_PREMISE".equals(granularity))
                && !verdict.path("hasUnconfirmedComponents").asBoolean(false)
                && !verdict.path("hasInferredComponents").asBoolean(false)
                && !verdict.path("hasReplacedComponents").asBoolean(false)
                && !verdict.path("hasSpellCorrectedComponents").asBoolean(false);

        String formattedAddress = result.path("address").path("formattedAddress").asText("").trim();
        if (!completePremise || formattedAddress.isBlank() || formattedAddress.length() > 500) {
            throw new AddressNotValidException();
        }
        return formattedAddress;
    }
}
