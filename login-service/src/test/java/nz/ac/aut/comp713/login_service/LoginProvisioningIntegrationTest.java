package nz.ac.aut.comp713.login_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import nz.ac.aut.comp713.login_service.model.AccountRole;
import nz.ac.aut.comp713.login_service.model.RegistrationStatus;
import nz.ac.aut.comp713.login_service.repository.AccountRepository;
import nz.ac.aut.comp713.login_service.repository.LoginSessionRepository;
import nz.ac.aut.comp713.login_service.repository.RegistrationRequestRepository;
import nz.ac.aut.comp713.login_service.service.AuthCookies;
import nz.ac.aut.comp713.login_service.service.SessionService;

@SpringBootTest(properties = {"debug=false", "logging.level.root=ERROR",
        "login.customer-provisioning-secret=test-customer-secret-0123456789abcdef0123456789abcdef",
        "login.dispatcher-provisioning-secret=test-dispatcher-secret-0123456789abcdef0123456789abcdef"})
@AutoConfigureMockMvc
class LoginProvisioningIntegrationTest {

    private static final String CUSTOMER_SECRET = "test-customer-secret-0123456789abcdef0123456789abcdef";
    private static final Path DATABASE = temporaryDatabase();
    private static final AtomicBoolean CUSTOMER_ACTIVE = new AtomicBoolean();
    private static final HttpServer DOMAIN = startDomain();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DATABASE);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("login.customer-service-url", () -> "http://localhost:" + DOMAIN.getAddress().getPort());
        registry.add("login.dispatcher-service-url", () -> "http://localhost:" + DOMAIN.getAddress().getPort());
    }

    @Autowired MockMvc mvc;
    @Autowired AccountRepository accounts;
    @Autowired LoginSessionRepository sessionRows;
    @Autowired RegistrationRequestRepository requests;
    @Autowired SessionService sessions;

    @BeforeEach
    void reset() {
        CUSTOMER_ACTIVE.set(false);
        sessionRows.deleteAll();
        requests.deleteAll();
        accounts.deleteAll(accounts.findAll().stream()
                .filter(account -> account.getRole() != AccountRole.ADMIN).toList());
    }

    @AfterAll
    static void stopDomain() {
        DOMAIN.stop(0);
    }

    @Test
    void registrationCreatesInactiveLinkedAccountAndAdminControlsActivation() throws Exception {
        MvcResult registrationPage = mvc.perform(get("/register/customer"))
                .andExpect(status().isOk()).andReturn();
        String registrationCsrf = cookieValue(registrationPage, AuthCookies.CSRF);
        mvc.perform(post("/register/customer")
                        .cookie(new Cookie(AuthCookies.CSRF, registrationCsrf))
                        .param("csrf", registrationCsrf)
                        .param("username", "pending_customer")
                        .param("password", "strong-password-123")
                        .param("confirmPassword", "strong-password-123")
                        .param("name", "Island Foods")
                        .param("address", "12 Market Street, Auckland"))
                .andExpect(status().is3xxRedirection());

        var account = accounts.findByNormalizedUsername("pending_customer").orElseThrow();
        assertThat(account.isActive()).isFalse();
        assertThat(account.getProfileId()).isEqualTo(11L);
        assertThat(requests.findAll().getFirst().getStatus()).isEqualTo(RegistrationStatus.PROVISIONED);
        assertThat(CUSTOMER_ACTIVE.get()).isFalse();

        String adminToken = login("admin", "admin1234!");
        MvcResult adminPage = mvc.perform(get("/admin/accounts")
                .cookie(new Cookie(AuthCookies.SESSION, adminToken))).andExpect(status().isOk()).andReturn();
        assertThat(adminPage.getResponse().getContentAsString())
                .contains("Pending requests <span>1</span>", "Awaiting approval");
        String adminCsrf = cookieValue(adminPage, AuthCookies.CSRF);
        mvc.perform(post("/admin/accounts/{id}/active", account.getId())
                        .cookie(new Cookie(AuthCookies.SESSION, adminToken), new Cookie(AuthCookies.CSRF, adminCsrf))
                        .param("csrf", adminCsrf).param("active", "true"))
                .andExpect(status().is3xxRedirection());
        assertThat(accounts.findById(account.getId()).orElseThrow().isActive()).isTrue();
        assertThat(CUSTOMER_ACTIVE.get()).isTrue();
        String approvedPage = mvc.perform(get("/admin/accounts")
                        .cookie(new Cookie(AuthCookies.SESSION, adminToken)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(approvedPage)
                .contains("Pending requests <span>0</span>", "No pending account requests.", "Active")
                .doesNotContain("Awaiting approval");

        String customerToken = login("pending_customer", "strong-password-123");
        assertThat(sessions.validate(customerToken)).isPresent();
        mvc.perform(post("/admin/accounts/{id}/active", account.getId())
                        .cookie(new Cookie(AuthCookies.SESSION, adminToken), new Cookie(AuthCookies.CSRF, adminCsrf))
                        .param("csrf", adminCsrf).param("active", "false"))
                .andExpect(status().is3xxRedirection());
        assertThat(accounts.findById(account.getId()).orElseThrow().isActive()).isFalse();
        assertThat(CUSTOMER_ACTIVE.get()).isFalse();
        assertThat(sessions.validate(customerToken)).isEmpty();
        String disabledPage = mvc.perform(get("/admin/accounts")
                        .cookie(new Cookie(AuthCookies.SESSION, adminToken)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(disabledPage).contains("Pending requests <span>1</span>", "Awaiting approval");
    }

    private String login(String username, String password) throws Exception {
        MvcResult page = mvc.perform(get("/login")).andExpect(status().isOk()).andReturn();
        String csrf = cookieValue(page, AuthCookies.CSRF);
        MvcResult result = mvc.perform(post("/login")
                        .cookie(new Cookie(AuthCookies.CSRF, csrf))
                        .param("csrf", csrf).param("username", username).param("password", password))
                .andExpect(status().is3xxRedirection()).andReturn();
        return cookieValue(result, AuthCookies.SESSION);
    }

    private String cookieValue(MvcResult result, String name) {
        String header = result.getResponse().getHeaders("Set-Cookie").stream()
                .filter(value -> value.startsWith(name + "="))
                .findFirst().orElseThrow();
        return header.substring(header.indexOf('=') + 1, header.indexOf(';'));
    }

    private static HttpServer startDomain() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/internal/v1/customers", LoginProvisioningIntegrationTest::handleCustomer);
            server.start();
            return server;
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void handleCustomer(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        boolean trusted = CUSTOMER_SECRET.equals(exchange.getRequestHeaders().getFirst("X-Login-Provisioning-Key"));
        if (!trusted) {
            respond(exchange, 403, "{}");
        } else if ("/internal/v1/customers".equals(path) && "POST".equals(exchange.getRequestMethod())) {
            if (exchange.getRequestHeaders().getFirst("X-Registration-Request-Id") == null) {
                respond(exchange, 400, "{}");
            } else {
                respond(exchange, 201, "{\"id\":11,\"active\":false}");
            }
        } else if ("/internal/v1/customers/11/active".equals(path)
                && "PUT".equals(exchange.getRequestMethod())) {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            boolean active = body.contains("true");
            CUSTOMER_ACTIVE.set(active);
            respond(exchange, 200, "{\"id\":11,\"active\":" + active + "}");
        } else {
            respond(exchange, 404, "{}");
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var response = exchange.getResponseBody()) {
            response.write(bytes);
        }
    }

    private static Path temporaryDatabase() {
        try {
            return Files.createTempFile("taro-login-provisioning-test-", ".db");
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
