package nz.ac.aut.comp713.login_service.service;

import java.util.Set;

import org.springframework.stereotype.Service;

import nz.ac.aut.comp713.login_service.model.AccountRole;

/**
 * Restricts redirects to role-appropriate destinations and exact admin return
 * URLs.
 */
@Service
public class LoginDestinations {

    private static final Set<String> ADMIN_RETURN_URLS = Set.of(
            "http://localhost:8081/customers", "http://localhost:8081/customers-client.html",
            "http://localhost:8082/allocations", "http://localhost:8082/allocations-client.html",
            "http://localhost:8083/taro-types", "http://localhost:8083/taro-types-client.html",
            "http://localhost:8084/", "http://localhost:8084/dispatchers",
            "http://localhost:8084/dispatchers-client.html",
            "http://localhost:8081/portal/allocations", "http://localhost:8084/portal/deliveries");

    /**
     * Resolves the post-login destination without accepting arbitrary redirect
     * URLs.
     */
    public String destination(AccountRole role, String requested) {
        if (role == AccountRole.ADMIN) {
            if (requested != null && ADMIN_RETURN_URLS.contains(requested)) {
                return requested;
            }
            return "/admin/accounts";
        }
        if (role == AccountRole.CUSTOMER)
            return "http://localhost:8081/portal/allocations";
        return "http://localhost:8084/portal/deliveries";
    }

    /**
     * Returns an allow-listed admin destination for rendering the login form,
     * otherwise an empty string.
     */
    public String permittedReturnTo(String requested) {
        return requested != null && ADMIN_RETURN_URLS.contains(requested) ? requested : "";
    }
}
