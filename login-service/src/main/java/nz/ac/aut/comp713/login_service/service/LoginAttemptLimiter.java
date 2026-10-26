package nz.ac.aut.comp713.login_service.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Local backoff for repeated failures; a shared store is needed for multi-instance deployment. */
@Service
public class LoginAttemptLimiter {

    private record Attempt(int failures, Instant lastFailure, Instant blockedUntil) {}

    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();

    public boolean allowed(String address, String username) {
        Attempt attempt = attempts.get(key(address, username));
        return attempt == null || !attempt.blockedUntil().isAfter(Instant.now());
    }

    public void failed(String address, String username) {
        Instant now = Instant.now();
        attempts.compute(key(address, username), (ignored, previous) -> {
            int failures = previous == null || previous.lastFailure().isBefore(now.minus(Duration.ofMinutes(15)))
                    ? 1 : previous.failures() + 1;
            long backoffSeconds = failures < 5 ? 0 : Math.min(300, 5L << Math.min(failures - 5, 7));
            return new Attempt(failures, now, now.plusSeconds(backoffSeconds));
        });
    }

    public void succeeded(String address, String username) {
        attempts.remove(key(address, username));
    }

    @Scheduled(fixedDelay = 900_000)
    public void removeStaleAttempts() {
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(15));
        attempts.entrySet().removeIf(entry -> entry.getValue().lastFailure().isBefore(cutoff));
    }

    private String key(String address, String username) {
        String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 100) normalized = normalized.substring(0, 100);
        return (address == null ? "" : address) + ":" + normalized;
    }
}
