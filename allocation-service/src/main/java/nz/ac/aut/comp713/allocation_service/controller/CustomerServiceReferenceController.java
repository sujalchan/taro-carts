package nz.ac.aut.comp713.allocation_service.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import nz.ac.aut.comp713.allocation_service.client.CustomerClient;
import nz.ac.aut.comp713.allocation_service.client.CustomerResponse;
import nz.ac.aut.comp713.allocation_service.client.TaroTypeResponse;

// exposes customer-service reference data through allocation-service for client-rendered pages
@RestController
@RequestMapping("/api/v1/customer-service-reference")
public class CustomerServiceReferenceController {

    private final CustomerClient customerClient;

    // inject the HTTP client used to communicate with customer-service
    public CustomerServiceReferenceController(CustomerClient customerClient) {
        this.customerClient = customerClient;
    }

    // return active customers for allocation form dropdowns
    @GetMapping("/customers")
    public List<CustomerResponse> getCustomers() {
        return customerClient.getCustomers()
                .stream()
                .filter(customer -> Boolean.TRUE.equals(customer.active()))
                .toList();
    }

    // return taro types for allocation form dropdowns
    @GetMapping("/taro-types")
    public List<TaroTypeResponse> getTaroTypes() {
        return customerClient.getTaroTypes();
    }
}