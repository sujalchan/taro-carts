package nz.ac.aut.comp713.customer_service.internal;

import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import nz.ac.aut.comp713.customer_service.dto.CustomerResponse;
import nz.ac.aut.comp713.customer_service.exception.CustomerAlreadyExistsException;
import nz.ac.aut.comp713.customer_service.exception.CustomerNotFoundException;
import nz.ac.aut.comp713.customer_service.model.Customer;
import nz.ac.aut.comp713.customer_service.repository.CustomerRepository;

/** Package-private persistence entry point used only by the authenticated internal controller. */
@Service
class InternalCustomerProvisioning {

    private final CustomerRepository customers;

    InternalCustomerProvisioning(CustomerRepository customers) {
        this.customers = customers;
    }

    @Transactional
    CustomerResponse create(Long registrationRequestId, ProvisionCustomerRequest request) {
        var existing = customers.findByProvisioningRequestId(registrationRequestId);
        if (existing.isPresent()) return response(existing.get());
        String name = request.name().trim();
        Customer customer = new Customer();
        customer.setProvisioningRequestId(registrationRequestId);
        customer.setName(name);
        customer.setNormalizedName(name.toLowerCase(Locale.ROOT).replaceAll("\\s+", ""));
        customer.setAddress(request.address().trim());
        customer.setContactName(clean(request.contactName()));
        customer.setPhone(clean(request.phone()));
        customer.setActive(false);
        try {
            return response(customers.saveAndFlush(customer));
        } catch (DataIntegrityViolationException | JpaSystemException exception) {
            throw new CustomerAlreadyExistsException(name);
        }
    }

    @Transactional
    CustomerResponse setActive(Long id, boolean active) {
        Customer customer = customers.findById(id).orElseThrow(() -> new CustomerNotFoundException(id));
        customer.setActive(active);
        return response(customers.saveAndFlush(customer));
    }

    @Transactional
    boolean deleteInactive(Long id) {
        Customer customer = customers.findById(id).orElseThrow(() -> new CustomerNotFoundException(id));
        if (customer.isActive()) return false;
        customers.delete(customer);
        customers.flush();
        return true;
    }

    private CustomerResponse response(Customer customer) {
        return new CustomerResponse(customer.getId(), customer.getName(), customer.getAddress(),
                customer.getContactName(), customer.getPhone(), customer.isActive());
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
