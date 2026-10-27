package nz.ac.aut.comp713.login_service.service;

import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import nz.ac.aut.comp713.login_service.model.RegistrationRequest;
import nz.ac.aut.comp713.login_service.model.RegistrationRole;
import nz.ac.aut.comp713.login_service.model.RegistrationStatus;
import nz.ac.aut.comp713.login_service.repository.AccountRepository;
import nz.ac.aut.comp713.login_service.repository.RegistrationRequestRepository;
import nz.ac.aut.comp713.login_service.web.RegistrationForm;

/**
 * Normalizes and persists a unique registration request before returning its
 * receipt ID.
 */
@Service
public class RegistrationRequestService {

    private final RegistrationRequestRepository repository;
    private final AccountRepository accounts;
    private final BCryptPasswordEncoder passwordEncoder;
    private final RegistrationProvisioningService provisioning;

    public RegistrationRequestService(RegistrationRequestRepository repository,
            AccountRepository accounts, BCryptPasswordEncoder passwordEncoder,
            RegistrationProvisioningService provisioning) {
        this.repository = repository;
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.provisioning = provisioning;
    }

    /**
     * Hashes the submitted password, stores the request, then attempts recoverable
     * profile provisioning.
     */
    public Long submit(RegistrationRole role, RegistrationForm form) {
        String username = form.getUsername().trim();
        String normalizedUsername = username.toLowerCase(Locale.ROOT);
        if (accounts.existsByNormalizedUsername(normalizedUsername)
                || repository.existsByNormalizedUsername(normalizedUsername)) {
            throw new DuplicateUsernameException();
        }

        RegistrationRequest request = new RegistrationRequest(
                username,
                normalizedUsername,
                passwordEncoder.encode(form.getPassword()),
                role,
                form.getName().trim(),
                trimOptional(form.getContactName()),
                trimOptional(form.getPhone()),
                form.getAddress().trim());
        try {
            Long id = repository.saveAndFlush(request).getId();
            provisioning.provision(id);
            return id;
        } catch (DataIntegrityViolationException exception) {
            // The unique database constraint also handles concurrent requests.
            if (repository.existsByNormalizedUsername(normalizedUsername)) {
                throw new DuplicateUsernameException();
            }
            throw exception;
        }
    }

    /** Returns the latest provisioning state used by the registration receipt. */
    public RegistrationStatus status(Long id) {
        return repository.findById(id).map(RegistrationRequest::getStatus).orElse(null);
    }

    private String trimOptional(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * Indicates that the normalized username belongs to an account or another
     * request.
     */
    public static class DuplicateUsernameException extends RuntimeException {
    }
}
