package nz.ac.aut.comp713.login_service.service;

import java.time.Instant;

import org.springframework.stereotype.Service;

import nz.ac.aut.comp713.login_service.model.Account;
import nz.ac.aut.comp713.login_service.model.AccountRole;
import nz.ac.aut.comp713.login_service.repository.AccountRepository;

/** The persisted desired state makes partial cross-service changes visible and retryable. */
@Service
public class AccountActivationService {

    private final AccountRepository accounts;
    private final DomainProvisioningClient domain;
    private final SessionService sessions;

    public AccountActivationService(AccountRepository accounts, DomainProvisioningClient domain,
                                    SessionService sessions) {
        this.accounts = accounts;
        this.domain = domain;
        this.sessions = sessions;
    }

    public synchronized boolean change(Long accountId, boolean desiredActive) {
        Account account = accounts.findById(accountId).orElseThrow();
        if (account.getRole() == AccountRole.ADMIN || account.getProfileId() == null) return false;
        if (account.isActive() == desiredActive && account.getDesiredActive() == null) return true;

        account.beginActivationChange(desiredActive, Instant.now());
        accounts.saveAndFlush(account);
        try {
            if (!desiredActive) sessions.revokeAllForAccount(accountId);
            domain.setActive(account, desiredActive);
            account.finishActivationChange(desiredActive, Instant.now());
            accounts.saveAndFlush(account);
            return true;
        } catch (RuntimeException exception) {
            // The desired state remains in login.db for an admin retry.
            return false;
        }
    }
}
