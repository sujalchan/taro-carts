package nz.ac.aut.comp713.login_service.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import nz.ac.aut.comp713.login_service.model.Account;

public interface AccountRepository extends JpaRepository<Account, Long> {
    Optional<Account> findByNormalizedUsername(String normalizedUsername);
    boolean existsByNormalizedUsername(String normalizedUsername);
}
