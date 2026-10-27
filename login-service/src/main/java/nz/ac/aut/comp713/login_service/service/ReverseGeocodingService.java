package nz.ac.aut.comp713.login_service.service;

import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/** Keeps the Geocoding API credential on login-service, not in mobile requests. */
@Service
public class ReverseGeocodingService {
    public record AddressComponent(String shortText, List<String> types) {}
    public record AddressResult(String formattedAddress, List<AddressComponent> addressComponents) {}
    public record GeocodingResponse(List<AddressResult> results) {}

    private final RestClient client;
    private final String apiKey;

    public ReverseGeocodingService(@Value("${google.maps.geocoding-api-key:}") String apiKey) {
        this.apiKey = apiKey;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.client = RestClient.builder().baseUrl("https://geocode.googleapis.com")
                .requestFactory(factory).build();
    }

    public String reverse(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid coordinates.");
        }
        if (apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Address lookup is not configured.");
        }
        GeocodingResponse response;
        try {
            response = client.get().uri(builder -> builder.path("/v4/geocode/location")
                            .queryParam("location.latitude", latitude)
                            .queryParam("location.longitude", longitude)
                            .queryParam("languageCode", "en")
                            .queryParam("regionCode", "NZ").build())
                    .header("X-Goog-Api-Key", apiKey)
                    .retrieve().body(GeocodingResponse.class);
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Address lookup is unavailable. Search for your address instead.");
        }
        if (response != null && response.results() != null) {
            for (AddressResult result : response.results()) {
                if (completeNewZealandStreet(result)) return result.formattedAddress();
            }
        }
        throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                "Could not find a complete New Zealand street address here. Search for your address instead.");
    }

    static boolean completeNewZealandStreet(AddressResult result) {
        if (result == null || result.formattedAddress() == null
                || result.formattedAddress().isBlank() || result.formattedAddress().length() > 500
                || result.addressComponents() == null) return false;
        boolean number = false, route = false, locality = false, country = false;
        for (AddressComponent component : result.addressComponents()) {
            if (component == null || component.types() == null) continue;
            if (component.types().contains("street_number")) number = true;
            if (component.types().contains("route")) route = true;
            if (component.types().contains("locality") || component.types().contains("postal_town")
                    || component.types().contains("sublocality_level_1")
                    || component.types().contains("postal_code")) locality = true;
            if (component.types().contains("country") && "NZ".equalsIgnoreCase(component.shortText())) country = true;
        }
        return number && route && locality && country;
    }
}
