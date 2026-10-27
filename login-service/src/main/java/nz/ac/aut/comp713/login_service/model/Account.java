package nz.ac.aut.comp713.login_service.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Login identity linked to an optional customer or dispatcher profile. */
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(nullable = false, unique = true, length = 100)
    private String normalizedUsername;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountRole role;

    private Long profileId;

    @Column(nullable = false)
    private boolean active;

    /**
     * Target activation state retained while a domain profile update needs
     * retrying.
     */
    private Boolean desiredActive;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant lastLoginAt;

    protected Account() {
    }

    public Account(String username, String normalizedUsername, String passwordHash,
            AccountRole role, Long profileId, boolean active) {
        this.username = username;
        this.normalizedUsername = normalizedUsername;
        this.passwordHash = passwordHash;
        this.role = role;
        this.profileId = profileId;
        this.active = active;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getNormalizedUsername() {
        return normalizedUsername;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public AccountRole getRole() {
        return role;
    }

    public Long getProfileId() {
        return profileId;
    }

    public boolean isActive() {
        return active;
    }

    public Boolean getDesiredActive() {
        return desiredActive;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public void recordLogin(Instant when) {
        lastLoginAt = when;
        updatedAt = when;
    }

    public void changePassword(String newHash, Instant when) {
        passwordHash = newHash;
        updatedAt = when;
    }

    /**
     * Persists the target state; disabling takes effect locally before the remote
     * call.
     */
    public void beginActivationChange(boolean desired, Instant when) {
        desiredActive = desired;
        if (!desired)
            active = false;
        updatedAt = when;
    }

    /** Commits the remote state locally and clears the retry marker. */
    public void finishActivationChange(boolean value, Instant when) {
        active = value;
        desiredActive = null;
        updatedAt = when;
    }
}
