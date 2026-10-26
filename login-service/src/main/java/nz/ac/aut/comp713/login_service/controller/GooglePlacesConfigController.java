package nz.ac.aut.comp713.login_service.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes the restricted browser key used by registration address autocomplete. */
@RestController
@RequestMapping("/api/v1/google-places")
public class GooglePlacesConfigController {

    private final String browserApiKey;

    public GooglePlacesConfigController(@Value("${google.maps.browser-api-key:}") String browserApiKey) {
        this.browserApiKey = browserApiKey;
    }

    @GetMapping("/config")
    public Map<String, String> getConfig() {
        return Map.of("apiKey", browserApiKey);
    }
}
