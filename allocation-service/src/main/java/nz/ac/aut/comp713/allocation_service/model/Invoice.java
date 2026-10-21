package nz.ac.aut.comp713.allocation_service.model;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// The saved PDF is the immutable invoice snapshot for an allocation.
@Entity
@Table(name = "invoice", uniqueConstraints = {
        @UniqueConstraint(name = "unique_invoice_allocation", columnNames = "allocation_id"),
        @UniqueConstraint(name = "unique_invoice_number", columnNames = "invoice_number")
})
public class Invoice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "allocation_id", nullable = false)
    private Long allocationId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "invoice_number", nullable = false)
    private String invoiceNumber;

    @Column(name = "issued_on", nullable = false)
    private LocalDate issuedOn;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "pdf_bytes", nullable = false, columnDefinition = "BLOB")
    private byte[] pdfBytes;

    public Long getId() { return id; }
    public Long getAllocationId() { return allocationId; }
    public void setAllocationId(Long allocationId) { this.allocationId = allocationId; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }
    public LocalDate getIssuedOn() { return issuedOn; }
    public void setIssuedOn(LocalDate issuedOn) { this.issuedOn = issuedOn; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public byte[] getPdfBytes() { return pdfBytes; }
    public void setPdfBytes(byte[] pdfBytes) { this.pdfBytes = pdfBytes; }
}
