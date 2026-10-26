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
    }

    public Long getId() {
        return id;
    }

    public String getUsername() { return username; }
    public RegistrationRole getRole() { return role; }
    public String getName() { return name; }
    public Instant getSubmittedAt() { return submittedAt; }
}
