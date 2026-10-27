package nz.ac.aut.comp713.login_service.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import nz.ac.aut.comp713.login_service.model.Account;

/**
 * Database access for accounts, including normalized username lookup and
 * uniqueness checks.
 */
public interface AccountRepository extends JpaRepository<Account, Long> {
    /** Looks up accounts using the lowercase, trimmed username key. */
    Optional<Account> findByNormalizedUsername(String normalizedUsername);

    /**
     * Checks the normalized key used to enforce case-insensitive username
     * uniqueness.
     */
    boolean existsByNormalizedUsername(String normalizedUsername);
}
