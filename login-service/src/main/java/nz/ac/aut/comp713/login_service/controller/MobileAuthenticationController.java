package nz.ac.aut.comp713.login_service.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import nz.ac.aut.comp713.login_service.model.Account;
import nz.ac.aut.comp713.login_service.model.RegistrationRole;
import nz.ac.aut.comp713.login_service.dto.ApiError;
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

/** JSON sign-in, registration, and sign-out endpoints for the native Android client. */
@RestController
@RequestMapping("/api/v1/mobile")
public class MobileAuthenticationController {
    /** Credentials accepted by the native client sign-in endpoint. */
    public record LoginRequest(
            @NotBlank(message = "Username is required.")
            @Size(max = 100, message = "Username must be 100 characters or fewer.") String username,
            @NotBlank(message = "Password is required.")
            @Size(max = 72, message = "Password must be 72 characters or fewer.") String password) {}
    /** Session credential and role/profile identity returned after successful sign-in. */
    public record LoginResponse(String sessionToken, String username, String role, Long profileId) {}
    /** Receipt returned after a pending account request has been saved. */
    public record RegistrationResponse(long requestId, String status, String message) {}
    /** Token submitted to revoke a native-client session. */
    public record LogoutRequest(@NotBlank(message = "Session token is required.") String sessionToken) {}

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

    /** Returns a one-time session token, or an API error that explains the sign-in failure safely. */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request,
                                                HttpServletRequest servletRequest) {
        String address = servletRequest.getRemoteAddr();
        if (!limiter.allowed(address, request.username())) {
            SecurityAudit.denied("mobile.login.denied", "reason=rate_limited");
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many attempts. Please wait before trying again.");
        }
        var result = authentication.authenticateForLogin(request.username(), request.password());
        if (result.awaitingActivation()) {
            SecurityAudit.denied("mobile.login.denied", "reason=inactive_account");
            limiter.failed(address, request.username());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).cacheControl(CacheControl.noStore())
                    .body(new ApiError("ACCOUNT_AWAITING_ACTIVATION",
                            "Your account is waiting for administrator activation. You can sign in once it has been enabled.",
                            servletRequest.getRequestURI()));
        }
        Account account = result.account();
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

    /** Persists a pending customer/dispatcher request and returns its receipt without activating it. */
    @PostMapping("/register/{role}")
    public ResponseEntity<RegistrationResponse> register(@PathVariable String role,
                                                          @Valid @RequestBody MobileRegistrationRequest request) {
        RegistrationRole accountRole;
        try {
            accountRole = RegistrationRole.valueOf(role.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Choose customer or dispatcher as the account type.");
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
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "That username is already in use or has a pending request. Choose another username.");
        }
    }

    /** Revokes the supplied session token; unknown tokens are treated as already signed out. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        sessions.revoke(request.sessionToken());
        SecurityAudit.event("mobile.logout", "sessionPresented=true");
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    /** Validated account and profile fields accepted by mobile registration. */
    public record MobileRegistrationRequest(
            @NotBlank(message = "Username is required.")
            @Size(min = 3, max = 100, message = "Username must be 3 to 100 characters.")
            @jakarta.validation.constraints.Pattern(regexp = "[A-Za-z0-9._-]{3,100}",
                    message = "Username can use letters, numbers, periods, underscores, and hyphens.") String username,
            @NotBlank(message = "Password is required.")
            @Size(min = 12, max = 72, message = "Password must be 12 to 72 characters.") String password,
            @NotBlank(message = "Please confirm your password.") String confirmPassword,
            @NotBlank(message = "Name is required.")
            @Size(max = 100, message = "Name must be 100 characters or fewer.") String name,
            @Size(max = 100, message = "Contact name must be 100 characters or fewer.") String contactName,
            @jakarta.validation.constraints.Pattern(regexp = "^$|^\\+?[0-9]{7,15}$",
                    message = "Phone must contain 7 to 15 digits, optionally starting with +.") String phone,
            @NotBlank(message = "Address is required.")
            @Size(max = 500, message = "Address must be 500 characters or fewer.") String address) {}
}
