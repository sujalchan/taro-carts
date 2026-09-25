package nz.ac.aut.comp713.customer_service.service;

import java.util.List;

import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import nz.ac.aut.comp713.customer_service.dto.CustomerRequest;
import nz.ac.aut.comp713.customer_service.dto.CustomerResponse;
import nz.ac.aut.comp713.customer_service.exception.CustomerAlreadyExistsException;
import nz.ac.aut.comp713.customer_service.exception.CustomerNotFoundException;
import nz.ac.aut.comp713.customer_service.model.Customer;
import nz.ac.aut.comp713.customer_service.repository.CustomerRepository;

// service layer containing customer business logic
@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    // pass the respository used for customer persistence
    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    // return all customers, optionally filtered by name, contact name, or phone
    public List<CustomerResponse> getAllCustomers(String search) {
        return customerRepository.findAll()
                .stream()
                .filter(customer -> matchesSearch(customer, search))
                .map(this::toResponse)
                .toList();
    }

    // retrieve a customer or fail if the requested ID does not exist
    public CustomerResponse getCustomerById(Long id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new CustomerNotFoundException(id));

        return toResponse(customer);
    }

    // create a customer within a transaction so the database write is atomic
    @Transactional
    public CustomerResponse createCustomer(CustomerRequest request) {

        String name = request.name();
        String normalizedName = name.toLowerCase().replaceAll("\\s+", "");

        Customer customer = new Customer();
        customer.setName(name);
        customer.setNormalizedName(normalizedName);
        customer.setContactName(request.contactName());
        customer.setPhone(request.phone());

        // set the active status if provided in the request
        if (request.active() != null) {
            customer.setActive(request.active());
        }

        // flush immediately so database uniqueness violations are detected here
        try {
            Customer savedCustomer = customerRepository.saveAndFlush(customer);
            return toResponse(savedCustomer);
        } catch (JpaSystemException e) {
            // convert the database constraint failure into a domain-specific conflict
            throw new CustomerAlreadyExistsException(name);
        }
    }

    // update an existing customer within a transaction
    @Transactional
    public CustomerResponse updateCustomer(Long id, CustomerRequest request) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new CustomerNotFoundException(id));

        String name = request.name();
        String normalizedName = name.toLowerCase().replaceAll("\\s+", "");

        customer.setName(name);
        customer.setNormalizedName(normalizedName);
        customer.setContactName(request.contactName());
        customer.setPhone(request.phone());

        // set the active status if provided in the request
        if (request.active() != null) {
            customer.setActive(request.active());
        }

        // flush immediately so duplicate-name conflicts are raised before returning
        try {
            Customer updatedCustomer = customerRepository.saveAndFlush(customer);
            return toResponse(updatedCustomer);
        } catch (JpaSystemException e) {
            throw new CustomerAlreadyExistsException(name);
        }
    }

    // map the persistence entity to the response DTO exposed by the API
    private CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getName(),
                customer.getContactName(),
                customer.getPhone(),
                customer.isActive());
    }

    // match the search query against customer name, contact name, or phone
    private boolean matchesSearch(Customer customer, String search) {
        // no search value means every customer should be returned
        if (search == null || search.isBlank()) {
            return true;
        }

        String query = search.trim().toLowerCase();

        return customer.getName().toLowerCase().contains(query)
                || (customer.getContactName() != null
                        && customer.getContactName().toLowerCase().contains(query))
                || (customer.getPhone() != null
                        && customer.getPhone().toLowerCase().contains(query));
    }
}
