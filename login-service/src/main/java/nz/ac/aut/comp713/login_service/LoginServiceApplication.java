package nz.ac.aut.comp713.login_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Boots the login service and enables scheduled session and limiter cleanup.
 */
@SpringBootApplication
@EnableScheduling
public class LoginServiceApplication {

    /** Uses BCrypt for persisted account and pending-registration passwords. */
    @Bean
    BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Starts the embedded server and initializes the configured persistence layer.
     */
    public static void main(String[] args) {
        SpringApplication.run(LoginServiceApplication.class, args);
    }
}
