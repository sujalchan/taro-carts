package nz.ac.aut.comp713.login_service.dto;

/**
 * Standard API failure body: stable error code, user-facing message, and
 * request path.
 */
public record ApiError(String code, String message, String path) {
}
