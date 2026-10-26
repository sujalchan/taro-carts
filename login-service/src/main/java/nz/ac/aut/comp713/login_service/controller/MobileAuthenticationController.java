package nz.ac.aut.comp713.login_service.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import nz.ac.aut.comp713.login_service.model.Account;
import nz.ac.aut.comp713.login_service.model.RegistrationRole;
import nz.ac.aut.comp713.login_service.service.AuthenticationService;
import nz.ac.aut.comp713.login_service.service.LoginAttemptLimiter;
import nz.ac.aut.comp713.login_service.service.RegistrationRequestService;
import nz.ac.aut.comp713.login_service.service.SessionService;
import nz.ac.aut.comp713.login_service.service.SecurityAudit;
import nz.ac.aut.comp713.login_service.web.RegistrationForm;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** JSON authentication endpoints for the native Android client. */
@RestController
@RequestMapping("/api/v1/mobile")
public class MobileAuthenticationController {
    public record LoginRequest(@NotBlank @Size(max = 100) String username,
                               @NotBlank @Size(max = 72) String password) {}
    public record LoginResponse(String sessionToken, String username, String role, Long profileId) {}
    public record RegistrationResponse(long requestId, String status, String message) {}
    public record LogoutRequest(@NotBlank String sessionToken) {}

    private final AuthenticationService authentication;
    private final RegistrationRequestService registrations;
    private final SessionService sessions;
    private final LoginAttemptLimiter limiter;

    public MobileAuthenticationController(AuthenticationService authentication,
            RegistrationRequestService registrations, SessionService sessions, LoginAttemptLimiter limiter) {
        this.authentication = authentication;
        this.registrations = registrations;
        this.sessions = sessions;
        this.limiter = limiter;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                                HttpServletRequest servletRequest) {
        String address = servletRequest.getRemoteAddr();
        if (!limiter.allowed(address, request.username())) {
            SecurityAudit.denied("mobile.login.denied", "reason=rate_limited");
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many attempts. Please wait before trying again.");
        }
        Account account = authentication.authenticate(request.username(), request.password()).orElse(null);
        if (account == null) {
            SecurityAudit.denied("mobile.login.denied", "reason=credentials_or_inactive_account");
            limiter.failed(address, request.username());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Sign-in failed. Check your details or account status and try again.");
        }
        limiter.succeeded(address, request.username());
        SecurityAudit.event("mobile.login.succeeded", "accountId=" + account.getId()
                + " role=" + account.getRole().name());
        SessionService.IssuedSession issued = sessions.issue(account);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new LoginResponse(issued.token(), account.getUsername(), account.getRole().name(),
                        account.getProfileId()));
    }

    @PostMapping("/register/{role}")
    public ResponseEntity<RegistrationResponse> register(@PathVariable String role,
                                                          @Valid @RequestBody MobileRegistrationRequest request) {
        RegistrationRole accountRole;
        try {
            accountRole = RegistrationRole.valueOf(role.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (!request.password().equals(request.confirmPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Passwords do not match.");
        }
        RegistrationForm form = new RegistrationForm();
        form.setUsername(request.username());
        form.setPassword(request.password());
        form.setConfirmPassword(request.confirmPassword());
        form.setName(request.name());
        form.setContactName(request.contactName());
        form.setPhone(request.phone());
        form.setAddress(request.address());
        try {
            long id = registrations.submit(accountRole, form);
            SecurityAudit.event("mobile.registration.requested", "requestId=" + id + " role=" + accountRole.name());
            return ResponseEntity.accepted().cacheControl(CacheControl.noStore())
                    .body(new RegistrationResponse(id, "PENDING",
                            "Request received. An administrator must approve the account before sign-in."));
        } catch (RegistrationRequestService.DuplicateUsernameException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That username has already been requested.");
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        sessions.revoke(request.sessionToken());
        SecurityAudit.event("mobile.logout", "sessionPresented=true");
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    public record MobileRegistrationRequest(
            @NotBlank @Size(min = 3, max = 100) @jakarta.validation.constraints.Pattern(
                    regexp = "[A-Za-z0-9._-]{3,100}") String username,
            @NotBlank @Size(min = 12, max = 72) String password,
            @NotBlank String confirmPassword,
            @NotBlank @Size(max = 100) String name,
            @Size(max = 100) String contactName,
            @jakarta.validation.constraints.Pattern(regexp = "^$|^\\+?[0-9]{7,15}$") String phone,
            @NotBlank @Size(max = 500) String address) {}
}
