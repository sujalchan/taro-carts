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

    // show the create form with new customers active by default
    @GetMapping("/customers/new")
    public String getCreateCustomerPage(Model model) {
        model.addAttribute("active", true);
        return "customer-form";
    }

    @PostMapping("/customers/new")
    public String createCustomer(
            @RequestParam String name,
            @RequestParam(required = false) String contactName,
            @RequestParam(required = false) String phone,
            @RequestParam(defaultValue = "false") boolean active,
            Model model) {

        CustomerRequest request = new CustomerRequest(
                name,
                contactName,
                phone,
                active);

        // run the same validation rules used by the API
        var violations = validator.validate(request);

        // return validation messages and preserve the submitted form values
        if (!violations.isEmpty()) {
            List<String> errors = violations.stream()
                    .map(violation -> violation.getMessage())
                    .toList();

            addFormValues(
                    model,
                    name,
                    contactName,
                    phone,
                    active);

            model.addAttribute("errors", errors);
            return "customer-form";
        }

        // show the duplicate customer error without losing the entered form data
        try {
            customerService.createCustomer(request);
        } catch (CustomerAlreadyExistsException exception) {

            addFormValues(
                    model,
                    name,
                    contactName,
                    phone,
                    active);

            model.addAttribute("errors", List.of(exception.getMessage()));
            return "customer-form";
        }

        return "redirect:/customers";
    }

    // restore submitted values when the create form has to be displayed again
    private void addFormValues(
            Model model,
            String name,
            String contactName,
            String phone,
            boolean active) {

        model.addAttribute("name", name);
        model.addAttribute("contactName", contactName);
        model.addAttribute("phone", phone);
        model.addAttribute("active", active);
    }

    // load the existing customer and populate the edit form
    @GetMapping("/customers/{id}/edit")
    public String getEditCustomerPage(
            @PathVariable Long id,
            Model model) {

        var customer = customerService.getCustomerById(id);

        model.addAttribute("customerId", customer.id());
        model.addAttribute("name", customer.name());
        model.addAttribute("contactName", customer.contactName());
        model.addAttribute("phone", customer.phone());
        model.addAttribute("active", customer.active());

        return "customer-edit-form";
    }

    // validate and submit changes to an existing customer
    @PostMapping("/customers/{id}/edit")
    public String updateCustomer(
            @PathVariable Long id,
            @RequestParam String name,
            @RequestParam(required = false) String contactName,
            @RequestParam(required = false) String phone,
            @RequestParam(defaultValue = "false") boolean active,
            Model model) {

        CustomerRequest request = new CustomerRequest(
                name,
                contactName,
                phone,
                active);

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
                    contactName,
                    phone,
                    active);

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
                    contactName,
                    phone,
                    active);

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
            String contactName,
            String phone,
            boolean active) {

        model.addAttribute("customerId", customerId);
        model.addAttribute("name", name);
        model.addAttribute("contactName", contactName);
        model.addAttribute("phone", phone);
        model.addAttribute("active", active);
    }
}