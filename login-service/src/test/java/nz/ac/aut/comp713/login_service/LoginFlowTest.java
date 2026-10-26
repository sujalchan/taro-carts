package nz.ac.aut.comp713.login_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import nz.ac.aut.comp713.login_service.model.Account;
import nz.ac.aut.comp713.login_service.model.AccountRole;
import nz.ac.aut.comp713.login_service.repository.AccountRepository;
import nz.ac.aut.comp713.login_service.repository.LoginSessionRepository;
import nz.ac.aut.comp713.login_service.repository.RegistrationRequestRepository;
import nz.ac.aut.comp713.login_service.service.AccountSeeder;
import nz.ac.aut.comp713.login_service.service.AuthCookies;

@SpringBootTest(properties = {"debug=false", "logging.level.root=ERROR", "logging.level.org.hibernate.SQL=OFF",
        "login.customer-provisioning-secret=test-customer-secret-0123456789abcdef0123456789abcdef",
        "login.dispatcher-provisioning-secret=test-dispatcher-secret-0123456789abcdef0123456789abcdef",
        "login.customer-service-url=http://localhost:19081",
        "login.dispatcher-service-url=http://localhost:19084",
        "login.provisioning.connect-timeout=PT0.2S", "login.provisioning.read-timeout=PT0.2S"})
@AutoConfigureMockMvc
class LoginFlowTest {

