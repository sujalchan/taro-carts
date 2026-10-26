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

/** An account request awaiting the future administrator approval workflow. */
@Entity
@Table(name = "registration_requests")
public class RegistrationRequest {

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
    private RegistrationRole role;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 100)
    private String contactName;

    @Column(length = 16)
    private String phone;

    @Column(nullable = false, length = 500)
    private String address;

    @Column(nullable = false)
    private Instant submittedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private RegistrationStatus status;

    private Long profileId;

    private Long accountId;

    protected RegistrationRequest() {
    }

    public RegistrationRequest(String username, String normalizedUsername, String passwordHash,
                               RegistrationRole role, String name, String contactName, String phone, String address) {
        this.username = username;
        this.normalizedUsername = normalizedUsername;
        this.passwordHash = passwordHash;
        this.role = role;
        this.name = name;
        this.contactName = contactName;
        this.phone = phone;
        this.address = address;
        this.submittedAt = Instant.now();
        this.status = RegistrationStatus.PENDING_PROVISIONING;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() { return username; }
    public String getNormalizedUsername() { return normalizedUsername; }
    public String getPasswordHash() { return passwordHash; }
    public RegistrationRole getRole() { return role; }
    public String getName() { return name; }
    public String getContactName() { return contactName; }
    public String getPhone() { return phone; }
    public String getAddress() { return address; }
    public Instant getSubmittedAt() { return submittedAt; }
    public RegistrationStatus getStatus() { return status == null ? RegistrationStatus.PENDING_PROVISIONING : status; }
    public Long getProfileId() { return profileId; }
    public Long getAccountId() { return accountId; }

    public void markProfileCreated(Long id) {
        profileId = id;
        status = RegistrationStatus.REPAIR_REQUIRED;
    }

    public void markProvisioned(Long id) {
        accountId = id;
        status = RegistrationStatus.PROVISIONED;
    }

    public void markRetryable() {
        status = profileId == null ? RegistrationStatus.PENDING_PROVISIONING : RegistrationStatus.REPAIR_REQUIRED;
    }
}
