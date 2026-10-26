package nz.ac.aut.comp713.dispatcher_service.controller;

import java.util.List;

import jakarta.validation.Validator;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import nz.ac.aut.comp713.dispatcher_service.dto.DispatcherRequest;
import nz.ac.aut.comp713.dispatcher_service.exception.DispatcherAlreadyExistsException;
import nz.ac.aut.comp713.dispatcher_service.exception.DispatcherNotFoundException;
import nz.ac.aut.comp713.dispatcher_service.service.DispatcherService;

@Controller
public class DispatcherPageController {
    private final DispatcherService dispatcherService;
    private final Validator validator;

    public DispatcherPageController(DispatcherService dispatcherService, Validator validator) {
        this.dispatcherService = dispatcherService;
        this.validator = validator;
    }

    @GetMapping({"/", "/dispatchers"})
    public String list(@RequestParam(required = false) String search, Model model) {
        model.addAttribute("dispatchers", dispatcherService.getAll(search));
        model.addAttribute("search", search);
        return "dispatchers";
    }

    @GetMapping("/dispatchers/{id}/edit")
    public String editDispatcher(@PathVariable Long id, Model model) {
        try {
            var dispatcher = dispatcherService.getById(id);
            model.addAttribute("dispatcherId", dispatcher.id());
            addValues(model, dispatcher.name(), dispatcher.contactName(), dispatcher.phone(), dispatcher.address());
            return "dispatcher-edit-form";
        } catch (DispatcherNotFoundException exception) {
            return "redirect:/dispatchers";
        }
    }

    @PostMapping("/dispatchers/{id}/edit")
    public String updateDispatcher(
            @PathVariable Long id,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String contactName,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String address,
            Model model) {
        DispatcherRequest request = new DispatcherRequest(name, contactName, phone, address, null);
        var violations = validator.validate(request);
        if (!violations.isEmpty()) {
            model.addAttribute("dispatcherId", id);
            addValues(model, name, contactName, phone, address);
            model.addAttribute("errors", violations.stream().map(v -> v.getMessage()).toList());
            return "dispatcher-edit-form";
        }
        try {
            dispatcherService.update(id, request);
        } catch (DispatcherAlreadyExistsException | DispatcherNotFoundException exception) {
            model.addAttribute("dispatcherId", id);
            addValues(model, name, contactName, phone, address);
            model.addAttribute("errors", List.of(exception.getMessage()));
            return "dispatcher-edit-form";
        }
        return "redirect:/dispatchers";
    }

    private void addValues(Model model, String name, String contactName, String phone, String address) {
        model.addAttribute("name", name);
        model.addAttribute("contactName", contactName);
        model.addAttribute("phone", phone);
        model.addAttribute("address", address);
    }
}