    private static final Path DATABASE = temporaryDatabase();

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DATABASE);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired MockMvc mvc;
    @Autowired AccountRepository accounts;
    @Autowired LoginSessionRepository sessions;
    @Autowired RegistrationRequestRepository requests;
    @Autowired BCryptPasswordEncoder passwords;
    @Autowired AccountSeeder seeder;

    @BeforeEach
    void cleanUp() {
        sessions.deleteAll();
        requests.deleteAll();
        accounts.deleteAll(accounts.findAll().stream()
                .filter(account -> account.getRole() != AccountRole.ADMIN).toList());
        Account admin = accounts.findByNormalizedUsername("admin").orElseThrow();
        admin.changePassword(passwords.encode("admin1234!"), Instant.now());
        accounts.save(admin);
    }

    @Test
    void missingBrowserPageUsesBrandedError() throws Exception {
        mvc.perform(get("/missing-page").accept(MediaType.TEXT_HTML))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("Page not found")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Whitelabel Error Page"))));
    }

    @Test
    void adminSeederIsIdempotentAndPasswordIsHashed() throws Exception {
        Account original = accounts.findByNormalizedUsername("admin").orElseThrow();
        String storedHash = original.getPasswordHash();
        assertThat(original.isActive()).isTrue();
        assertThat(storedHash).isNotEqualTo("admin1234!");
        assertThat(passwords.matches("admin1234!", storedHash)).isTrue();

        ApplicationArguments arguments = new DefaultApplicationArguments(new String[0]);
        seeder.run(arguments);
        assertThat(accounts.count()).isEqualTo(1);
        assertThat(accounts.findByNormalizedUsername("admin").orElseThrow().getPasswordHash())
                .isEqualTo(storedHash);
    }

    @Test
    void mobileLoginReturnsOpaqueSessionForActiveAccountAndLogoutRevokesIt() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/mobile/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin1234!\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andReturn();
        String token = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.sessionToken");
        assertThat(token).hasSize(43);
        assertThat(sessions.findAll()).hasSize(1);
        assertThat(sessions.findAll().getFirst().getTokenHash()).isNotEqualTo(token);

        mvc.perform(post("/api/v1/mobile/logout").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionToken\":\"" + token + "\"}"))
                .andExpect(status().isNoContent());
        assertThat(sessions.findAll().getFirst().getRevokedAt()).isNotNull();
    }

    @Test
    void mobileLoginExplainsAdministratorActivationAfterCorrectPassword() throws Exception {
        accounts.save(new Account("pending-mobile", "pending-mobile", passwords.encode("validpassword123"),
                AccountRole.CUSTOMER, 7L, false));

        mvc.perform(post("/api/v1/mobile/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"pending-mobile\",\"password\":\"validpassword123\"}"))
                .andExpect(status().isForbidden())
                .andExpect(content().string(containsString(
                        "Your account is waiting for administrator activation.")));
    }

    @Test
    void mobileRegistrationCreatesPendingCustomerRequestAndRejectsPasswordMismatch() throws Exception {
        mvc.perform(post("/api/v1/mobile/register/customer").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"mobile-customer","password":"strong-password-123",
                                 "confirmPassword":"strong-password-123","name":"Mobile Customer",
                                 "contactName":"Contact","phone":"+64211234567","address":"1 Test Road"}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"));
        assertThat(requests.existsByNormalizedUsername("mobile-customer")).isTrue();
        assertThat(accounts.findByNormalizedUsername("mobile-customer")).isEmpty();

        mvc.perform(post("/api/v1/mobile/register/dispatcher").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"mobile-driver","password":"strong-password-123",
                                 "confirmPassword":"different-password","name":"Mobile Driver",
                                 "phone":"+64211234567","address":"1 Test Road"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminLoginStoresOnlyTokenHashAndLogoutRevokesIt() throws Exception {
        String csrf = csrfFromLoginPage();
        MvcResult login = mvc.perform(post("/login")
                        .cookie(new Cookie(AuthCookies.CSRF, csrf))
                        .param("csrf", csrf)
                        .param("username", "ADMIN")
                        .param("password", "admin1234!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/admin/accounts"))
                .andReturn();

        String sessionCookie = cookieHeader(login, AuthCookies.SESSION);
        String token = cookieValue(sessionCookie);
        assertThat(sessionCookie).contains("HttpOnly", "SameSite=Lax", "Path=/");
        assertThat(token).hasSize(43);
        assertThat(sessions.count()).isEqualTo(1);
        assertThat(sessions.findAll().getFirst().getTokenHash()).isNotEqualTo(token).hasSize(64);

        mvc.perform(get("/admin/accounts").cookie(new Cookie(AuthCookies.SESSION, token)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Account requests")));

        MvcResult adminPage = mvc.perform(get("/admin/accounts")
                        .cookie(new Cookie(AuthCookies.SESSION, token)))
                .andReturn();
        String logoutCsrf = cookieValue(cookieHeader(adminPage, AuthCookies.CSRF));
        mvc.perform(post("/logout")
                        .cookie(new Cookie(AuthCookies.SESSION, token), new Cookie(AuthCookies.CSRF, logoutCsrf))
                        .param("csrf", logoutCsrf))
                .andExpect(status().is3xxRedirection());
        assertThat(sessions.findAll().getFirst().getRevokedAt()).isNotNull();
        mvc.perform(get("/admin/accounts").cookie(new Cookie(AuthCookies.SESSION, token)))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void signedInAccountWithoutAProfileCanReturnToLoginAndSignOut() throws Exception {
        accounts.save(new Account("orphan", "orphan", passwords.encode("validpassword123"),
                AccountRole.CUSTOMER, 99999L, true));
        String csrf = csrfFromLoginPage();
        MvcResult login = mvc.perform(post("/login")
                        .cookie(new Cookie(AuthCookies.CSRF, csrf))
                        .param("csrf", csrf)
                        .param("username", "orphan")
                        .param("password", "validpassword123"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String token = cookieValue(cookieHeader(login, AuthCookies.SESSION));

        MvcResult loginPage = mvc.perform(get("/login")
                        .cookie(new Cookie(AuthCookies.SESSION, token)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("You are signed in as")))
                .andExpect(content().string(containsString("orphan")))
                .andExpect(content().string(containsString("Sign out")))
                .andReturn();
        String logoutCsrf = cookieValue(cookieHeader(loginPage, AuthCookies.CSRF));
        mvc.perform(post("/login")
                        .cookie(new Cookie(AuthCookies.SESSION, token),
                                new Cookie(AuthCookies.CSRF, logoutCsrf))
                        .param("csrf", logoutCsrf)
                        .param("username", "admin")
                        .param("password", "incorrect"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sign out")));

        mvc.perform(post("/logout")
                        .cookie(new Cookie(AuthCookies.SESSION, token),
                                new Cookie(AuthCookies.CSRF, logoutCsrf))
                        .param("csrf", logoutCsrf))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/login"));
        mvc.perform(get("/login").cookie(new Cookie(AuthCookies.SESSION, token)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("You are signed in as"))));
    }

    @Test
    void signOutPageConfirmsAccountAndRevokesSession() throws Exception {
        String csrf = csrfFromLoginPage();
        MvcResult login = mvc.perform(post("/login")
                        .cookie(new Cookie(AuthCookies.CSRF, csrf))
                        .param("csrf", csrf).param("username", "admin").param("password", "admin1234!"))
                .andExpect(status().is3xxRedirection()).andReturn();
        String token = cookieValue(cookieHeader(login, AuthCookies.SESSION));
        MvcResult page = mvc.perform(get("/logout").cookie(new Cookie(AuthCookies.SESSION, token)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sign out?")))
                .andExpect(content().string(containsString("admin")))
                .andReturn();
        String logoutCsrf = cookieValue(cookieHeader(page, AuthCookies.CSRF));
        mvc.perform(post("/logout")
                        .cookie(new Cookie(AuthCookies.SESSION, token), new Cookie(AuthCookies.CSRF, logoutCsrf))
                        .param("csrf", logoutCsrf))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/logout").cookie(new Cookie(AuthCookies.SESSION, token)))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/login"));
    }

    @Test
    void introspectionRequiresServiceCredentialAndReturnsMinimumIdentity() throws Exception {
        String csrf = csrfFromLoginPage();
        MvcResult login = mvc.perform(post("/login")
                        .cookie(new Cookie(AuthCookies.CSRF, csrf))
                        .param("csrf", csrf).param("username", "admin").param("password", "admin1234!"))
                .andExpect(status().is3xxRedirection()).andReturn();
        String token = cookieValue(cookieHeader(login, AuthCookies.SESSION));
        String body = "{\"token\":\"" + token + "\"}";

        mvc.perform(post("/internal/v1/sessions/introspect")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/internal/v1/sessions/introspect")
                        .header("X-Service-Validation-Key", "wrong")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/internal/v1/sessions/introspect")
                        .with(request -> { request.setRemoteAddr("192.0.2.10"); return request; })
                        .header("X-Service-Validation-Key", "test-customer-validation-secret-0123456789abcdef0123456789abcdef")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        MvcResult validated = mvc.perform(post("/internal/v1/sessions/introspect")
                        .header("X-Service-Validation-Key", "test-customer-validation-secret-0123456789abcdef0123456789abcdef")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        assertThat(validated.getResponse().getContentAsString())
                .contains("\"username\":\"admin\"", "\"role\":\"ADMIN\"", "\"active\":true")
                .doesNotContain("passwordHash", "tokenHash", token);
        mvc.perform(post("/internal/v1/sessions/introspect")
                        .header("X-Service-Validation-Key", "test-customer-validation-secret-0123456789abcdef0123456789abcdef")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"invalid\"}"))
                .andExpect(status().isUnauthorized());
        var storedSession = sessions.findAll().getFirst();
        storedSession.revokeAt(Instant.now());
        sessions.save(storedSession);
        mvc.perform(post("/internal/v1/sessions/introspect")
                        .header("X-Service-Validation-Key", "test-customer-validation-secret-0123456789abcdef0123456789abcdef")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void badCredentialsAndInactiveAccountCannotSignIn() throws Exception {
        accounts.save(new Account("pending", "pending", passwords.encode("validpassword123"),
                AccountRole.CUSTOMER, 7L, false));
        for (String username : List.of("unknown", "admin")) {
            String csrf = csrfFromLoginPage();
            mvc.perform(post("/login")
                            .cookie(new Cookie(AuthCookies.CSRF, csrf))
                            .param("csrf", csrf)
                            .param("username", username)
                            .param("password", "incorrect"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Sign-in failed. Check your details or account status and try again.")));
        }
        String csrf = csrfFromLoginPage();
        mvc.perform(post("/login")
                        .cookie(new Cookie(AuthCookies.CSRF, csrf))
                        .param("csrf", csrf)
                        .param("username", "pending")
                        .param("password", "validpassword123"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "Your password is correct, but your account is waiting for administrator activation.")));
        assertThat(sessions.count()).isZero();
    }

    @Test
    void forgedReturnUrlAndMissingCsrfAreRejected() throws Exception {
        String csrf = csrfFromLoginPage();
        mvc.perform(post("/login").param("username", "admin").param("password", "admin1234!"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/login")
                        .cookie(new Cookie(AuthCookies.CSRF, csrf))
                        .param("csrf", csrf)
                        .param("username", "admin")
                        .param("password", "admin1234!")
                        .param("returnTo", "https://example.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/admin/accounts"));
    }

    @Test
    void changingAdminPasswordRevokesSessions() throws Exception {
        String csrf = csrfFromLoginPage();
        MvcResult login = mvc.perform(post("/login")
                        .cookie(new Cookie(AuthCookies.CSRF, csrf))
                        .param("csrf", csrf)
                        .param("username", "admin")
                        .param("password", "admin1234!"))
                .andExpect(status().is3xxRedirection()).andReturn();
        String token = cookieValue(cookieHeader(login, AuthCookies.SESSION));
        MvcResult adminPage = mvc.perform(get("/admin/accounts")
                .cookie(new Cookie(AuthCookies.SESSION, token))).andReturn();
        String changeCsrf = cookieValue(cookieHeader(adminPage, AuthCookies.CSRF));

        mvc.perform(post("/admin/password")
                        .cookie(new Cookie(AuthCookies.SESSION, token), new Cookie(AuthCookies.CSRF, changeCsrf))
                        .param("csrf", changeCsrf)
                        .param("currentPassword", "admin1234!")
                        .param("newPassword", "new-admin-password-123")
                        .param("confirmPassword", "new-admin-password-123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/login?passwordChanged=1"));

        assertThat(sessions.findAll().getFirst().getRevokedAt()).isNotNull();
        assertThat(passwords.matches("new-admin-password-123",
                accounts.findByNormalizedUsername("admin").orElseThrow().getPasswordHash())).isTrue();
    }

    @Test
    void registrationRequiresCsrfAndRemainsPending() throws Exception {
        MvcResult page = mvc.perform(get("/register/customer"))
                .andExpect(status().isOk()).andReturn();
        String csrf = cookieValue(cookieHeader(page, AuthCookies.CSRF));
        mvc.perform(post("/register/customer")
                        .param("username", "newcustomer")
                        .param("password", "validpassword123")
                        .param("confirmPassword", "validpassword123")
                        .param("name", "Island Foods")
                        .param("address", "12 Market Street, Auckland"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/register/customer")
                        .cookie(new Cookie(AuthCookies.CSRF, csrf))
                        .param("csrf", csrf)
                        .param("username", "newcustomer")
                        .param("password", "validpassword123")
                        .param("confirmPassword", "validpassword123")
                        .param("name", "Island Foods")
                        .param("address", "12 Market Street, Auckland"))
                .andExpect(status().is3xxRedirection());
        assertThat(requests.count()).isEqualTo(1);
        assertThat(accounts.existsByNormalizedUsername("newcustomer")).isFalse();

        String loginCsrf = csrfFromLoginPage();
        MvcResult login = mvc.perform(post("/login")
                        .cookie(new Cookie(AuthCookies.CSRF, loginCsrf))
                        .param("csrf", loginCsrf)
                        .param("username", "admin")
                        .param("password", "admin1234!"))
                .andExpect(status().is3xxRedirection()).andReturn();
        String adminToken = cookieValue(cookieHeader(login, AuthCookies.SESSION));
        mvc.perform(get("/admin/accounts").cookie(new Cookie(AuthCookies.SESSION, adminToken)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("newcustomer")));
    }

    private String csrfFromLoginPage() throws Exception {
        MvcResult page = mvc.perform(get("/login")).andExpect(status().isOk()).andReturn();
        return cookieValue(cookieHeader(page, AuthCookies.CSRF));
    }

    private String cookieHeader(MvcResult result, String name) {
        return result.getResponse().getHeaders("Set-Cookie").stream()
                .filter(header -> header.startsWith(name + "="))
                .findFirst().orElseThrow();
    }

    private String cookieValue(String header) {
        return header.substring(header.indexOf('=') + 1, header.indexOf(';'));
    }

    private static Path temporaryDatabase() {
        try {
            return Files.createTempFile("taro-login-test-", ".db");
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
