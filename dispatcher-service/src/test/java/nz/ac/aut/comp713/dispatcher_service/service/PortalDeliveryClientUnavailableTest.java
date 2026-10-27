package nz.ac.aut.comp713.dispatcher_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.HttpServletRequest;
import nz.ac.aut.comp713.dispatcher_service.exception.GlobalExceptionHandler;
import nz.ac.aut.comp713.dispatcher_service.security.AllocationIdentitySigner;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

class PortalDeliveryClientUnavailableTest {
    @Test
    void hidesDeliveredAddressFromDispatcherButKeepsItForAdmin() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        var body = """
                [{"id":11,"customerId":3,"customerName":"Customer","weekStart":"2026-10-26",
                "deliveryStatus":"DELIVERED","paymentStatus":"UNPAID","dispatcherId":7,
                "deliveryAddress":"69 Greenmeadows Avenue","invoiceAvailable":false,
                "allocationItems":[],"dispatchStartAddress":"16 Putney Way"}]
                """.getBytes(StandardCharsets.UTF_8);
        for (var path : new String[] {"/api/v1/allocations/dispatcher/me", "/api/v1/allocations/dispatcher/7"}) {
            server.createContext(path, exchange -> {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (var output = exchange.getResponseBody()) { output.write(body); }
            });
        }
        server.start();
        try {
            var signer = mock(AllocationIdentitySigner.class);
            when(signer.sign(any(), anyString(), anyString())).thenReturn("test-assertion");
            var client = new PortalDeliveryClient(signer,
                    "http://localhost:" + server.getAddress().getPort(), Duration.ofSeconds(1), Duration.ofSeconds(1));
            var request = mock(HttpServletRequest.class);
            assertThat(client.list(request, null).get(0).deliveryAddress()).isNull();
            assertThat(client.list(request, 7L).get(0).deliveryAddress()).isEqualTo("69 Greenmeadows Avenue");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void propagatesTheServiceNamedByAnAllocationFailure() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/v1/allocations/dispatcher/me", exchange -> {
            byte[] body = "{\"code\":\"CUSTOMER_SERVICE_UNAVAILABLE\",\"message\":\"Unavailable\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(503, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            var signer = mock(AllocationIdentitySigner.class);
            when(signer.sign(any(), anyString(), anyString())).thenReturn("test-assertion");
            var client = new PortalDeliveryClient(signer,
                    "http://localhost:" + server.getAddress().getPort(), Duration.ofSeconds(1), Duration.ofSeconds(1));
            assertThatThrownBy(() -> client.list(mock(HttpServletRequest.class), null))
                    .isInstanceOfSatisfying(PortalServiceUnavailableException.class, failure -> {
                        assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                        assertThat(failure.getReason()).isEqualTo("Customer Service is unavailable.");
                        var response = new GlobalExceptionHandler().status(failure,
                                new MockHttpServletRequest("GET", "/api/v1/mobile/portal/deliveries"));
                        assertThat(response.getBody().code()).isEqualTo("CUSTOMER_SERVICE_UNAVAILABLE");
                    });
        } finally {
            server.stop(0);
        }
    }
}
