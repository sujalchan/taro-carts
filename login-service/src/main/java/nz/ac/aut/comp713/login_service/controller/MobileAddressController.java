package nz.ac.aut.comp713.login_service.controller;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import nz.ac.aut.comp713.login_service.service.ReverseGeocodingService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** One-time address lookup for registration, without exposing the server API key. */
@RestController
@RequestMapping("/api/v1/mobile/address")
public class MobileAddressController {
    public record ReverseRequest(@NotNull Double latitude, @NotNull Double longitude) {}
    public record ReverseResponse(String address) {}
    private record Window(long minute, int count) {}

    private final ReverseGeocodingService geocoding;
    private final ConcurrentHashMap<String, Window> requests = new ConcurrentHashMap<>();

    public MobileAddressController(ReverseGeocodingService geocoding) {
        this.geocoding = geocoding;
    }

    @PostMapping("/reverse")
    public ResponseEntity<ReverseResponse> reverse(@Valid @RequestBody ReverseRequest body,
                                                    HttpServletRequest request) {
        long minute = Instant.now().getEpochSecond() / 60;
        AtomicBoolean allowed = new AtomicBoolean();
        requests.compute(request.getRemoteAddr(), (ignored, previous) -> {
            int count = previous == null || previous.minute() != minute ? 1 : previous.count() + 1;
            allowed.set(count <= 10);
            return new Window(minute, count);
        });
        if (requests.size() > 1_000) {
            requests.entrySet().removeIf(entry -> entry.getValue().minute() < minute - 1);
        }
        if (!allowed.get()) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "Too many address lookups. Please wait and try again.");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ReverseResponse(geocoding.reverse(body.latitude(), body.longitude())));
    }
}
