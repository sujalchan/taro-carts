package nz.ac.aut.comp713.login_service.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Persisted session metadata; the raw token held by the client is never stored
 * here.
 */
@Entity
@Table(name = "login_sessions")
public class LoginSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private Instant lastSeenAt;

    private Instant revokedAt;

    protected LoginSession() {
    }

    public LoginSession(Account account, String tokenHash, Instant createdAt, Instant expiresAt) {
        this.account = account;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.lastSeenAt = createdAt;
    }

    public Account getAccount() {
        return account;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void seenAt(Instant when) {
        lastSeenAt = when;
    }

    /** Marks the session unusable from the supplied instant onward. */
    public void revokeAt(Instant when) {
        revokedAt = when;
    }
}
