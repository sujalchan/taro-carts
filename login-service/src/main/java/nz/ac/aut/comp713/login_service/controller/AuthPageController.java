package nz.ac.aut.comp713.login_service.controller;

import jakarta.validation.Validator;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import nz.ac.aut.comp713.login_service.dto.LoginRequest;
import nz.ac.aut.comp713.login_service.dto.RegisterRequest;
import nz.ac.aut.comp713.login_service.service.AuthSubmissionService;

@Controller
public class AuthPageController {

    private final AuthSubmissionService submissionService;
    private final Validator validator;

    public AuthPageController(AuthSubmissionService submissionService, Validator validator) {
        this.submissionService = submissionService;
        this.validator = validator;
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/login";
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @PostMapping("/login")
    public String login(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String password,
            Model model) {
        LoginRequest request = new LoginRequest(email, password);
        var violations = validator.validate(request);
        if (!violations.isEmpty()) {
            model.addAttribute("email", email);
            model.addAttribute("errors", violations.stream().map(v -> v.getMessage()).toList());
            return "login";
        }

        model.addAttribute("email", email);
        model.addAttribute("success", submissionService.receiveLogin(request).message());
        return "login";
    }

    @GetMapping("/register")
    public String registerPage() {
        return "register";
    }

    @PostMapping("/register")
    public String register(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String accountType,
            @RequestParam(required = false) String businessName,
            @RequestParam(required = false) String contactName,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String password,
            Model model) {
        RegisterRequest request = new RegisterRequest(
                name, email, accountType, businessName, contactName, phone, password);
        var violations = validator.validate(request);
        if (!violations.isEmpty()) {
            addRegistrationValues(model, name, email, accountType, businessName, contactName, phone);
            model.addAttribute("errors", violations.stream().map(v -> v.getMessage()).toList());
            return "register";
        }

        addRegistrationValues(model, name, email, accountType, businessName, contactName, phone);
        model.addAttribute("success", submissionService.receiveRegistration(request).message());
        return "register";
    }

    private void addRegistrationValues(
            Model model,
            String name,
            String email,
            String accountType,
            String businessName,
            String contactName,
            String phone) {
        model.addAttribute("name", name);
        model.addAttribute("email", email);
        model.addAttribute("accountType", accountType);
        model.addAttribute("businessName", businessName);
        model.addAttribute("contactName", contactName);
        model.addAttribute("phone", phone);
    }
}
