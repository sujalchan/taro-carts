package nz.ac.aut.comp713.allocation_service.client;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import nz.ac.aut.comp713.allocation_service.exception.CustomerNotFoundException;
import nz.ac.aut.comp713.allocation_service.exception.CustomerServiceUnavailableException;

// HTTP client used by allocation-service to communicate with customer-service
@Component
public class CustomerClient {

    private final RestClient restClient;

    // build a RestClient using the configured customer-service base URL
    public CustomerClient(
            RestClient.Builder builder,
            @Value("${customer.service.url}") String baseUrl) {

        this.restClient = builder.baseUrl(baseUrl).build();
    }

    // retrieve all customers from customer-service
    public List<CustomerResponse> getCustomers() {
        try {
            // call the customer-service REST API and deserialize the JSON response
            CustomerResponse[] customers = restClient.get()
                    .uri("/api/v1/customers")
                    .retrieve()
                    .body(CustomerResponse[].class);

            // return an empty list if the remote service returns no response body
            if (customers == null) {
                return List.of();
            }
            return Arrays.asList(customers);

        } catch (ResourceAccessException | HttpServerErrorException e) {
            // translate connection failures into an application-specific service exception
            throw new CustomerServiceUnavailableException();
        }
    }

    // retrieve a customer from customer-service by ID
    public CustomerResponse getCustomer(Long customerId) {
        try {
            return restClient.get()
                    .uri("/api/v1/customers/{id}", customerId)
                    .retrieve()
                    .body(CustomerResponse.class);

        } catch (HttpClientErrorException.NotFound e) {
            // translate a remote 404 into the allocation-service customer exception
            throw new CustomerNotFoundException(customerId);

        } catch (ResourceAccessException | HttpServerErrorException e) {
            // handle cases where customer-service cannot be reached
            throw new CustomerServiceUnavailableException();
        }
    }

}
