package nz.ac.aut.comp713.dispatcher_service.controller;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Exposes browser-safe configuration needed by the address autocomplete UI. */
@RestController
@RequestMapping("/api/v1/google-places")
@Tag(name = "Address Validation Configuration", description = "Public browser configuration for address autocomplete")
public class GooglePlacesConfigController {
    private final String browserApiKey;

    /** Creates the config endpoint with the configured browser API key, or an empty key when unset. */
    public GooglePlacesConfigController(@Value("${google.maps.browser-api-key:}") String browserApiKey) {
        this.browserApiKey = browserApiKey;
    }

    /** Returns the Google Places browser API key used by address autocomplete. */
    @Operation(summary = "Get Google Places browser configuration", description = "Returns the browser API key as {\"apiKey\": \"...\"}. An empty string means the key is not configured.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Browser configuration returned as an object containing a string apiKey", content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"apiKey\":\"...\"}")))
    })
    @GetMapping("/config")
    public Map<String, String> getConfig() {
        return Map.of("apiKey", browserApiKey);
    }
}
