package nz.ac.aut.comp713.taro_service.controller;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Validator;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import nz.ac.aut.comp713.taro_service.dto.TaroTypeRequest;
import nz.ac.aut.comp713.taro_service.exception.TaroTypeAlreadyExistsException;
import nz.ac.aut.comp713.taro_service.service.TaroTypeService;

// MVC controller for the server rendered taro type pages
@Controller
public class TaroTypePageController {

    private final TaroTypeService taroTypeService;
    private final Validator validator;

    // pass the taro type service and validator used by the HTML forms
    public TaroTypePageController(TaroTypeService taroTypeService, Validator validator) {
        this.taroTypeService = taroTypeService;
        this.validator = validator;
    }

    // render all taro types, or filter them when a search query is supplied
    @GetMapping("/taro-types")
    public String getTaroTypesPage(@RequestParam(required = false) String search, Model model) {

        model.addAttribute("taroTypes", taroTypeService.getAllTaroTypes(search));

        // keep the current search value visible in the search box
        model.addAttribute("search", search);
        return "taro-types";
    }

    // show the server rendered form for creating a taro type
    @GetMapping("/taro-types/new")
    public String getCreateTaroTypePage() {
        return "taro-type-form";
    }

    // create a taro type
    @PostMapping("/taro-types/new")
    public String createTaroType(
            @RequestParam String name,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String standardPrice,
            Model model) {

        // convert the submitted price string to BigDecimal before validation
        BigDecimal parsedPrice;

        try {
            parsedPrice = parsePrice(standardPrice);
        } catch (NumberFormatException exception) {

            addCreateFormValues(
                    model,
                    name,
                    description,
                    standardPrice);

            model.addAttribute("errors", List.of("Standard price must be a valid number"));
            return "taro-type-form";
        }

        // build the same request DTO used by the REST API
        TaroTypeRequest request = new TaroTypeRequest(
                name,
                description,
                parsedPrice);

        // run the same validation rules used by the REST API
        var violations = validator.validate(request);

        // redisplay the form with validation errors and the submitted values
        if (!violations.isEmpty()) {

            List<String> errors = violations.stream()
                    .map(violation -> violation.getMessage())
                    .toList();

            addCreateFormValues(
                    model,
                    name,
                    description,
                    standardPrice);

            model.addAttribute("errors", errors);
            return "taro-type-form";
        }

        // show duplicate-name errors without losing the entered form data
        try {
            taroTypeService.createTaroType(request);
        } catch (TaroTypeAlreadyExistsException exception) {

            addCreateFormValues(
                    model,
                    name,
                    description,
                    standardPrice);

            model.addAttribute(
                    "errors",
                    List.of(exception.getMessage()));

            return "taro-type-form";
        }

        return "redirect:/taro-types";
    }

    // load the existing taro type and populate the edit form
    @GetMapping("/taro-types/{id}/edit")
    public String getEditTaroTypePage(
            @PathVariable Long id,
            Model model) {

        var taroType = taroTypeService.getTaroTypeById(id);

        model.addAttribute("taroTypeId", taroType.id());
        model.addAttribute("name", taroType.name());
        model.addAttribute("description", taroType.description());
        model.addAttribute("standardPrice", taroType.standardPrice());

        return "taro-type-edit-form";
    }

    // validate and submit changes to an existing taro type
    @PostMapping("/taro-types/{id}/edit")
    public String updateTaroType(
            @PathVariable Long id,
            @RequestParam String name,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String standardPrice,
            Model model) {

        BigDecimal parsedPrice;

        // parse the submitted price before creating the request DTO
        try {
            parsedPrice = parsePrice(standardPrice);
        } catch (NumberFormatException exception) {

            addEditFormValues(
                    model,
                    id,
                    name,
                    description,
                    standardPrice);

            model.addAttribute(
                    "errors",
                    List.of("Standard price must be a valid number"));

            return "taro-type-edit-form";
        }

        TaroTypeRequest request = new TaroTypeRequest(
                name,
                description,
                parsedPrice);

        var violations = validator.validate(request);

        if (!violations.isEmpty()) {
            List<String> errors = violations.stream()
                    .map(violation -> violation.getMessage())
                    .toList();

            addEditFormValues(
                    model,
                    id,
                    name,
                    description,
                    standardPrice);

            model.addAttribute("errors", errors);

            return "taro-type-edit-form";
        }

        // show duplicate name errors while preserving the attempted changes
        try {
            taroTypeService.updateTaroType(id, request);
        } catch (TaroTypeAlreadyExistsException exception) {

            addEditFormValues(
                    model,
                    id,
                    name,
                    description,
                    standardPrice);

            model.addAttribute(
                    "errors",
                    List.of(exception.getMessage()));

            return "taro-type-edit-form";
        }

        return "redirect:/taro-types";
    }

    // convert an optional form price into BigDecimal for the service layer
    private BigDecimal parsePrice(String standardPrice) {
        if (standardPrice == null || standardPrice.isBlank()) {
            return null;
        }
        return new BigDecimal(standardPrice);
    }

    // restore submitted values when the create form must be displayed again
    private void addCreateFormValues(
            Model model,
            String name,
            String description,
            String standardPrice) {

        model.addAttribute("name", name);
        model.addAttribute("description", description);
        model.addAttribute("standardPrice", standardPrice);
    }

    // restore submitted values when the edit form must be displayed again
    private void addEditFormValues(
            Model model,
            Long taroTypeId,
            String name,
            String description,
            String standardPrice) {

        model.addAttribute("taroTypeId", taroTypeId);
        model.addAttribute("name", name);
        model.addAttribute("description", description);
        model.addAttribute("standardPrice", standardPrice);
    }
}