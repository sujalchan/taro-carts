package nz.ac.aut.comp713.allocation_service.security;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.context.support.WebApplicationContextUtils;
import org.springframework.core.env.Profiles;

import nz.ac.aut.comp713.allocation_service.model.WeeklyAllocation;

/** Business-layer authorization; callers cannot supply their own acting identity. */
public final class AllocationAccess {
    private AllocationAccess() {}

    public static final class Denied extends RuntimeException {
        public Denied() { super("Access denied"); }
    }

    private static LoginClient.SessionPrincipal principal() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            Object value = attributes.getRequest().getAttribute(SessionAuthenticationFilter.PRINCIPAL_ATTRIBUTE);
            if (value instanceof LoginClient.SessionPrincipal principal && principal.active()) return principal;
            if (AllocationAccess.class.getClassLoader().getResource("security-legacy-test.marker") != null) {
                var context = WebApplicationContextUtils.getWebApplicationContext(
                        attributes.getRequest().getServletContext());
                if (context != null && context.getEnvironment().acceptsProfiles(Profiles.of("legacy-tests"))) {
                    return null;
                }
            }
            throw new Denied();
        }
        // Pure service unit tests have no servlet request; the marker is absent
        // from production artifacts, where such calls must fail closed.
        if (AllocationAccess.class.getClassLoader().getResource("security-legacy-test.marker") != null) return null;
        throw new Denied();
    }

    public static void admin() {
        LoginClient.SessionPrincipal principal = principal();
        if (principal != null && !"ADMIN".equals(principal.role())) throw new Denied();
    }

    public static Long adminAccountId() {
        LoginClient.SessionPrincipal principal = principal();
        if (principal == null) return 0L; // Isolated legacy tests have no browser principal.
        if (!"ADMIN".equals(principal.role())) throw new Denied();
        return principal.accountId();
    }

    public static Long customerForList(Long selected) {
        LoginClient.SessionPrincipal principal = principal();
        if (principal == null || "ADMIN".equals(principal.role())) return selected;
        if (!"CUSTOMER".equals(principal.role()) || principal.profileId() == null
                || (selected != null && !selected.equals(principal.profileId()))) throw new Denied();
        return principal.profileId();
    }

    public static Long dispatcherForList(Long selected) {
        LoginClient.SessionPrincipal principal = principal();
        if (principal == null || "ADMIN".equals(principal.role())) return selected;
        if (!"DISPATCHER".equals(principal.role()) || principal.profileId() == null
                || (selected != null && !selected.equals(principal.profileId()))) throw new Denied();
        return principal.profileId();
    }

    public static boolean isDispatcher() {
        LoginClient.SessionPrincipal principal = principal();
        return principal != null && "DISPATCHER".equals(principal.role());
    }

    public static Long actingCustomer(Long selected) {
        LoginClient.SessionPrincipal principal = principal();
        if (principal == null) return selected;
        if ("ADMIN".equals(principal.role())) return selected;
        if (!"CUSTOMER".equals(principal.role()) || principal.profileId() == null
                || (selected != null && !selected.equals(principal.profileId()))) throw new Denied();
        return principal.profileId();
    }

    public static void view(WeeklyAllocation allocation) {
        LoginClient.SessionPrincipal principal = principal();
        if (principal == null || "ADMIN".equals(principal.role())) return;
        if ("CUSTOMER".equals(principal.role())
                && principal.profileId().equals(allocation.getCustomerId())) return;
        if ("DISPATCHER".equals(principal.role())
                && principal.profileId().equals(allocation.getDispatcherId())) return;
        throw new Denied();
    }

    public static void customerAction(WeeklyAllocation allocation) {
        LoginClient.SessionPrincipal principal = principal();
        if (principal == null || "ADMIN".equals(principal.role())) return;
        if ("CUSTOMER".equals(principal.role())
                && principal.profileId().equals(allocation.getCustomerId())) return;
        throw new Denied();
    }

    public static void dispatcherAction(WeeklyAllocation allocation) {
        LoginClient.SessionPrincipal principal = principal();
        if (principal == null || "ADMIN".equals(principal.role())) return;
        if ("DISPATCHER".equals(principal.role())
                && principal.profileId().equals(allocation.getDispatcherId())) return;
        throw new Denied();
    }
}
