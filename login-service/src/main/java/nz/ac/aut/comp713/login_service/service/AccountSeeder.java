package nz.ac.aut.comp713.login_service.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import nz.ac.aut.comp713.login_service.model.Account;
import nz.ac.aut.comp713.login_service.model.AccountRole;
import nz.ac.aut.comp713.login_service.repository.AccountRepository;

/**
 * Creates the initial development admin once and never overwrites an existing
 * account or password.
 */
@Component
public class AccountSeeder implements ApplicationRunner {

    private final AccountRepository accounts;
    private final BCryptPasswordEncoder passwordEncoder;

    public AccountSeeder(AccountRepository accounts, BCryptPasswordEncoder passwordEncoder) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
    }

    /** Seeds only when the normalized admin username is absent. */
    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (!accounts.existsByNormalizedUsername("admin")) {
            accounts.save(new Account("admin", "admin", passwordEncoder.encode("admin1234!"),
                    AccountRole.ADMIN, null, true));
        }
    }
}
