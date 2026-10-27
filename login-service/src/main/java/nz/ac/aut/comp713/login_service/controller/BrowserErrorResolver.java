package nz.ac.aut.comp713.login_service.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Cookie;
import nz.ac.aut.comp713.login_service.service.AuthCookies;
import nz.ac.aut.comp713.login_service.service.LoginDestinations;
import nz.ac.aut.comp713.login_service.service.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.AnnotationUtils;

/** Renders a safe HTML error page for browser requests while leaving API errors to the JSON advice. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class BrowserErrorResolver implements HandlerExceptionResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(BrowserErrorResolver.class);
    private final SessionService sessions;
    private final LoginDestinations destinations;

    public BrowserErrorResolver(SessionService sessions, LoginDestinations destinations) {
        this.sessions = sessions;
        this.destinations = destinations;
    }

    @Override
    public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response,
            Object handler, Exception exception) {
        String path = request.getRequestURI();
        String accept = request.getHeader("Accept");
        if (accept == null || !accept.contains("text/html")
                || path.startsWith("/api/") || path.startsWith("/internal/")
                || path.matches(".*\\.(js|css|png|svg|ico|map)$")) return null;

        int status = HttpStatus.INTERNAL_SERVER_ERROR.value();
        if (exception instanceof ErrorResponse error) {
            status = error.getStatusCode().value();
        } else {
            ResponseStatus annotation = AnnotationUtils.findAnnotation(exception.getClass(), ResponseStatus.class);
            if (annotation != null) status = annotation.code().value();
        }
        if (status >= 500) LOGGER.error("Browser request failed: {}", path, exception);
        response.setStatus(status);
        response.setHeader("Cache-Control", "no-store");
        ModelAndView page = new ModelAndView("error");
        page.addObject("status", status);
        String token = null;
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (AuthCookies.SESSION.equals(cookie.getName())) {
                    token = cookie.getValue();
                    break;
                }
            }
        }
        try {
            var existing = sessions.validate(token);
            page.addObject("signedInRole", existing.map(session -> session.getAccount().getRole().name()).orElse(null));
            page.addObject("accountHomeUrl", existing.map(session ->
                    destinations.destination(session.getAccount().getRole(), null)).orElse("/login"));
        } catch (RuntimeException navigationFailure) {
            page.addObject("accountHomeUrl", "/login");
        }
        return page;
    }
}
