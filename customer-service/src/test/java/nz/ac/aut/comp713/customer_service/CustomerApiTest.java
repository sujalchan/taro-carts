package nz.ac.aut.comp713.customer_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
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

import nz.ac.aut.comp713.customer_service.repository.CustomerRepository;

@SpringBootTest
@AutoConfigureMockMvc
class CustomerApiTest {

    private static final String SECRET = "test-customer-secret-0123456789abcdef0123456789abcdef";
    private static final String VALID = """
            {"name":"Island Foods","address":"12 Market Street, Auckland",
             "contactName":"John","phone":"0211234567"}
            """;

    @Autowired MockMvc mvc;
    @Autowired CustomerRepository customers;

    @BeforeEach
    void resetDatabase() {
        customers.deleteAll();
    }

    @Test
    void publicCreateRoutesAndButtonsAreGone() throws Exception {
        mvc.perform(post("/api/v1/customers").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(get("/customers/new")).andExpect(status().isNotFound());
        mvc.perform(post("/customers/new").param("name", "Forged"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/customer-new-client.html")).andExpect(status().isNotFound());
        mvc.perform(get("/customers")).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Add Customer"))));
        mvc.perform(get("/customers-client.html")).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Add Customer"))));
        assertThat(customers.count()).isZero();
    }

    @Test
    void privateProvisioningRequiresCorrectSecretAndLoopback() throws Exception {
        mvc.perform(post("/internal/v1/customers").header("X-Registration-Request-Id", "1")
                .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isForbidden());
        mvc.perform(post("/internal/v1/customers").header("X-Registration-Request-Id", "1")
                .header("X-Login-Provisioning-Key", "wrong")
                .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isForbidden());
        mvc.perform(post("/internal/v1/customers").header("X-Registration-Request-Id", "1")
                .header("X-Login-Provisioning-Key", SECRET)
                .with(request -> { request.setRemoteAddr("203.0.113.10"); return request; })
                .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isForbidden());
        assertThat(customers.count()).isZero();
    }

    @Test
    void privateProvisioningForcesInactiveEvenWhenActiveIsForged() throws Exception {
        mvc.perform(post("/internal/v1/customers").header("X-Registration-Request-Id", "1")
                .header("X-Login-Provisioning-Key", SECRET)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Island Foods","address":"12 Market Street, Auckland","active":true}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(false));
        assertThat(customers.findAll().getFirst().isActive()).isFalse();
    }

    @Test
    void privateActivationWorksButPublicUpdateCannotToggleStatus() throws Exception {
        provision(VALID);
        Long id = customers.findAll().getFirst().getId();
        mvc.perform(put("/api/v1/customers/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Island Foods","address":"12 Market Street, Auckland","active":true}
                        """))
                .andExpect(status().isBadRequest());
        assertThat(customers.findById(id).orElseThrow().isActive()).isFalse();
        mvc.perform(put("/internal/v1/customers/{id}/active", id)
                .header("X-Login-Provisioning-Key", SECRET)
                .contentType(MediaType.APPLICATION_JSON).content("{\"active\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void readUpdateAndDuplicateChecksStillWork() throws Exception {
        provision(VALID);
        Long id = customers.findAll().getFirst().getId();
        mvc.perform(get("/api/v1/customers/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Island Foods"))
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(put("/api/v1/customers/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Island Foods","address":"44 Queen Street, Auckland","phone":"0219999999"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address").value("44 Queen Street, Auckland"))
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(post("/internal/v1/customers").header("X-Registration-Request-Id", "2")
                .header("X-Login-Provisioning-Key", SECRET)
                .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isConflict());
        assertThat(customers.count()).isEqualTo(1);
    }

    @Test
    void invalidInternalDataAndUnknownCustomerAreRejected() throws Exception {
        mvc.perform(post("/internal/v1/customers").header("X-Registration-Request-Id", "1")
                .header("X-Login-Provisioning-Key", SECRET)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Island Foods\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/customers/999")).andExpect(status().isNotFound());
    }

    @Test
    void retryingTheSameRegistrationReturnsTheSameProfile() throws Exception {
        provision(VALID);
        Long firstId = customers.findAll().getFirst().getId();
        mvc.perform(post("/internal/v1/customers").header("X-Registration-Request-Id", "1")
                .header("X-Login-Provisioning-Key", SECRET)
                .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(firstId));
        assertThat(customers.count()).isEqualTo(1);
    }

    private void provision(String body) throws Exception {
        mvc.perform(post("/internal/v1/customers").header("X-Registration-Request-Id", "1")
                .header("X-Login-Provisioning-Key", SECRET)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }
}
