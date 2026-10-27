package nz.ac.aut.comp713.login_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ReverseGeocodingServiceTest {
    @Test
    void acceptsOnlyCompleteNewZealandStreetAddresses() {
        var complete = new ReverseGeocodingService.AddressResult("1 Queen Street, Auckland, New Zealand",
                List.of(component("1", "street_number"), component("Queen Street", "route"),
                        component("Auckland", "locality"), component("NZ", "country")));
        assertThat(ReverseGeocodingService.completeNewZealandStreet(complete)).isTrue();
        assertThat(ReverseGeocodingService.completeNewZealandStreet(new ReverseGeocodingService.AddressResult(
                complete.formattedAddress(), complete.addressComponents().subList(1, 4)))).isFalse();
        assertThat(ReverseGeocodingService.completeNewZealandStreet(new ReverseGeocodingService.AddressResult(
                complete.formattedAddress(), List.of(component("1", "street_number"),
                        component("Queen Street", "route"), component("Auckland", "locality"),
                        component("AU", "country"))))).isFalse();
    }

    @Test
    void rejectsInvalidCoordinatesBeforeCallingGoogle() {
        var service = new ReverseGeocodingService("test-key");
        assertThatThrownBy(() -> service.reverse(Double.NaN, 174.0))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThatThrownBy(() -> service.reverse(-91.0, 174.0))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private ReverseGeocodingService.AddressComponent component(String value, String type) {
        return new ReverseGeocodingService.AddressComponent(value, List.of(type));
    }
}
