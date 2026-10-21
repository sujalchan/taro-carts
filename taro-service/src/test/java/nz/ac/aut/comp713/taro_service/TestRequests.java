package nz.ac.aut.comp713.taro_service;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

final class TestRequests {
    private TestRequests() {}

    static MockHttpServletRequestBuilder get(String url, Object... variables) {
        return authenticate(MockMvcRequestBuilders.get(url, variables));
    }

    static MockHttpServletRequestBuilder post(String url, Object... variables) {
        return authenticate(MockMvcRequestBuilders.post(url, variables));
    }

    static MockHttpServletRequestBuilder put(String url, Object... variables) {
        return authenticate(MockMvcRequestBuilders.put(url, variables));
    }

    static MockHttpServletRequestBuilder delete(String url, Object... variables) {
        return authenticate(MockMvcRequestBuilders.delete(url, variables));
    }

    private static MockHttpServletRequestBuilder authenticate(MockHttpServletRequestBuilder request) {
        return request.with(user("test-admin").roles("ADMIN")).with(csrf());
    }
}
