package nz.ac.aut.comp713.login_service.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Applies per-address and normalized-username backoff in memory; multiple
 * instances need a shared limiter.
 */
@Service
public class LoginAttemptLimiter {

    /** State for one normalized remote-address/username pair. */
    private record Attempt(int failures, Instant lastFailure, Instant blockedUntil) {
    }

    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();

    /**
     * Checks whether this address/username pair is outside its current backoff
     * window.
     */
    public boolean allowed(String address, String username) {
        Attempt attempt = attempts.get(key(address, username));
        return attempt == null || !attempt.blockedUntil().isAfter(Instant.now());
    }

    /**
     * Records a failure and applies increasing delay after the fifth recent
     * failure.
     */
    public void failed(String address, String username) {
        Instant now = Instant.now();
        attempts.compute(key(address, username), (ignored, previous) -> {
            int failures = previous == null || previous.lastFailure().isBefore(now.minus(Duration.ofMinutes(15)))
                    ? 1
                    : previous.failures() + 1;
            long backoffSeconds = failures < 5 ? 0 : Math.min(300, 5L << Math.min(failures - 5, 7));
            return new Attempt(failures, now, now.plusSeconds(backoffSeconds));
        });
    }

    /** Clears the matching failure history after a successful login. */
    public void succeeded(String address, String username) {
        attempts.remove(key(address, username));
    }

    /** Drops attempt state older than the 15-minute failure window. */
    @Scheduled(fixedDelay = 900_000)
    public void removeStaleAttempts() {
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(15));
        attempts.entrySet().removeIf(entry -> entry.getValue().lastFailure().isBefore(cutoff));
    }

    private String key(String address, String username) {
        String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 100)
            normalized = normalized.substring(0, 100);
        return (address == null ? "" : address) + ":" + normalized;
    }
}
