package nz.ac.aut.comp713.auth_service;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AccountPageController {
    private final AccountRepository accounts;
    private final AccountManagement management;

    public AccountPageController(AccountRepository accounts, AccountManagement management) {
        this.accounts = accounts;
        this.management = management;
    }

    @GetMapping("/accounts")
    public String accounts(Model model) {
        model.addAttribute("accounts", accounts.findAll());
        return "accounts";
    }

    @PostMapping("/accounts")
    public String create(@RequestParam String username, @RequestParam String password,
            @RequestParam String role, Model model) {
        try {
            management.create(username, password, role);
            return "redirect:/accounts";
        } catch (IllegalArgumentException exception) {
            model.addAttribute("error", exception.getMessage());
            model.addAttribute("accounts", accounts.findAll());
            model.addAttribute("username", username);
            model.addAttribute("role", role);
            return "accounts";
        }
    }
}
