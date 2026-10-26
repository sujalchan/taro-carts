package nz.ac.aut.comp713.customer_service.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import nz.ac.aut.comp713.customer_service.dto.CustomerResponse;
import nz.ac.aut.comp713.customer_service.service.CustomerService;

/** Minimal customer data needed by allocation-service, behind its private read key. */
@RestController
@RequestMapping("/internal/v1/allocation-reference/customers")
public class AllocationReferenceController {
    public record CustomerReference(Long id, String name, String address, String phone, Boolean active) {
        static CustomerReference from(CustomerResponse customer) {
            return new CustomerReference(customer.id(), customer.name(), customer.address(),
                    customer.phone(), customer.active());
        }
    }

    private final CustomerService customers;

    public AllocationReferenceController(CustomerService customers) {
        this.customers = customers;
    }

    @GetMapping
    public List<CustomerReference> list() {
        return customers.getAllCustomers(null).stream().map(CustomerReference::from).toList();
    }

    @GetMapping("/{id}")
    public CustomerReference get(@PathVariable Long id) {
        return CustomerReference.from(customers.getCustomerById(id));
    }
}
