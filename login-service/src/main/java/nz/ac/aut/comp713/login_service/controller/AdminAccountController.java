package nz.ac.aut.comp713.login_service.controller;

import java.util.ArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletResponse;
import nz.ac.aut.comp713.login_service.model.Account;
import nz.ac.aut.comp713.login_service.model.AccountRole;
import nz.ac.aut.comp713.login_service.model.RegistrationRequest;
import nz.ac.aut.comp713.login_service.model.RegistrationStatus;
import nz.ac.aut.comp713.login_service.repository.AccountRepository;
import nz.ac.aut.comp713.login_service.repository.RegistrationRequestRepository;
import nz.ac.aut.comp713.login_service.service.AuthCookies;
import nz.ac.aut.comp713.login_service.service.AdminPasswordService;
import nz.ac.aut.comp713.login_service.service.AccountActivationService;
import nz.ac.aut.comp713.login_service.service.RegistrationProvisioningService;
import nz.ac.aut.comp713.login_service.service.SessionService;

@Controller
public class AdminAccountController {

    public record PendingRequestRow(RegistrationRequest request, String status, boolean retryable) {}

    private final SessionService sessions;
    private final AuthCookies cookies;
    private final AccountRepository accounts;
    private final RegistrationRequestRepository requests;
    private final AdminPasswordService passwords;
    private final AccountActivationService activation;
    private final RegistrationProvisioningService provisioning;

    public AdminAccountController(SessionService sessions, AuthCookies cookies,
                                  AccountRepository accounts, RegistrationRequestRepository requests,
                                  AdminPasswordService passwords, AccountActivationService activation,
                                  RegistrationProvisioningService provisioning) {
        this.sessions = sessions;
        this.cookies = cookies;
        this.accounts = accounts;
        this.requests = requests;
        this.passwords = passwords;
        this.activation = activation;
        this.provisioning = provisioning;
    }

    @GetMapping("/admin/accounts")
    public String accounts(@CookieValue(value = AuthCookies.SESSION, required = false) String sessionToken,
                           @RequestParam(value = "passwordError", required = false) String passwordError,
                           @RequestParam(value = "activationError", required = false) String activationError,
                           @RequestParam(value = "provisioningError", required = false) String provisioningError,
                           HttpServletResponse response, Model model) {
        var session = sessions.validate(sessionToken);
        if (session.isEmpty()) return "redirect:/login";
        if (session.get().getAccount().getRole() != AccountRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        response.setHeader("Cache-Control", "no-store");
        var accountList = accounts.findAll();
        var accountsById = accountList.stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));
        var pendingRequests = new ArrayList<PendingRequestRow>();
        for (RegistrationRequest request : requests.findAllByOrderBySubmittedAtDesc()) {
            Account linkedAccount = accountsById.get(request.getAccountId());
            if (request.getStatus() == RegistrationStatus.PROVISIONED
                    && linkedAccount != null && linkedAccount.isActive()
                    && linkedAccount.getDesiredActive() == null) {
                continue;
            }
            String status;
            boolean retryable;
            if (request.getStatus() == RegistrationStatus.REPAIR_REQUIRED) {
                status = "Needs repair";
                retryable = true;
            } else if (request.getStatus() == RegistrationStatus.PENDING_PROVISIONING) {
                status = "Awaiting profile";
                retryable = true;
            } else if (linkedAccount == null) {
                status = "Needs repair";
                retryable = true;
            } else if (linkedAccount.getDesiredActive() != null) {
                status = "Sync pending";
                retryable = false;
            } else {
                status = "Awaiting approval";
                retryable = false;
            }
            pendingRequests.add(new PendingRequestRow(request, status, retryable));
        }
        model.addAttribute("accounts", accountList);
        model.addAttribute("pendingRequests", pendingRequests);
        model.addAttribute("csrfToken", cookies.issueCsrf(response));
        model.addAttribute("passwordError", "1".equals(passwordError));
        model.addAttribute("activationError", "1".equals(activationError));
        model.addAttribute("provisioningError", "1".equals(provisioningError));
        return "admin-accounts";
    }

    @PostMapping("/admin/requests/{id}/retry")
    public String retryProvisioning(@PathVariable Long id,
                                    @CookieValue(value = AuthCookies.SESSION, required = false) String sessionToken,
                                    @CookieValue(value = AuthCookies.CSRF, required = false) String csrfCookie,
                                    @RequestParam(value = "csrf", required = false) String csrf) {
        requireAdmin(sessionToken, csrfCookie, csrf);
        if (!requests.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return provisioning.provision(id) ? "redirect:/admin/accounts"
                : "redirect:/admin/accounts?provisioningError=1";
    }

    @PostMapping("/admin/accounts/{id}/active")
    public String changeActive(@PathVariable Long id,
                               @CookieValue(value = AuthCookies.SESSION, required = false) String sessionToken,
                               @CookieValue(value = AuthCookies.CSRF, required = false) String csrfCookie,
                               @RequestParam(value = "csrf", required = false) String csrf,
                               @RequestParam boolean active) {
        requireAdmin(sessionToken, csrfCookie, csrf);
        if (!accounts.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return activation.change(id, active) ? "redirect:/admin/accounts"
                : "redirect:/admin/accounts?activationError=1";
    }

    @PostMapping("/admin/password")
    public String changePassword(@CookieValue(value = AuthCookies.SESSION, required = false) String sessionToken,
                                 @CookieValue(value = AuthCookies.CSRF, required = false) String csrfCookie,
                                 @RequestParam(value = "csrf", required = false) String csrf,
                                 @RequestParam String currentPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 HttpServletResponse response) {
        Long adminId = requireAdmin(sessionToken, csrfCookie, csrf);
        if (!passwords.change(adminId, currentPassword,
                newPassword, confirmPassword)) {
            return "redirect:/admin/accounts?passwordError=1";
        }
        cookies.clearSession(response);
        cookies.clearCsrf(response);
        return "redirect:/login?passwordChanged=1";
    }

    private Long requireAdmin(String sessionToken, String csrfCookie, String csrf) {
        if (!cookies.validCsrf(csrf, csrfCookie)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        var session = sessions.validate(sessionToken);
        if (session.isEmpty() || session.get().getAccount().getRole() != AccountRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return session.get().getAccount().getId();
    }
}
