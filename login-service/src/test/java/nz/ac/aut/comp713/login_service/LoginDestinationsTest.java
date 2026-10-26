package nz.ac.aut.comp713.login_service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import nz.ac.aut.comp713.login_service.model.AccountRole;
import nz.ac.aut.comp713.login_service.service.LoginDestinations;

class LoginDestinationsTest {

    private final LoginDestinations destinations = new LoginDestinations();

    @Test
    void allowsOnlyExactLocalAdminManagementDestinations() {
        String allowed = "http://localhost:8081/customers";
        assertThat(destinations.permittedReturnTo(allowed)).isEqualTo(allowed);
        assertThat(destinations.destination(AccountRole.ADMIN, allowed)).isEqualTo(allowed);
        assertThat(destinations.permittedReturnTo("https://example.com")).isEmpty();
        assertThat(destinations.destination(AccountRole.ADMIN, "https://example.com"))
                .isEqualTo("/admin/accounts");
        assertThat(destinations.destination(AccountRole.CUSTOMER, allowed))
                .isEqualTo("http://localhost:8081/portal/allocations");
    }
}
