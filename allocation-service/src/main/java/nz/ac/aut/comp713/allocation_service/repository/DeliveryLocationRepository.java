package nz.ac.aut.comp713.allocation_service.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import nz.ac.aut.comp713.allocation_service.model.DeliveryLocation;

public interface DeliveryLocationRepository extends JpaRepository<DeliveryLocation, Long> {
    List<DeliveryLocation> findByAllocationId(Long allocationId);
    Optional<DeliveryLocation> findByAllocationIdAndKind(Long allocationId, DeliveryLocation.Kind kind);
    void deleteByAllocationId(Long allocationId);
}
