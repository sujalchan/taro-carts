package nz.ac.aut.comp713.customer_service.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Makes the validated CSRF token available to server-rendered forms. */
@ControllerAdvice
@Profile("!legacy-tests")
public class SecurityModelAdvice {
    @ModelAttribute("csrfToken")
    public String csrfToken(HttpServletRequest request) {
        Object token = request.getAttribute(SessionAuthenticationFilter.CSRF_ATTRIBUTE);
        return token == null ? "" : token.toString();
    }
}

