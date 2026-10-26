package nz.ac.aut.comp713.allocation_service.model;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Immutable snapshot of an invoice before an administrator amends its prices. */
@Entity
@Table(name = "invoice_revision", uniqueConstraints =
        @UniqueConstraint(name = "unique_invoice_revision", columnNames = {"invoice_id", "revision_number"}))
public class InvoiceRevision {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JdbcTypeCode(SqlTypes.INTEGER)
    private Long id;

    @Column(name = "invoice_id", nullable = false)
    private Long invoiceId;

    @Column(name = "revision_number", nullable = false)
    private int revisionNumber;

    @Column(name = "actor_account_id", nullable = false)
    private Long actorAccountId;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "previous_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal previousTotal;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "previous_pdf_bytes", nullable = false, columnDefinition = "BLOB")
    private byte[] previousPdfBytes;

    protected InvoiceRevision() {}

    public InvoiceRevision(Long invoiceId, int revisionNumber, Long actorAccountId,
                           String reason, BigDecimal previousTotal, byte[] previousPdfBytes) {
        this.invoiceId = invoiceId;
        this.revisionNumber = revisionNumber;
        this.actorAccountId = actorAccountId;
        this.changedAt = Instant.now();
        this.reason = reason;
        this.previousTotal = previousTotal;
        this.previousPdfBytes = previousPdfBytes.clone();
    }

    public Long getInvoiceId() { return invoiceId; }
    public int getRevisionNumber() { return revisionNumber; }
    public Long getActorAccountId() { return actorAccountId; }
    public Instant getChangedAt() { return changedAt; }
    public String getReason() { return reason; }
    public BigDecimal getPreviousTotal() { return previousTotal; }
    public byte[] getPreviousPdfBytes() { return previousPdfBytes; }
}
