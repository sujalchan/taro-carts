package nz.ac.aut.comp713.login_service.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import nz.ac.aut.comp713.login_service.service.AdminPasswordService;
import nz.ac.aut.comp713.login_service.service.SessionService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Session-token protected password change used by authenticated web portals. */
@RestController
@RequestMapping("/api/v1/account")
public class AccountPasswordApiController {
    public record PasswordChangeRequest(
            @NotBlank(message = "Session token is required.") String sessionToken,
            @NotBlank(message = "Current password is required.") String currentPassword,
            @NotBlank(message = "New password is required.") @Size(min = 12, max = 72,
                    message = "New password must be 12 to 72 characters.") String newPassword,
            @NotBlank(message = "Please confirm your new password.") String confirmPassword) {}

    private final SessionService sessions;
    private final AdminPasswordService passwords;

    public AccountPasswordApiController(SessionService sessions, AdminPasswordService passwords) {
        this.sessions = sessions;
        this.passwords = passwords;
    }

    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(@jakarta.validation.Valid @RequestBody PasswordChangeRequest request) {
        var session = sessions.validate(request.sessionToken())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Your session has expired. Sign in again before changing your password."));
        if (!passwords.changeForAccount(session.getAccount().getId(), request.currentPassword(),
                request.newPassword(), request.confirmPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Password was not changed. Check your current password and make sure both new password entries match.");
        }
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
