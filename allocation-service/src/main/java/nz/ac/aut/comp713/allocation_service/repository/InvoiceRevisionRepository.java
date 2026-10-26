package nz.ac.aut.comp713.allocation_service.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import nz.ac.aut.comp713.allocation_service.model.InvoiceRevision;

public interface InvoiceRevisionRepository extends JpaRepository<InvoiceRevision, Long> {
    List<InvoiceRevision> findByInvoiceIdOrderByRevisionNumberDesc(Long invoiceId);
    Optional<InvoiceRevision> findByInvoiceIdAndRevisionNumber(Long invoiceId, int revisionNumber);
    long countByInvoiceId(Long invoiceId);
}
