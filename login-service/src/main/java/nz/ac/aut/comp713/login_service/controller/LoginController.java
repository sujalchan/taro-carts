package nz.ac.aut.comp713.login_service.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import nz.ac.aut.comp713.login_service.service.AuthCookies;
import nz.ac.aut.comp713.login_service.service.AuthenticationService;
import nz.ac.aut.comp713.login_service.service.LoginAttemptLimiter;
import nz.ac.aut.comp713.login_service.service.LoginDestinations;
import nz.ac.aut.comp713.login_service.service.SessionService;
import nz.ac.aut.comp713.login_service.service.SecurityAudit;

/** Handles browser sign-in and sign-out with CSRF-protected forms and opaque session cookies. */
@Controller
public class LoginController {

    private final AuthenticationService authentication;
    private final SessionService sessions;
    private final AuthCookies cookies;
    private final LoginAttemptLimiter limiter;
    private final LoginDestinations destinations;

    public LoginController(AuthenticationService authentication, SessionService sessions,
                           AuthCookies cookies, LoginAttemptLimiter limiter, LoginDestinations destinations) {
        this.authentication = authentication;
        this.sessions = sessions;
        this.cookies = cookies;
        this.limiter = limiter;
        this.destinations = destinations;
    }

    /** Prepares the sign-in page, validates any existing cookie, and issues a CSRF token. */
    @GetMapping("/login")
    public String loginPage(@CookieValue(value = AuthCookies.SESSION, required = false) String sessionToken,
                            @RequestParam(value = "returnTo", required = false) String returnTo,
                            @RequestParam(value = "passwordChanged", required = false) String passwordChanged,
                            HttpServletResponse response, Model model) {
        response.setHeader("Cache-Control", "no-store");
        model.addAttribute("csrfToken", cookies.issueCsrf(response));
        model.addAttribute("returnTo", destinations.permittedReturnTo(returnTo));
        model.addAttribute("passwordChanged", "1".equals(passwordChanged));
        addSessionNavigation(model, sessionToken);
        return "login";
    }

    /** Authenticates the form and replaces any previous browser session on success. */
    @PostMapping("/login")
    public String login(@RequestParam String username, @RequestParam String password,
                        @RequestParam(value = "csrf", required = false) String csrf,
                        @RequestParam(value = "returnTo", required = false) String returnTo,
                        @CookieValue(value = AuthCookies.CSRF, required = false) String csrfCookie,
                        @CookieValue(value = AuthCookies.SESSION, required = false) String oldSession,
                        HttpServletRequest request, HttpServletResponse response, Model model) {
        if (!cookies.validCsrf(csrf, csrfCookie)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        String address = request.getRemoteAddr();
        if (!limiter.allowed(address, username)) {
            SecurityAudit.denied("login.denied", "reason=rate_limited");
            return loginError(model, username, returnTo, csrfCookie, oldSession,
                    "Sign-in failed. Check your details or account status and try again.");
        }
        var result = authentication.authenticateForLogin(username, password);
        if (result.awaitingActivation()) {
            SecurityAudit.denied("login.denied", "reason=inactive_account");
            limiter.failed(address, username);
            return loginError(model, username, returnTo, csrfCookie, oldSession,
                    "Your password is correct, but your account is waiting for administrator activation. You can sign in once it has been enabled.");
        }
        var account = result.account();
        if (account == null) {
            SecurityAudit.denied("login.denied", "reason=credentials_or_inactive_account");
            limiter.failed(address, username);
            return loginError(model, username, returnTo, csrfCookie, oldSession,
                    "Sign-in failed. Check your details or account status and try again.");
        }
        limiter.succeeded(address, username);
        SecurityAudit.event("login.succeeded", "accountId=" + account.getId()
                + " role=" + account.getRole().name());
        sessions.revoke(oldSession);
        cookies.issueSession(response, sessions.issue(account));
        cookies.clearCsrf(response);
        return "redirect:" + destinations.destination(account.getRole(), returnTo);
    }

    /** Revokes the presented session only after validating the form CSRF token. */
    @PostMapping("/logout")
    public String logout(@RequestParam(value = "csrf", required = false) String csrf,
                         @CookieValue(value = AuthCookies.CSRF, required = false) String csrfCookie,
                         @CookieValue(value = AuthCookies.SESSION, required = false) String sessionToken,
                         HttpServletResponse response) {
        if (!cookies.validCsrf(csrf, csrfCookie)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        sessions.revoke(sessionToken);
        SecurityAudit.event("logout", "sessionPresented=" + (sessionToken != null));
        cookies.clearSession(response);
        cookies.clearCsrf(response);
        return "redirect:/login";
    }

    @GetMapping("/logout")
    public String logoutPage(@CookieValue(value = AuthCookies.SESSION, required = false) String sessionToken,
                             HttpServletResponse response, Model model) {
        var existing = sessions.validate(sessionToken);
        if (existing.isEmpty()) return "redirect:/login";
        response.setHeader("Cache-Control", "no-store");
        model.addAttribute("username", existing.get().getAccount().getUsername());
        model.addAttribute("returnUrl", destinations.destination(existing.get().getAccount().getRole(), null));
        model.addAttribute("csrfToken", cookies.issueCsrf(response));
        return "logout";
    }

    private String loginError(Model model, String username, String returnTo, String csrfCookie,
                              String sessionToken, String message) {
        model.addAttribute("loginError", message);
        model.addAttribute("username", username);
        model.addAttribute("returnTo", destinations.permittedReturnTo(returnTo));
        model.addAttribute("csrfToken", csrfCookie);
        addSessionNavigation(model, sessionToken);
        return "login";
    }

    /** Builds navigation only from the validated session, never from the attempted login. */
    private void addSessionNavigation(Model model, String sessionToken) {
        var existing = sessions.validate(sessionToken);
        model.addAttribute("signedInUsername", existing.map(session -> session.getAccount().getUsername()).orElse(null));
        model.addAttribute("signedInRole", existing.map(session -> session.getAccount().getRole().name()).orElse(null));
        model.addAttribute("accountHomeUrl", existing.map(session ->
                destinations.destination(session.getAccount().getRole(), null)).orElse("/login"));
    }
}
