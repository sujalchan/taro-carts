package nz.ac.aut.comp713.customer_service.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// JPA entity representing a customer stored in the customers table
@Entity
@Table(name = "customers")
public class Customer {

    // database generated primary key
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // customer business name shown to users
    @Column(nullable = false)
    private String name;

    // normalized name used to enforce case insensitive uniqueness
    @Column(nullable = false, unique = true)
    private String normalizedName;

    @Column
    private Long provisioningRequestId;

    private String contactName;
    private String phone;
    // Nullable so existing SQLite customer rows remain readable after the column is added.
    @Column(length = 500)
    private String address;

    // Accounts require explicit approval through login-service.
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean active = false;

    public Customer() {
    }

    // New domain profiles are always inactive until the login admin enables them.
    public Customer(String name, String address, String contactName, String phone) {
        this.name = name;
        this.address = address;
        this.contactName = contactName;
        this.phone = phone;
        this.active = false;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public void setNormalizedName(String normalizedName) {
        this.normalizedName = normalizedName;
    }

    public Long getProvisioningRequestId() { return provisioningRequestId; }
    public void setProvisioningRequestId(Long provisioningRequestId) { this.provisioningRequestId = provisioningRequestId; }

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
