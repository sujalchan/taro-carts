package nz.ac.aut.comp713.customer_service.controller;

import java.util.List;

import jakarta.validation.Validator;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import nz.ac.aut.comp713.customer_service.dto.CustomerRequest;
import nz.ac.aut.comp713.customer_service.exception.CustomerAlreadyExistsException;
import nz.ac.aut.comp713.customer_service.service.CustomerService;

// MVC controller for the server rendered customer pages
@Controller
public class CustomerPageController {

    private final CustomerService customerService;
    private final Validator validator;

    // pass the customer service with Jakarta validator used by the page forms
    public CustomerPageController(CustomerService customerService, Validator validator) {
        this.customerService = customerService;
        this.validator = validator;
    }

    // render the customer list, optionally filtered by the search query
    @GetMapping("/customers")
    public String getCustomersPage(@RequestParam(required = false) String search, Model model) {
        model.addAttribute("customers", customerService.getAllCustomers(search));

        // keep the current search value visible in the search box
        model.addAttribute("search", search);
        return "customers";
    }

    // load the existing customer and populate the edit form
    @GetMapping("/customers/{id}/edit")
    public String getEditCustomerPage(
            @PathVariable Long id,
            Model model) {

        var customer = customerService.getCustomerById(id);

        model.addAttribute("customerId", customer.id());
        model.addAttribute("name", customer.name());
        model.addAttribute("address", customer.address());
        model.addAttribute("contactName", customer.contactName());
        model.addAttribute("phone", customer.phone());

        return "customer-edit-form";
    }

    // validate and submit changes to an existing customer
    @PostMapping("/customers/{id}/edit")
    public String updateCustomer(
            @PathVariable Long id,
            @RequestParam String name,
            @RequestParam(defaultValue = "") String address,
            @RequestParam(required = false) String contactName,
            @RequestParam(required = false) String phone,
            Model model) {

        CustomerRequest request = new CustomerRequest(
                name,
                address,
                contactName,
                phone,
                null);

        var violations = validator.validate(request);

        // redisplay the edit form with validation errors and the user's submitted
        // values
        if (!violations.isEmpty()) {

            List<String> errors = violations.stream()
                    .map(violation -> violation.getMessage())
                    .toList();

            addEditFormValues(
                    model,
                    id,
                    name,
                    address,
                    contactName,
                    phone);

            model.addAttribute("errors", errors);

            return "customer-edit-form";
        }

        // show duplicate-name errors while preserving the attempted changes
        try {
            customerService.updateCustomer(id, request);
        } catch (CustomerAlreadyExistsException exception) {

            addEditFormValues(
                    model,
                    id,
                    name,
                    address,
                    contactName,
                    phone);

            model.addAttribute(
                    "errors",
                    List.of(exception.getMessage()));

            return "customer-edit-form";
        }

        return "redirect:/customers";
    }

    // restore submitted values when the edit form must be displayed again
    private void addEditFormValues(
            Model model,
            Long customerId,
            String name,
            String address,
            String contactName,
            String phone) {

        model.addAttribute("customerId", customerId);
        model.addAttribute("name", name);
        model.addAttribute("address", address);
        model.addAttribute("contactName", contactName);
        model.addAttribute("phone", phone);
    }
}
