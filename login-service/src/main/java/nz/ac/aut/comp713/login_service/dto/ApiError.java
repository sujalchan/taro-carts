package nz.ac.aut.comp713.login_service.dto;

public record ApiError(String code, String message, String path) {
}
