package nz.ac.aut.comp713.login_service.service;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import nz.ac.aut.comp713.login_service.model.Account;
import nz.ac.aut.comp713.login_service.model.AccountRole;
import nz.ac.aut.comp713.login_service.repository.AccountRepository;

@Service
public class AuthenticationService {

    private final AccountRepository accounts;
    private final BCryptPasswordEncoder passwordEncoder;
    // Unknown usernames still perform a BCrypt comparison.
    private final String dummyHash;

    public AuthenticationService(AccountRepository accounts, BCryptPasswordEncoder passwordEncoder) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.dummyHash = passwordEncoder.encode("not-a-real-account-password");
    }

    @Transactional
    public Optional<Account> authenticate(String username, String password) {
        String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        Optional<Account> account = normalized.length() > 100 ? Optional.empty()
                : accounts.findByNormalizedUsername(normalized);
        boolean passwordMatches = passwordEncoder.matches(password == null ? "" : password,
                account.map(Account::getPasswordHash).orElse(dummyHash));
        if (account.isEmpty() || !passwordMatches || !account.get().isActive()
                || (account.get().getRole() != AccountRole.ADMIN && account.get().getProfileId() == null)) {
            return Optional.empty();
        }
        account.get().recordLogin(Instant.now());
        return account;
    }
}
