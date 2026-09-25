package nz.ac.aut.comp713.allocation_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import nz.ac.aut.comp713.allocation_service.client.TaroClient;
import nz.ac.aut.comp713.allocation_service.exception.TaroServiceUnavailableException;
import nz.ac.aut.comp713.allocation_service.exception.TaroTypeNotFoundException;

class TaroClientTest {
    private MockRestServiceServer server;
    private TaroClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new TaroClient(builder, "http://localhost:8083");
    }

    @Test
    void retrievesTaroTypeFromTaroService() {
        server.expect(once(), requestTo("http://localhost:8083/api/v1/taro-types/7"))
                .andRespond(withSuccess("""
                        {"id":7,"name":"Samoan Taro","description":"Large","standardPrice":50.00}
                        """, MediaType.APPLICATION_JSON));
        var response = client.getTaroType(7L);
        assertEquals("Samoan Taro", response.name());
        assertEquals(new BigDecimal("50.00"), response.standardPrice());
        server.verify();
    }

    @Test
    void mapsMissingTaroTypeTo404Exception() {
        server.expect(once(), requestTo("http://localhost:8083/api/v1/taro-types/999"))
                .andRespond(withResourceNotFound());
        assertThrows(TaroTypeNotFoundException.class, () -> client.getTaroType(999L));
        server.verify();
    }

    @Test
    void mapsTaroServiceFailureToOwnUnavailableException() {
        server.expect(once(), requestTo("http://localhost:8083/api/v1/taro-types"))
                .andRespond(withServerError());
        assertThrows(TaroServiceUnavailableException.class, () -> client.getTaroTypes());
        server.verify();
    }
}
