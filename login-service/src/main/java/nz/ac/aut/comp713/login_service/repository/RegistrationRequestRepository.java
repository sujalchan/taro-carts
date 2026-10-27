package nz.ac.aut.comp713.login_service.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import nz.ac.aut.comp713.login_service.model.RegistrationRequest;

/**
 * Database access for pending registration requests and their normalized
 * usernames.
 */
public interface RegistrationRequestRepository extends JpaRepository<RegistrationRequest, Long> {
    /**
     * Detects account-name collisions across still-pending registration requests.
     */
    boolean existsByNormalizedUsername(String normalizedUsername);

    /** Returns requests newest first for the administrator's pending-work list. */
    List<RegistrationRequest> findAllByOrderBySubmittedAtDesc();
}
