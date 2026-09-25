package nz.ac.aut.comp713.taro_service.model;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// JPA entity representing a taro type stored in the taro_type table
@Entity
@Table(name = "taro_type")
public class TaroType {

    // database generated primary key
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // taro type name shown to users
    @Column(nullable = false)
    private String name;

    // normalized name used to enforce case insensitive uniqueness
    @Column(name = "normalized_name", nullable = false, unique = true)
    private String normalizedName;

    private String description;

    // default price used when an allocation does not provide a custom price
    @Column(name = "standard_price", nullable = false)
    private BigDecimal standardPrice;

    public TaroType() {
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public void setNormalizedName(String normalizedName) {
        this.normalizedName = normalizedName;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getStandardPrice() {
        return standardPrice;
    }

    public void setStandardPrice(BigDecimal standardPrice) {
        this.standardPrice = standardPrice;
    }
}
