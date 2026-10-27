package nz.ac.aut.comp713.allocation_service.model;

import java.time.Instant;
import jakarta.persistence.*;

@Entity
@Table(name = "delivery_location", uniqueConstraints = @UniqueConstraint(
        name = "unique_allocation_location_kind", columnNames = {"allocation_id", "kind"}))
public class DeliveryLocation {
    // Earlier point names remain readable for existing database rows; new updates use DRIVER.
    public enum Kind { START, INTERMEDIATE, LATEST, DRIVER }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "integer")
    private Long id;
    @Column(name = "allocation_id", nullable = false)
    private Long allocationId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Kind kind;
    @Column(nullable = false)
    private double latitude;
    @Column(nullable = false)
    private double longitude;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DeliveryLocation() {}
    public DeliveryLocation(Long allocationId, Kind kind) {
        this.allocationId = allocationId;
        this.kind = kind;
    }
    public Long getAllocationId() { return allocationId; }
    public Kind getKind() { return kind; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void update(double latitude, double longitude, Instant updatedAt) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.updatedAt = updatedAt;
    }
}
