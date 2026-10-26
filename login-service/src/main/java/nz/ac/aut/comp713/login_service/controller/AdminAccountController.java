package nz.ac.aut.comp713.login_service.controller;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletResponse;
import nz.ac.aut.comp713.login_service.model.AccountRole;
import nz.ac.aut.comp713.login_service.repository.AccountRepository;
import nz.ac.aut.comp713.login_service.repository.RegistrationRequestRepository;
import nz.ac.aut.comp713.login_service.service.AuthCookies;
import nz.ac.aut.comp713.login_service.service.AdminPasswordService;
import nz.ac.aut.comp713.login_service.service.SessionService;

@Controller
public class AdminAccountController {

    private final SessionService sessions;
    private final AuthCookies cookies;
    private final AccountRepository accounts;
    private final RegistrationRequestRepository requests;
    private final AdminPasswordService passwords;

    public AdminAccountController(SessionService sessions, AuthCookies cookies,
                                  AccountRepository accounts, RegistrationRequestRepository requests,
                                  AdminPasswordService passwords) {
        this.sessions = sessions;
        this.cookies = cookies;
        this.accounts = accounts;
        this.requests = requests;
        this.passwords = passwords;
    }

    @GetMapping("/admin/accounts")
    public String accounts(@CookieValue(value = AuthCookies.SESSION, required = false) String sessionToken,
                           @RequestParam(value = "passwordError", required = false) String passwordError,
                           HttpServletResponse response, Model model) {
        var session = sessions.validate(sessionToken);
        if (session.isEmpty()) return "redirect:/login";
        if (session.get().getAccount().getRole() != AccountRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        response.setHeader("Cache-Control", "no-store");
        model.addAttribute("accounts", accounts.findAll());
        model.addAttribute("requests", requests.findAllByOrderBySubmittedAtDesc());
        model.addAttribute("csrfToken", cookies.issueCsrf(response));
        model.addAttribute("passwordError", "1".equals(passwordError));
        return "admin-accounts";
    }

    @PostMapping("/admin/password")
    public String changePassword(@CookieValue(value = AuthCookies.SESSION, required = false) String sessionToken,
                                 @CookieValue(value = AuthCookies.CSRF, required = false) String csrfCookie,
                                 @RequestParam(value = "csrf", required = false) String csrf,
                                 @RequestParam String currentPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 HttpServletResponse response) {
        if (!cookies.validCsrf(csrf, csrfCookie)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        var session = sessions.validate(sessionToken);
        if (session.isEmpty()) return "redirect:/login";
        if (session.get().getAccount().getRole() != AccountRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        if (!passwords.change(session.get().getAccount().getId(), currentPassword,
                newPassword, confirmPassword)) {
            return "redirect:/admin/accounts?passwordError=1";
        }
        cookies.clearSession(response);
        cookies.clearCsrf(response);
        return "redirect:/login?passwordChanged=1";
    }
}
