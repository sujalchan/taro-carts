package nz.ac.aut.comp713.login_service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes credential-safe audit events; callers must never include tokens or
 * request bodies.
 */
public final class SecurityAudit {
    private static final Logger LOGGER = LoggerFactory.getLogger("security.audit");

    private SecurityAudit() {
    }

    public static void event(String event, String details) {
        LOGGER.info("event={} {}", event, details == null ? "" : details);
    }

    public static void denied(String event, String details) {
        LOGGER.warn("event={} {}", event, details == null ? "" : details);
    }
}
