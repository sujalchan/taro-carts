package nz.ac.aut.comp713.login_service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import nz.ac.aut.comp713.login_service.service.LoginAttemptLimiter;

class LoginAttemptLimiterTest {

    @Test
    void repeatedFailuresBackOffOnlyTheMatchingAddressAndUsername() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter();
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThat(limiter.allowed("127.0.0.1", "admin")).isTrue();
            limiter.failed("127.0.0.1", "admin");
        }
        assertThat(limiter.allowed("127.0.0.1", "ADMIN")).isFalse();
        assertThat(limiter.allowed("127.0.0.1", "someone-else")).isTrue();
        assertThat(limiter.allowed("192.0.2.1", "admin")).isTrue();
    }
}
