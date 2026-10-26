package nz.ac.aut.comp713.login_service.controller;

import java.util.List;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import nz.ac.aut.comp713.login_service.model.RegistrationRole;
import nz.ac.aut.comp713.login_service.service.RegistrationRequestService;
import nz.ac.aut.comp713.login_service.service.RegistrationRequestService.DuplicateUsernameException;
import nz.ac.aut.comp713.login_service.web.RegistrationForm;

/** Public sign-in and account request pages. Sign-in is added with the session phase. */
@Controller
public class AccountPageController {

    private final RegistrationRequestService registrationRequestService;

    public AccountPageController(RegistrationRequestService registrationRequestService) {
        this.registrationRequestService = registrationRequestService;
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/login";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String register() {
        return "register-type";
    }

    @GetMapping("/register/customer")
    public String registerCustomer(Model model) {
        model.addAttribute("accountType", "customer");
        model.addAttribute("registration", new RegistrationForm());
        return "register";
    }

    @GetMapping("/register/dispatcher")
    public String registerDispatcher(Model model) {
        model.addAttribute("accountType", "dispatcher");
        model.addAttribute("registration", new RegistrationForm());
        return "register";
    }

    @PostMapping("/register/customer")
    public String requestCustomerAccount(@Valid @ModelAttribute("registration") RegistrationForm form,
                                         BindingResult bindingResult, Model model) {
        return submitRequest(RegistrationRole.CUSTOMER, form, bindingResult, model);
    }

    @PostMapping("/register/dispatcher")
    public String requestDispatcherAccount(@Valid @ModelAttribute("registration") RegistrationForm form,
                                           BindingResult bindingResult, Model model) {
        return submitRequest(RegistrationRole.DISPATCHER, form, bindingResult, model);
    }

    @GetMapping("/register/received/{id}")
    public String requestReceived(@PathVariable Long id, Model model) {
        if (!registrationRequestService.exists(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("requestId", id);
        return "request-received";
    }

    private String submitRequest(RegistrationRole role, RegistrationForm form,
                                 BindingResult bindingResult, Model model) {
        if (form.getPassword() != null && form.getConfirmPassword() != null
                && !form.getPassword().equals(form.getConfirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "mismatch", "Passwords do not match.");
        }
        if (bindingResult.hasErrors()) {
            return registrationErrorPage(role, bindingResult, model);
        }
        try {
            Long id = registrationRequestService.submit(role, form);
            return "redirect:/register/received/" + id;
        } catch (DuplicateUsernameException exception) {
            bindingResult.rejectValue("username", "duplicate", "That username has already been requested.");
            return registrationErrorPage(role, bindingResult, model);
        }
    }

    private String registrationErrorPage(RegistrationRole role, BindingResult bindingResult, Model model) {
        model.addAttribute("accountType", role == RegistrationRole.CUSTOMER ? "customer" : "dispatcher");
        List<String> errors = bindingResult.getAllErrors().stream()
                .map(error -> error.getDefaultMessage() == null ? "Please check your details." : error.getDefaultMessage())
                .toList();
        model.addAttribute("errors", errors);
        return "register";
    }
}
