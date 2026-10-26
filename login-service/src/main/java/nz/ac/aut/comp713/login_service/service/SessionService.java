package nz.ac.aut.comp713.login_service.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import nz.ac.aut.comp713.login_service.model.Account;
import nz.ac.aut.comp713.login_service.model.AccountRole;
import nz.ac.aut.comp713.login_service.model.LoginSession;
import nz.ac.aut.comp713.login_service.repository.LoginSessionRepository;

@Service
public class SessionService {

    public record IssuedSession(String token, Instant expiresAt) {}

    private static final SecureRandom RANDOM = new SecureRandom();
    private final LoginSessionRepository sessions;
    private final Duration lifetime;

    public SessionService(LoginSessionRepository sessions,
                          @Value("${login.session.lifetime:PT8H}") Duration lifetime) {
        if (lifetime.isNegative() || lifetime.isZero()) {
            throw new IllegalArgumentException("Session lifetime must be positive");
        }
        this.sessions = sessions;
        this.lifetime = lifetime;
    }

    @Transactional
    public IssuedSession issue(Account account) {
        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        Instant now = Instant.now();
        Instant expiresAt = now.plus(lifetime);
        sessions.save(new LoginSession(account, hash(token), now, expiresAt));
        return new IssuedSession(token, expiresAt);
    }

    @Transactional(readOnly = true)
    public Optional<LoginSession> validate(String token) {
        if (!isTokenShapeValid(token)) return Optional.empty();
        Optional<LoginSession> session = sessions.findByTokenHashAndRevokedAtIsNull(hash(token));
        Instant now = Instant.now();
        if (session.isEmpty() || !session.get().getExpiresAt().isAfter(now)
                || !session.get().getAccount().isActive()
                || (session.get().getAccount().getRole() != AccountRole.ADMIN
                    && session.get().getAccount().getProfileId() == null)) {
            return Optional.empty();
        }
        return session;
    }

    @Transactional
    public void revoke(String token) {
        if (!isTokenShapeValid(token)) return;
        sessions.findByTokenHashAndRevokedAtIsNull(hash(token))
                .ifPresent(session -> session.revokeAt(Instant.now()));
    }

    @Transactional
    public void revokeAllForAccount(Long accountId) {
        sessions.revokeAllForAccount(accountId, Instant.now());
    }

    @Scheduled(fixedDelayString = "${login.session.cleanup-delay-ms:3600000}")
    @Transactional
    public void removeExpiredSessions() {
        sessions.deleteByExpiresAtBefore(Instant.now());
    }

    private boolean isTokenShapeValid(String token) {
        return token != null && token.matches("[A-Za-z0-9_-]{43}");
    }

    private String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
