package nz.ac.aut.comp713.auth_service;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, String> {
    Optional<Account> findByUsernameIgnoreCase(String username);
    boolean existsByUsernameIgnoreCase(String username);
}
