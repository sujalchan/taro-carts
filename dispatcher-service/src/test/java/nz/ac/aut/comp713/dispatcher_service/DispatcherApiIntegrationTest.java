package nz.ac.aut.comp713.dispatcher_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import nz.ac.aut.comp713.dispatcher_service.repository.DispatcherRepository;

@SpringBootTest
@AutoConfigureMockMvc
class DispatcherApiIntegrationTest {

    private static final String SECRET = "test-dispatcher-secret-0123456789abcdef0123456789abcdef";
    private static final String VALID = """
            {"name":"Auckland Dispatch","contactName":"Hana",
             "phone":"+64211234567","address":"20 Example Road, Auckland"}
            """;

    @Autowired MockMvc mvc;
    @Autowired DispatcherRepository dispatchers;

    @BeforeEach
    void clearDispatchers() {
        dispatchers.deleteAll();
    }

    @Test
    void missingBrowserPageUsesBrandedError() throws Exception {
        mvc.perform(get("/missing-page").accept(MediaType.TEXT_HTML))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("Page not found")))
                .andExpect(content().string(not(containsString("Whitelabel Error Page"))));
    }

    @Test
    void publicCreateRoutesAndPagesAreGone() throws Exception {
        mvc.perform(post("/api/v1/dispatchers").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(get("/dispatchers/new")).andExpect(status().isNotFound());
        mvc.perform(post("/dispatchers/new").param("name", "Forged"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/dispatcher-new-client.html")).andExpect(status().isNotFound());
        mvc.perform(get("/dispatchers")).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Create Dispatcher"))));
        mvc.perform(get("/dispatchers-client.html")).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Add Dispatcher"))));
        assertThat(dispatchers.count()).isZero();
    }

    @Test
    void secretAndLoopbackAreRequired() throws Exception {
        mvc.perform(post("/internal/v1/dispatchers").header("X-Registration-Request-Id", "1")
                .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isForbidden());
        mvc.perform(post("/internal/v1/dispatchers").header("X-Registration-Request-Id", "1")
                .header("X-Login-Provisioning-Key", "wrong")
                .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isForbidden());
        mvc.perform(post("/internal/v1/dispatchers").header("X-Registration-Request-Id", "1")
                .header("X-Login-Provisioning-Key", SECRET)
                .with(request -> { request.setRemoteAddr("203.0.113.10"); return request; })
                .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isForbidden());
        assertThat(dispatchers.count()).isZero();
    }

    @Test
    void provisioningIgnoresForgedActiveAndActivationIsPrivate() throws Exception {
        mvc.perform(post("/internal/v1/dispatchers").header("X-Registration-Request-Id", "1")
                .header("X-Login-Provisioning-Key", SECRET)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Auckland Dispatch","address":"20 Example Road, Auckland","active":true}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(false));
        Long id = dispatchers.findAll().getFirst().getId();
        mvc.perform(put("/api/v1/dispatchers/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Auckland Dispatch","address":"20 Example Road, Auckland","active":true}
                        """))
                .andExpect(status().isBadRequest());
        assertThat(dispatchers.findById(id).orElseThrow().isActive()).isFalse();
        mvc.perform(put("/internal/v1/dispatchers/{id}/active", id)
                .header("X-Login-Provisioning-Key", SECRET)
                .contentType(MediaType.APPLICATION_JSON).content("{\"active\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void readSearchUpdateAndDuplicateChecksStillWork() throws Exception {
        provision(VALID);
        Long id = dispatchers.findAll().getFirst().getId();
        mvc.perform(get("/api/v1/dispatchers/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Auckland Dispatch"));
        mvc.perform(get("/api/v1/dispatchers").param("search", "Example Road"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(put("/api/v1/dispatchers/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Auckland Dispatch","address":"22 Example Road, Auckland"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(post("/internal/v1/dispatchers").header("X-Registration-Request-Id", "2")
                .header("X-Login-Provisioning-Key", SECRET)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"aucklanddispatch","address":"24 Example Road, Auckland"}
                        """))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidDataAndUnknownProfileAreRejected() throws Exception {
        mvc.perform(post("/internal/v1/dispatchers").header("X-Registration-Request-Id", "1")
                .header("X-Login-Provisioning-Key", SECRET)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Auckland Dispatch\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/dispatchers/999")).andExpect(status().isNotFound());
    }

    @Test
    void retryingTheSameRegistrationReturnsTheSameProfile() throws Exception {
        provision(VALID);
        Long firstId = dispatchers.findAll().getFirst().getId();
        mvc.perform(post("/internal/v1/dispatchers").header("X-Registration-Request-Id", "1")
                .header("X-Login-Provisioning-Key", SECRET)
                .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(firstId));
        assertThat(dispatchers.count()).isEqualTo(1);
    }

    private void provision(String body) throws Exception {
        mvc.perform(post("/internal/v1/dispatchers").header("X-Registration-Request-Id", "1")
                .header("X-Login-Provisioning-Key", SECRET)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }
}
