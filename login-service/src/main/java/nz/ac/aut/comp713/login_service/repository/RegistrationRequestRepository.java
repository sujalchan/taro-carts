package nz.ac.aut.comp713.login_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import nz.ac.aut.comp713.login_service.model.RegistrationRequest;

public interface RegistrationRequestRepository extends JpaRepository<RegistrationRequest, Long> {
    boolean existsByNormalizedUsername(String normalizedUsername);
}
