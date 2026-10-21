package nz.ac.aut.comp713.auth_service;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

@Service
public class AccountManagement {
    private final AccountRepository accounts;
    private final PasswordEncoder passwords;

    public AccountManagement(AccountRepository accounts, PasswordEncoder passwords) {
        this.accounts = accounts;
        this.passwords = passwords;
    }

    public Account create(String username, String password, String role) {
        String normalized = username == null ? "" : username.trim().toLowerCase();
        if (!normalized.matches("[a-z0-9._@-]{3,100}"))
            throw new IllegalArgumentException("Username must be 3–100 letters, numbers, or . _ @ -");
        if (password == null || password.length() < 12)
            throw new IllegalArgumentException("Password must have at least 12 characters");
        if (!List.of("ADMIN", "CUSTOMER", "DISPATCHER").contains(role))
            throw new IllegalArgumentException("Invalid role");
        if (accounts.existsByUsernameIgnoreCase(normalized))
            throw new IllegalArgumentException("Username already exists");
        return accounts.save(new Account(UUID.randomUUID().toString(), normalized, passwords.encode(password), role));
    }

}

@Component
class AdminBootstrap implements ApplicationRunner {
    private final AccountRepository accounts;
    private final AccountManagement management;
    private final String username;
    private final String password;

    AdminBootstrap(AccountRepository accounts, AccountManagement management,
            @Value("${auth.admin.username}") String username,
            @Value("${auth.admin.password}") String password) {
        this.accounts = accounts;
        this.management = management;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (accounts.count() == 0) {
            if (password.isBlank())
                throw new IllegalStateException("Configure auth.admin.password (at least 12 characters) for the first startup");
            management.create(username, password, "ADMIN");
        }
    }
}
