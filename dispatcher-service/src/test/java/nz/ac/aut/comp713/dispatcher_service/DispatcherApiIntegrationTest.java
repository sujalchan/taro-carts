package nz.ac.aut.comp713.dispatcher_service;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import nz.ac.aut.comp713.dispatcher_service.repository.DispatcherRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestAddressValidationConfiguration.class)
class DispatcherApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DispatcherRepository dispatcherRepository;

    @BeforeEach
    void clearDispatchers() {
        dispatcherRepository.deleteAll();
    }

    @Test
    void clientPagesAreServed() throws Exception {
        mockMvc.perform(get("/dispatchers-client.html")).andExpect(status().isOk());
        mockMvc.perform(get("/dispatcher-new-client.html")).andExpect(status().isOk());
        mockMvc.perform(get("/dispatcher-edit-client.html")).andExpect(status().isOk());
    }

    @Test
    void listStartsEmpty() throws Exception {
        mockMvc.perform(get("/api/v1/dispatchers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void createDispatcherReturnsCreatedProfile() throws Exception {
        mockMvc.perform(post("/api/v1/dispatchers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(dispatcherJson("Auckland Dispatch", "Hana", "+64211234567", "20 Example Road, Auckland")))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Auckland Dispatch"))
                .andExpect(jsonPath("$.contactName").value("Hana"))
                .andExpect(jsonPath("$.phone").value("+64211234567"))
                .andExpect(jsonPath("$.address").value("20 Example Road, Auckland"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void activeStateDefaultsToTrueAndCanBeUpdated() throws Exception {
        mockMvc.perform(post("/api/v1/dispatchers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(dispatcherJson("Auckland Dispatch", "Hana", "+64211234567", "20 Example Road, Auckland", false)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(false));

        Long id = dispatcherRepository.findAll().getFirst().getId();

        mockMvc.perform(get("/dispatchers/{id}/edit", id))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("id=\"active\" name=\"active\" value=\"true\"")));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                "/api/v1/dispatchers/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(dispatcherJson("Auckland Dispatch", "Hana", "+64211234567", "20 Example Road, Auckland")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                "/api/v1/dispatchers/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(dispatcherJson("Auckland Dispatch", "Hana", "+64211234567", "20 Example Road, Auckland", true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void getByIdAndSearchReturnCreatedDispatcher() throws Exception {
        mockMvc.perform(post("/api/v1/dispatchers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(dispatcherJson("Auckland Dispatch", "Hana", "+64211234567", "20 Example Road, Auckland")))
                .andExpect(status().isCreated());

        Long id = dispatcherRepository.findAll().getFirst().getId();

        mockMvc.perform(get("/api/v1/dispatchers/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Auckland Dispatch"));

        mockMvc.perform(get("/api/v1/dispatchers").param("search", "Example Road"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].address").value("20 Example Road, Auckland"));
    }

    @Test
    void missingAddressIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/dispatchers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Auckland Dispatch\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Dispatcher address is required"));
    }

    @Test
    void invalidPhoneNumberIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/dispatchers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(dispatcherJson("Auckland Dispatch", "Hana", "12AB", "20 Example Road, Auckland")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Phone number must contain 7 to 15 digits"));
    }

    @Test
    void duplicateNamesAreCaseAndWhitespaceInsensitive() throws Exception {
        mockMvc.perform(post("/api/v1/dispatchers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(dispatcherJson("Auckland Dispatch", null, null, "20 Example Road, Auckland")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/dispatchers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(dispatcherJson("aucklanddispatch", null, null, "22 Example Road, Auckland")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DISPATCHER_ALREADY_EXISTS"));
    }

    @Test
    void unknownDispatcherReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/dispatchers/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DISPATCHER_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/v1/dispatchers/999"));
    }

    private String dispatcherJson(String name, String contactName, String phone, String address) {
        return dispatcherJson(name, contactName, phone, address, null);
    }

    private String dispatcherJson(String name, String contactName, String phone, String address, Boolean active) {
        return """
                {"name":"%s","contactName":%s,"phone":%s,"address":"%s"%s}
                """.formatted(name, jsonValue(contactName), jsonValue(phone), address,
                        active == null ? "" : ",\"active\":" + active);
    }

    private String jsonValue(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }
}
