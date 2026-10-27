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
    @Column(columnDefinition = "integer")
    private Long id;

    // stores only the customer ID because customers are owned by customer-service
    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    // Set only for requests made through the customer-facing API.
    @Column(name = "requested_by_customer_id")
    private Long requestedByCustomerId;

    // stores the selected dispatcher ID because dispatchers are owned by dispatcher-service
    @Column(name = "dispatcher_id")
    private Long dispatcherId;

    // Destination for this delivery. Older allocations can use the customer's current address.
    @Column(name = "delivery_address", length = 500)
    private String deliveryAddress;

    @Column(name = "dispatch_start_address", length = 500)
    private String dispatchStartAddress;

    // normalized to the Monday of the allocation week by the service layer
    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    // store the enum name and default new allocations to requested
    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, columnDefinition = "varchar(255) default 'REQUESTED_ALLOCATION'")
    private DeliveryStatus deliveryStatus = DeliveryStatus.REQUESTED_ALLOCATION;

    // store the enum name and default new allocations to unpaid
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, columnDefinition = "varchar(255) default 'UNPAID'")
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

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

    public Long getRequestedByCustomerId() {
        return requestedByCustomerId;
    }

    public void setRequestedByCustomerId(Long requestedByCustomerId) {
        this.requestedByCustomerId = requestedByCustomerId;
    }

    public Long getDispatcherId() {
        return dispatcherId;
    }

    public void setDispatcherId(Long dispatcherId) {
        this.dispatcherId = dispatcherId;
    }

    public String getDeliveryAddress() {
        return deliveryAddress;
    }

    public void setDeliveryAddress(String deliveryAddress) {
        this.deliveryAddress = deliveryAddress;
    }

    public String getDispatchStartAddress() { return dispatchStartAddress; }

    public void setDispatchStartAddress(String dispatchStartAddress) {
        this.dispatchStartAddress = dispatchStartAddress;
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

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(PaymentStatus paymentStatus) {
        this.paymentStatus = paymentStatus;
    }
}
