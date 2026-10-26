package nz.ac.aut.comp713.allocation_service.security;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Refuse to disable HTTP security in a packaged/runtime deployment. */
@Component
@Profile("legacy-tests")
class LegacyTestProfileGuard {
    LegacyTestProfileGuard() {
        if (getClass().getClassLoader().getResource("security-legacy-test.marker") == null) {
            throw new IllegalStateException("The legacy-tests profile is only available on the test classpath");
        }
    }
}

