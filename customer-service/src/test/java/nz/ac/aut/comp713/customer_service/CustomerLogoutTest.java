package nz.ac.aut.comp713.customer_service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class CustomerLogoutTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void logoutClearsEveryLocalServiceSession() throws Exception {
        List<String> setCookies = mockMvc.perform(post("/logout")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "http://localhost:8084/login?logout"))
                .andReturn().getResponse().getHeaders("Set-Cookie");

        for (String name : List.of("TARO_AUTH_SESSION", "TARO_CUSTOMER_SESSION",
                "TARO_TARO_SESSION", "TARO_ALLOCATION_SESSION")) {
            assertTrue(setCookies.stream().anyMatch(cookie ->
                    cookie.startsWith(name + "=") && cookie.contains("Max-Age=0")),
                    "Logout should expire " + name);
        }
    }
}
