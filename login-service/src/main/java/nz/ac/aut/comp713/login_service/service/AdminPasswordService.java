package nz.ac.aut.comp713.login_service.service;

import java.time.Instant;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import nz.ac.aut.comp713.login_service.model.AccountRole;
import nz.ac.aut.comp713.login_service.repository.AccountRepository;

/**
 * Applies password changes only to active admin accounts and invalidates their
 * existing sessions.
 */
@Service
public class AdminPasswordService {

    private final AccountRepository accounts;
    private final BCryptPasswordEncoder encoder;
    private final SessionService sessions;

    public AdminPasswordService(AccountRepository accounts, BCryptPasswordEncoder encoder,
            SessionService sessions) {
        this.accounts = accounts;
        this.encoder = encoder;
        this.sessions = sessions;
    }

    /**
     * Returns false for invalid credentials, policy failures, or mismatched
     * confirmation.
     */
    @Transactional
    public boolean change(Long accountId, String currentPassword, String newPassword, String confirmation) {
        var account = accounts.findById(accountId).orElseThrow();
        if (account.getRole() != AccountRole.ADMIN || !account.isActive()
                || currentPassword == null || newPassword == null || confirmation == null
                || newPassword.length() < 12 || newPassword.length() > 72
                || !newPassword.equals(confirmation)
                || !encoder.matches(currentPassword, account.getPasswordHash())) {
            return false;
        }

        account.changePassword(encoder.encode(newPassword), Instant.now());
        sessions.revokeAllForAccount(accountId);
        return true;
    }
}
