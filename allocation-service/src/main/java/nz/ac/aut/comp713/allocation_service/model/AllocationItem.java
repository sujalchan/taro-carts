package nz.ac.aut.comp713.allocation_service.model;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

// JPA entity representing one taro item within a weekly allocation
@Entity
@Table(name = "allocation_item", uniqueConstraints = {
        @UniqueConstraint(name = "unique_taro_type_per_allocation", columnNames = { "weekly_allocation_id",
                "taro_type_id" })
})
public class AllocationItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // many allocation items can belong to the same weekly allocation
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "weekly_allocation_id", nullable = false)
    private WeeklyAllocation weeklyAllocation;

    // stores only the taro type ID because taro types are owned by taro-service
    @Column(name = "taro_type_id", nullable = false)
    private Long taroTypeId;

    @Column(nullable = false)
    private Integer quantity;

    // price used for this allocation, allowing the price to vary between customers
    // and weeks
    @Column(name = "price_per_kg", nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerKg;

    public AllocationItem() {}

    public Long getId() {
        return id;
    }

    public WeeklyAllocation getWeeklyAllocation() {
        return weeklyAllocation;
    }

    public void setWeeklyAllocation(WeeklyAllocation weeklyAllocation) {
        this.weeklyAllocation = weeklyAllocation;
    }

    public Long getTaroTypeId() {
        return taroTypeId;
    }

    public void setTaroTypeId(Long taroTypeId) {
        this.taroTypeId = taroTypeId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getPricePerKg() {
        return pricePerKg;
    }

    public void setPricePerKg(BigDecimal pricePerKg) {
        this.pricePerKg = pricePerKg;
    }
}
