package nz.ac.aut.comp713.auth_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

class AccountManagementTest {
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final PasswordEncoder passwords = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    private AccountManagement management;

    @BeforeEach
    void setUp() {
        management = new AccountManagement(accounts, passwords);
        when(accounts.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsCustomerWithStableIdAndHashedPassword() {
        Account account = management.create(" Customer.One ", "a secure password", "CUSTOMER");

        assertEquals("customer.one", account.getUsername());
        assertEquals("CUSTOMER", account.getRole());
        UUID.fromString(account.getId());
        assertFalse(account.getPasswordHash().contains("a secure password"));
        assertTrue(passwords.matches("a secure password", account.getPasswordHash()));
    }

    @Test
    void rejectsDuplicateUsername() {
        when(accounts.existsByUsernameIgnoreCase("customer.one")).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> management.create("Customer.One", "a secure password", "CUSTOMER"));
    }

    @Test
    void rejectsUnsupportedRoleAndShortPassword() {
        assertThrows(IllegalArgumentException.class,
                () -> management.create("customer.one", "a secure password", "SERVICE"));
        assertThrows(IllegalArgumentException.class,
                () -> management.create("customer.one", "short", "CUSTOMER"));
    }
}
