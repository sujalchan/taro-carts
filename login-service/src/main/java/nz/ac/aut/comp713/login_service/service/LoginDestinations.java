package nz.ac.aut.comp713.login_service.service;

import org.springframework.stereotype.Service;

import nz.ac.aut.comp713.login_service.model.AccountRole;

/** Exact return destinations avoid open redirects and cross-role navigation. */
@Service
public class LoginDestinations {

    public String destination(AccountRole role, String requested) {
        if (role == AccountRole.ADMIN) {
            if ("http://localhost:8081/portal/allocations".equals(requested)
                    || "http://localhost:8084/portal/deliveries".equals(requested)) {
                return requested;
            }
            return "/admin/accounts";
        }
        if (role == AccountRole.CUSTOMER) return "http://localhost:8081/portal/allocations";
        return "http://localhost:8084/portal/deliveries";
    }

    public String permittedReturnTo(String requested) {
        return "http://localhost:8081/portal/allocations".equals(requested)
                || "http://localhost:8084/portal/deliveries".equals(requested)
                ? requested : "";
    }
}
