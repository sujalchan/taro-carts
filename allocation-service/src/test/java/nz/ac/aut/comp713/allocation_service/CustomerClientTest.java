package nz.ac.aut.comp713.allocation_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import static org.springframework.test.web.client.ExpectedCount.once;
import org.springframework.test.web.client.MockRestServiceServer;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import org.springframework.web.client.RestClient;

import nz.ac.aut.comp713.allocation_service.client.CustomerClient;
import nz.ac.aut.comp713.allocation_service.exception.CustomerNotFoundException;
import nz.ac.aut.comp713.allocation_service.exception.CustomerServiceUnavailableException;

class CustomerClientTest {
    private MockRestServiceServer server;
    private CustomerClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new CustomerClient(builder, "http://localhost:8081");
    }

    @Test
    void retrievesCustomerFromCustomerService() {
        server.expect(once(), requestTo("http://localhost:8081/internal/v1/allocation-reference/customers/7"))
                .andRespond(withSuccess("""
                        {"id":7,"name":"Island Foods","contactName":"John",
                        "phoneNumber":"0211234567","active":true}
                        """, MediaType.APPLICATION_JSON));

        var response = client.getCustomer(7L);

        assertEquals("Island Foods", response.name());
        server.verify();
    }

    @Test
    void mapsMissingCustomerTo404Exception() {
        server.expect(once(), requestTo("http://localhost:8081/internal/v1/allocation-reference/customers/999"))
                .andRespond(withResourceNotFound());
        assertThrows(CustomerNotFoundException.class, () -> client.getCustomer(999L));
        server.verify();
    }

    @Test
    void mapsServerFailureToOwnUnavailableException() {
        server.expect(once(), requestTo("http://localhost:8081/internal/v1/allocation-reference/customers"))
                .andRespond(withServerError());
        assertThrows(CustomerServiceUnavailableException.class, () -> client.getCustomers());
        server.verify();
    }

    @Test
    void mapsUnexpectedClientErrorToOwnUnavailableException() {
        server.expect(once(), requestTo("http://localhost:8081/internal/v1/allocation-reference/customers/7"))
                .andRespond(withBadRequest());
        assertThrows(CustomerServiceUnavailableException.class, () -> client.getCustomer(7L));
        server.verify();
    }

    @Test
    void mapsEmptySingleResponseToOwnUnavailableException() {
        server.expect(once(), requestTo("http://localhost:8081/internal/v1/allocation-reference/customers/7"))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));
        assertThrows(CustomerServiceUnavailableException.class, () -> client.getCustomer(7L));
        server.verify();
    }

    @Test
    void mapsMalformedJsonToOwnUnavailableException() {
        server.expect(once(), requestTo("http://localhost:8081/internal/v1/allocation-reference/customers/7"))
                .andRespond(withSuccess("{not-json", MediaType.APPLICATION_JSON));
        assertThrows(CustomerServiceUnavailableException.class, () -> client.getCustomer(7L));
        server.verify();
    }
}
