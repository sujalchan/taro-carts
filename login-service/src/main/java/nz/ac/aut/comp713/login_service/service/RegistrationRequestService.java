package nz.ac.aut.comp713.login_service.service;

import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import nz.ac.aut.comp713.login_service.model.RegistrationRequest;
import nz.ac.aut.comp713.login_service.model.RegistrationRole;
import nz.ac.aut.comp713.login_service.repository.AccountRepository;
import nz.ac.aut.comp713.login_service.repository.RegistrationRequestRepository;
import nz.ac.aut.comp713.login_service.web.RegistrationForm;

/** Persists a pending request before the UI is allowed to show a receipt. */
@Service
public class RegistrationRequestService {

    private final RegistrationRequestRepository repository;
    private final AccountRepository accounts;
    private final BCryptPasswordEncoder passwordEncoder;

    public RegistrationRequestService(RegistrationRequestRepository repository,
                                      AccountRepository accounts, BCryptPasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
    }

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
            return repository.saveAndFlush(request).getId();
        } catch (DataIntegrityViolationException exception) {
            // The unique database constraint also handles concurrent requests.
            if (repository.existsByNormalizedUsername(normalizedUsername)) {
                throw new DuplicateUsernameException();
            }
            throw exception;
        }
    }

    public boolean exists(Long id) {
        return repository.existsById(id);
    }

    private String trimOptional(String value) {
        return value == null ? "" : value.trim();
    }

    public static class DuplicateUsernameException extends RuntimeException {
    }
}
