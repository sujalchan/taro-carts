package nz.ac.aut.comp713.login_service.dto;

/** Standard machine-readable error returned by login-service API endpoints. */
public record ApiError(String code, String message, String path) {}
