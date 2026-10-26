package nz.ac.aut.comp713.login_service.service;

import org.springframework.stereotype.Service;

import nz.ac.aut.comp713.login_service.model.Account;
import nz.ac.aut.comp713.login_service.model.AccountRole;
import nz.ac.aut.comp713.login_service.model.RegistrationRequest;
import nz.ac.aut.comp713.login_service.model.RegistrationStatus;
import nz.ac.aut.comp713.login_service.repository.AccountRepository;
import nz.ac.aut.comp713.login_service.repository.RegistrationRequestRepository;

/** Recoverable registration job: persisted profile ID prevents duplicate domain creation on retry. */
@Service
public class RegistrationProvisioningService {

    private final RegistrationRequestRepository requests;
    private final AccountRepository accounts;
    private final DomainProvisioningClient domain;

    public RegistrationProvisioningService(RegistrationRequestRepository requests,
                                           AccountRepository accounts, DomainProvisioningClient domain) {
        this.requests = requests;
        this.accounts = accounts;
        this.domain = domain;
    }

    public synchronized boolean provision(Long requestId) {
        RegistrationRequest request = requests.findById(requestId).orElseThrow();
        if (request.getStatus() == RegistrationStatus.PROVISIONED) return true;
        AccountRole role = AccountRole.valueOf(request.getRole().name());

        var existing = accounts.findByNormalizedUsername(request.getNormalizedUsername());
        if (existing.isPresent()) {
            Account account = existing.get();
            if (request.getProfileId() == null || account.getRole() != role
                    || !request.getProfileId().equals(account.getProfileId())) {
                request.markRetryable();
                requests.saveAndFlush(request);
                return false;
            }
            if (!account.isActive()) {
                try {
                    domain.ensureInactive(request.getRole(), request.getProfileId());
                } catch (RuntimeException exception) {
                    request.markRetryable();
                    requests.saveAndFlush(request);
                    return false;
                }
            }
            request.markProvisioned(account.getId());
            requests.saveAndFlush(request);
            return true;
        }

        if (request.getProfileId() == null) {
            try {
                DomainProvisioningClient.ProfileResponse profile = domain.create(request);
                request.markProfileCreated(profile.id());
                request = requests.saveAndFlush(request);
                if (profile.active()) {
                    domain.ensureInactive(request.getRole(), profile.id());
                }
            } catch (RuntimeException exception) {
                request.markRetryable();
                requests.saveAndFlush(request);
                return false;
            }
        } else {
            try {
                domain.ensureInactive(request.getRole(), request.getProfileId());
            } catch (RuntimeException exception) {
                request.markRetryable();
                requests.saveAndFlush(request);
                return false;
            }
        }

        try {
            Account account = accounts.saveAndFlush(new Account(request.getUsername(),
                    request.getNormalizedUsername(), request.getPasswordHash(),
                    role, request.getProfileId(), false));
            request.markProvisioned(account.getId());
            requests.saveAndFlush(request);
            return true;
        } catch (RuntimeException exception) {
            // The request keeps the profile ID for an administrator to retry or repair.
            request.markRetryable();
            requests.saveAndFlush(request);
            return false;
        }
    }
}
