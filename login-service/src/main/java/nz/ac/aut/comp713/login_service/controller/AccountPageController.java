package nz.ac.aut.comp713.login_service.controller;

import java.util.List;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import nz.ac.aut.comp713.login_service.model.RegistrationRole;
import nz.ac.aut.comp713.login_service.service.RegistrationRequestService;
import nz.ac.aut.comp713.login_service.service.AuthCookies;
import nz.ac.aut.comp713.login_service.service.RegistrationRequestService.DuplicateUsernameException;
import nz.ac.aut.comp713.login_service.service.SecurityAudit;
import nz.ac.aut.comp713.login_service.web.RegistrationForm;

/** Serves public registration pages and converts validated browser forms into account requests. */
@Controller
public class AccountPageController {

    private final RegistrationRequestService registrationRequestService;
    private final AuthCookies cookies;

    public AccountPageController(RegistrationRequestService registrationRequestService, AuthCookies cookies) {
        this.registrationRequestService = registrationRequestService;
        this.cookies = cookies;
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/login";
    }

    @GetMapping("/register")
    public String register() {
        return "register-type";
    }

    @GetMapping("/register/customer")
    public String registerCustomer(Model model, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        model.addAttribute("accountType", "customer");
        model.addAttribute("registration", new RegistrationForm());
        model.addAttribute("csrfToken", cookies.issueCsrf(response));
        return "register";
    }

    @GetMapping("/register/dispatcher")
    public String registerDispatcher(Model model, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        model.addAttribute("accountType", "dispatcher");
        model.addAttribute("registration", new RegistrationForm());
        model.addAttribute("csrfToken", cookies.issueCsrf(response));
        return "register";
    }

    @PostMapping("/register/customer")
    public String requestCustomerAccount(@Valid @ModelAttribute("registration") RegistrationForm form,
                                         BindingResult bindingResult,
                                         @RequestParam(value = "csrf", required = false) String csrf,
                                         @CookieValue(value = AuthCookies.CSRF, required = false) String csrfCookie,
                                         Model model) {
        return submitRequest(RegistrationRole.CUSTOMER, form, bindingResult, csrf, csrfCookie, model);
    }

    @PostMapping("/register/dispatcher")
    public String requestDispatcherAccount(@Valid @ModelAttribute("registration") RegistrationForm form,
                                           BindingResult bindingResult,
                                           @RequestParam(value = "csrf", required = false) String csrf,
                                           @CookieValue(value = AuthCookies.CSRF, required = false) String csrfCookie,
                                           Model model) {
        return submitRequest(RegistrationRole.DISPATCHER, form, bindingResult, csrf, csrfCookie, model);
    }

    @GetMapping("/register/received/{id}")
    public String requestReceived(@PathVariable Long id, Model model) {
        var status = registrationRequestService.status(id);
        if (status == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("requestId", id);
        model.addAttribute("requestStatus", status);
        return "request-received";
    }

    private String submitRequest(RegistrationRole role, RegistrationForm form,
                                 BindingResult bindingResult, String csrf, String csrfCookie, Model model) {
        if (!cookies.validCsrf(csrf, csrfCookie)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        if (form.getPassword() != null && form.getConfirmPassword() != null
                && !form.getPassword().equals(form.getConfirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "mismatch", "Passwords do not match.");
        }
        if (bindingResult.hasErrors()) {
            return registrationErrorPage(role, bindingResult, csrfCookie, model);
        }
        try {
            Long id = registrationRequestService.submit(role, form);
            SecurityAudit.event("registration.requested", "requestId=" + id + " role=" + role.name());
            return "redirect:/register/received/" + id;
        } catch (DuplicateUsernameException exception) {
            bindingResult.rejectValue("username", "duplicate", "That username has already been requested.");
            return registrationErrorPage(role, bindingResult, csrfCookie, model);
        }
    }

    private String registrationErrorPage(RegistrationRole role, BindingResult bindingResult,
                                         String csrfCookie, Model model) {
        model.addAttribute("accountType", role == RegistrationRole.CUSTOMER ? "customer" : "dispatcher");
        model.addAttribute("csrfToken", csrfCookie);
        List<String> errors = bindingResult.getAllErrors().stream()
                .map(error -> error.getDefaultMessage() == null ? "Please check your details." : error.getDefaultMessage())
                .toList();
        model.addAttribute("errors", errors);
        return "register";
    }
}
