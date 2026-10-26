package nz.ac.aut.comp713.allocation_service.client;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import nz.ac.aut.comp713.allocation_service.exception.CustomerNotFoundException;
import nz.ac.aut.comp713.allocation_service.exception.CustomerServiceUnavailableException;

// HTTP client used by allocation-service to communicate with customer-service
@Component
public class CustomerClient {

    private final RestClient restClient;

    @Value("${allocation.reference.customer-secret:}")
    private String referenceSecret = "";

    @PostConstruct
    void requireReferenceSecret() {
        if (referenceSecret.length() < 32) throw new IllegalStateException("ALLOCATION_CUSTOMER_REFERENCE_SECRET must contain at least 32 characters");
    }

    // build a RestClient using the configured customer-service base URL
    public CustomerClient(
            RestClient.Builder builder,
            @Value("${customer.service.url}") String baseUrl) {

        requireProtectedUrl(baseUrl);
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    private static void requireProtectedUrl(String value) {
        URI uri = URI.create(value);
        if (!("http".equalsIgnoreCase(uri.getScheme()) && "localhost".equalsIgnoreCase(uri.getHost()))
                && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Customer service URL must use local HTTP or HTTPS");
        }
    }

    // retrieve all customers from customer-service
    public List<CustomerResponse> getCustomers() {
        try {
            // call the customer-service REST API and deserialize the JSON response
            CustomerResponse[] customers = restClient.get()
                    .uri("/internal/v1/allocation-reference/customers")
                    .headers(headers -> headers.set("X-Allocation-Reference-Key", referenceSecret))
                    .retrieve()
                    .body(CustomerResponse[].class);

            // return an empty list if the remote service returns no response body
            if (customers == null) {
                return List.of();
            }
            return Arrays.asList(customers);

        } catch (RestClientException e) {
            // translate connection failures into an application-specific service exception
            throw new CustomerServiceUnavailableException();
        }
    }

    // retrieve a customer from customer-service by ID
    public CustomerResponse getCustomer(Long customerId) {
        try {
            CustomerResponse customer = restClient.get()
                    .uri("/internal/v1/allocation-reference/customers/{id}", customerId)
                    .headers(headers -> headers.set("X-Allocation-Reference-Key", referenceSecret))
                    .retrieve()
                    .body(CustomerResponse.class);
            if (customer == null) {
                throw new CustomerServiceUnavailableException();
            }
            return customer;

        } catch (HttpClientErrorException.NotFound e) {
            // translate a remote 404 into the allocation-service customer exception
            throw new CustomerNotFoundException(customerId);

        } catch (RestClientException e) {
            // handle cases where customer-service cannot be reached
            throw new CustomerServiceUnavailableException();
        }
    }

}
