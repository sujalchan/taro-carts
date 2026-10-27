package nz.ac.aut.comp713.login_service.model;

/**
 * Persisted provisioning states used to resume or repair incomplete
 * registration work.
 */
public enum RegistrationStatus {
    PENDING_PROVISIONING,
    PROVISIONED,
    REPAIR_REQUIRED
}
