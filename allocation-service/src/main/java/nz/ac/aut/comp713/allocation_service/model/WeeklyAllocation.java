package nz.ac.aut.comp713.allocation_service.model;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

// JPA entity representing one customer's allocation for a specific week
@Entity
@Table(name = "weekly_allocation", uniqueConstraints = {
        @UniqueConstraint(name = "unique_weekly_allocation", columnNames = { "customer_id", "week_start" })
})
public class WeeklyAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // stores only the customer ID because customers are owned by customer-service
    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    // normalized to the Monday of the allocation week by the service layer
    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    // store the enum name and default new or migrated allocations to pending
    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, columnDefinition = "varchar(255) default 'PENDING'")
    private DeliveryStatus deliveryStatus = DeliveryStatus.PENDING;

    public WeeklyAllocation() {
    }

    public Long getId() {
        return id;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public LocalDate getWeekStart() {
        return weekStart;
    }

    public void setWeekStart(LocalDate weekStart) {
        this.weekStart = weekStart;
    }

    public DeliveryStatus getDeliveryStatus() {
        return deliveryStatus;
    }

    public void setDeliveryStatus(DeliveryStatus deliveryStatus) {
        this.deliveryStatus = deliveryStatus;
    }
}
