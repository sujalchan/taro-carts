package nz.ac.aut.comp713.auth_service;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginPageController {
    @GetMapping("/")
    public String home() {
        return "redirect:http://localhost:8082/allocations";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
