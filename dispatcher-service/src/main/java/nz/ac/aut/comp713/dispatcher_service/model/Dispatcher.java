package nz.ac.aut.comp713.dispatcher_service.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "dispatchers")
public class Dispatcher {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String normalizedName;

    @Column
    private Long provisioningRequestId;

    private String contactName;
    private String phone;

    @Column(nullable = false, length = 500)
    private String address;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean active = false;

    public Dispatcher() {}
    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getNormalizedName() { return normalizedName; }
    public void setNormalizedName(String normalizedName) { this.normalizedName = normalizedName; }
    public Long getProvisioningRequestId() { return provisioningRequestId; }
    public void setProvisioningRequestId(Long provisioningRequestId) { this.provisioningRequestId = provisioningRequestId; }
    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
