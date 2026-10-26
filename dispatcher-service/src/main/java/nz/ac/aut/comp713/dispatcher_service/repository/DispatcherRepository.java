package nz.ac.aut.comp713.dispatcher_service.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import nz.ac.aut.comp713.dispatcher_service.model.Dispatcher;

public interface DispatcherRepository extends JpaRepository<Dispatcher, Long> {
    Optional<Dispatcher> findByProvisioningRequestId(Long provisioningRequestId);
}
