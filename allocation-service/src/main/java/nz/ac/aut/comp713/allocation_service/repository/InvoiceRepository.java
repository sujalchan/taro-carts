package nz.ac.aut.comp713.allocation_service.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import nz.ac.aut.comp713.allocation_service.model.Invoice;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    Optional<Invoice> findByAllocationId(Long allocationId);
    boolean existsByAllocationId(Long allocationId);
}
