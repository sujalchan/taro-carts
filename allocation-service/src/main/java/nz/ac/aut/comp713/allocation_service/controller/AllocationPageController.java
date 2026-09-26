package nz.ac.aut.comp713.allocation_service.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.validation.Validator;

import org.springframework.format.annotation.DateTimeFormat;

import org.springframework.stereotype.Controller;

import org.springframework.ui.Model;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import nz.ac.aut.comp713.allocation_service.client.CustomerClient;
import nz.ac.aut.comp713.allocation_service.client.TaroClient;
import nz.ac.aut.comp713.allocation_service.dto.AllocationItemRequest;
import nz.ac.aut.comp713.allocation_service.dto.WeeklyAllocationRequest;

import nz.ac.aut.comp713.allocation_service.exception.CustomerNotFoundException;
import nz.ac.aut.comp713.allocation_service.exception.CustomerServiceUnavailableException;
import nz.ac.aut.comp713.allocation_service.exception.TaroServiceUnavailableException;
import nz.ac.aut.comp713.allocation_service.exception.DuplicateTaroTypeException;
import nz.ac.aut.comp713.allocation_service.exception.TaroTypeNotFoundException;
import nz.ac.aut.comp713.allocation_service.exception.WeeklyAllocationAlreadyExistsException;
import nz.ac.aut.comp713.allocation_service.exception.InvalidQuantityException;
import nz.ac.aut.comp713.allocation_service.exception.WeeklyAllocationNotFoundException;

import nz.ac.aut.comp713.allocation_service.service.WeeklyAllocationService;
import nz.ac.aut.comp713.allocation_service.dto.WeeklyAllocationResponse;
import nz.ac.aut.comp713.allocation_service.model.DeliveryStatus;

// MVC controller for the server rendered weekly allocation pages
@Controller
public class AllocationPageController {

	private final WeeklyAllocationService weeklyAllocationService;
	private final CustomerClient customerClient;
	private final TaroClient taroClient;
	private final Validator validator;

	// pass the allocation service, both reference clients, and form validator
	public AllocationPageController(
			WeeklyAllocationService weeklyAllocationService,
			CustomerClient customerClient,
			TaroClient taroClient,
			Validator validator) {

		this.weeklyAllocationService = weeklyAllocationService;
		this.customerClient = customerClient;
		this.taroClient = taroClient;
		this.validator = validator;
	}

	// show all weekly allocations
	@GetMapping("/allocations")
	public String getAllocationsPage(Model model) {

		try {
			model.addAttribute(
					"allocations",
					weeklyAllocationService.getAllWeeklyAllocations());
		} catch (CustomerServiceUnavailableException | TaroServiceUnavailableException exception) {
			// show the unavailable service error
			model.addAttribute(
					"allocations",
					List.of());

			model.addAttribute(
					"errors",
					List.of(exception.getMessage()));
		}

		return "allocations";
	}

	// show the create form with one empty allocation item row
	@GetMapping("/allocations/new")
	public String getCreateAllocationPage(Model model) {
		return renderCreateForm(
				model,
				null,
				null,
				List.of(
						new AllocationFormRow(
								null,
								null,
								"")),
				new ArrayList<>());
	}

	// create a weekly allocation
	@PostMapping("/allocations/new")
	public String createAllocation(
			@RequestParam(required = false) Long customerId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
			@RequestParam(name = "taroTypeId", required = false) List<Long> taroTypeIds,
			@RequestParam(name = "quantity", required = false) List<BigDecimal> quantities,
			@RequestParam(name = "pricePerKg", required = false) List<String> prices,
			Model model) {

		List<String> errors = new ArrayList<>();

		// rebuild submitted rows so their values can be preserved if validation fails
		List<AllocationFormRow> formRows = buildFormRows(
				taroTypeIds,
				quantities,
				prices);

		List<AllocationItemRequest> allocationItems = new ArrayList<>();

		// convert optional price strings into BigDecimal values for the request DTO
		for (AllocationFormRow row : formRows) {
			BigDecimal pricePerKg = null;

			if (row.pricePerKg() != null && !row.pricePerKg().isBlank()) {
				try {
					pricePerKg = new BigDecimal(row.pricePerKg());
				} catch (NumberFormatException exception) {
					errors.add(
							"Price per kg must be a valid number");
				}
			}

			allocationItems.add(
					new AllocationItemRequest(
							row.taroTypeId(),
							row.quantity(),
							pricePerKg));
		}

		WeeklyAllocationRequest request = new WeeklyAllocationRequest(
				customerId,
				weekStart,
				allocationItems);

		// run the same validation rules used by the REST API
		validator.validate(request)
				.stream()
				.map(violation -> violation.getMessage())
				.forEach(errors::add);

		if (!errors.isEmpty()) {
			return renderCreateForm(
					model,
					customerId,
					weekStart,
					formRows,
					errors);
		}

		// pass valid form data to the same service layer used by the REST API
		try {
			weeklyAllocationService.createWeeklyAllocation(request);
		} catch (
				// redisplay the form with business rule or service errors
				WeeklyAllocationAlreadyExistsException
				| DuplicateTaroTypeException
				| InvalidQuantityException
				| CustomerNotFoundException
				| TaroTypeNotFoundException
				| CustomerServiceUnavailableException
				| TaroServiceUnavailableException exception) {

			errors.add(exception.getMessage());

			return renderCreateForm(
					model,
					customerId,
					weekStart,
					formRows,
					errors);
		}
		return "redirect:/allocations";
	}

	// load the existing allocation and convert its items into form rows
	@GetMapping("/allocations/{id}/edit")
	public String getEditAllocationPage(
			@PathVariable Long id,
			Model model) {

		try {
			WeeklyAllocationResponse allocation = weeklyAllocationService.getWeeklyAllocationById(id);

			// convert stored integer quantities and prices into values suitable for the
			// HTML form
			List<AllocationFormRow> allocationRows = allocation.allocationItems()
					.stream()
					.map(item -> new AllocationFormRow(
							item.taroTypeId(),
							BigDecimal.valueOf(item.quantity()),
							item.pricePerKg().toPlainString()))
					.toList();

			return renderEditForm(
					model,
					id,
					allocation.customerId(),
					allocation.weekStart(),
					allocation.deliveryStatus(),
					allocationRows,
					new ArrayList<>());

		} catch (WeeklyAllocationNotFoundException exception) {
			return "redirect:/allocations";
		}
	}

	// validate and submit changes to an existing weekly allocation
	@PostMapping("/allocations/{id}/edit")
	public String updateAllocation(
			@PathVariable Long id,
			@RequestParam(required = false) Long customerId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
			@RequestParam(required = false) DeliveryStatus deliveryStatus,
			@RequestParam(name = "taroTypeId", required = false) List<Long> taroTypeIds,
			@RequestParam(name = "quantity", required = false) List<BigDecimal> quantities,
			@RequestParam(name = "pricePerKg", required = false) List<String> prices,
			Model model) {

		List<String> errors = new ArrayList<>();

		List<AllocationFormRow> formRows = buildFormRows(
				taroTypeIds,
				quantities,
				prices);

		List<AllocationItemRequest> allocationItems = new ArrayList<>();

		for (AllocationFormRow row : formRows) {
			BigDecimal pricePerKg = null;

			if (row.pricePerKg() != null && !row.pricePerKg().isBlank()) {
				try {
					pricePerKg = new BigDecimal(row.pricePerKg());
				} catch (NumberFormatException exception) {
					errors.add("Price per kg must be a valid number");
				}
			}

			allocationItems.add(
					new AllocationItemRequest(
							row.taroTypeId(),
							row.quantity(),
							pricePerKg));
		}

		WeeklyAllocationRequest request = new WeeklyAllocationRequest(
				customerId,
				weekStart,
				allocationItems,
				deliveryStatus);

		// run the same validation rules used by the REST API
		validator.validate(request)
				.stream()
				.map(violation -> violation.getMessage())
				.forEach(errors::add);

		if (!errors.isEmpty()) {
			return renderEditForm(
					model,
					id,
					customerId,
					weekStart,
					deliveryStatus,
					formRows,
					errors);
		}

		try {
			weeklyAllocationService
					.updateWeeklyAllocation(
							id,
							request);

		} catch (
				WeeklyAllocationAlreadyExistsException
				| DuplicateTaroTypeException
				| InvalidQuantityException
				| CustomerNotFoundException
				| TaroTypeNotFoundException
				| CustomerServiceUnavailableException
				| TaroServiceUnavailableException
				| WeeklyAllocationNotFoundException exception) {

			errors.add(exception.getMessage());

			return renderEditForm(
					model,
					id,
					customerId,
					weekStart,
					deliveryStatus,
					formRows,
					errors);
		}

		return "redirect:/allocations";
	}

	// delete a weekly allocation
	@PostMapping("/allocations/{id}/delete")
	public String deleteAllocation(
			@PathVariable Long id) {

		try {
			weeklyAllocationService.deleteWeeklyAllocation(id);
		} catch (WeeklyAllocationNotFoundException exception) {
			// allocation may already have been deleted
		}

		return "redirect:/allocations";
	}

	// render the create form with dropdown data
	private String renderCreateForm(
			Model model,
			Long selectedCustomerId,
			LocalDate weekStart,
			List<AllocationFormRow> allocationRows,
			List<String> errors) {

		model.addAttribute(
				"selectedCustomerId",
				selectedCustomerId);

		model.addAttribute(
				"weekStart",
				weekStart);

		model.addAttribute(
				"allocationRows",
				allocationRows);

		try {
			// only active customers should receive new allocations
			model.addAttribute(
					"customers",
					customerClient.getCustomers()
							.stream()
							.filter(customer -> Boolean.TRUE.equals(
									customer.active()))
							.toList());

			model.addAttribute(
					"taroTypes",
					taroClient.getTaroTypes());

		} catch (CustomerServiceUnavailableException | TaroServiceUnavailableException exception) {
			model.addAttribute(
					"customers",
					List.of());

			model.addAttribute(
					"taroTypes",
					List.of());

			if (!errors.contains(exception.getMessage())) {
				errors.add(exception.getMessage());
			}
		}

		if (!errors.isEmpty()) {
			model.addAttribute("errors", errors);
		}

		return "allocation-form";
	}

	// render the edit form with dropdown data
	private String renderEditForm(
			Model model,
			Long allocationId,
			Long selectedCustomerId,
			LocalDate weekStart,
			DeliveryStatus deliveryStatus,
			List<AllocationFormRow> allocationRows,
			List<String> errors) {

		model.addAttribute(
				"allocationId",
				allocationId);

		model.addAttribute(
				"selectedCustomerId",
				selectedCustomerId);

		model.addAttribute(
				"weekStart",
				weekStart);
		// restore the selected status if the edit form has validation errors
		model.addAttribute("deliveryStatus", deliveryStatus);
		model.addAttribute("deliveryStatuses", DeliveryStatus.values());

		model.addAttribute(
				"allocationRows",
				allocationRows);

		try {

			model.addAttribute(
					"customers",
					customerClient.getCustomers()
							.stream()
							.filter(customer -> Boolean.TRUE.equals(
									customer.active())
									|| customer.id().equals(
											selectedCustomerId))
							.toList());

			model.addAttribute(
					"taroTypes",
					taroClient.getTaroTypes());

		} catch (CustomerServiceUnavailableException | TaroServiceUnavailableException exception) {
			model.addAttribute(
					"customers",
					List.of());

			model.addAttribute(
					"taroTypes",
					List.of());

			if (!errors.contains(exception.getMessage())) {
				errors.add(
						exception.getMessage());
			}
		}

		if (!errors.isEmpty()) {
			model.addAttribute(
					"errors",
					errors);
		}

		return "allocation-edit-form";
	}

	// rebuild allocation rows after a failed submission
	private List<AllocationFormRow> buildFormRows(
			List<Long> taroTypeIds,
			List<BigDecimal> quantities,
			List<String> prices) {

		int taroCount = taroTypeIds == null
				? 0
				: taroTypeIds.size();

		int quantityCount = quantities == null
				? 0
				: quantities.size();

		int priceCount = prices == null
				? 0
				: prices.size();

		int rowCount = Math.max(
				taroCount,
				Math.max(
						quantityCount,
						priceCount));

		if (rowCount == 0) {
			return List.of(
					new AllocationFormRow(
							null,
							null,
							""));
		}

		List<AllocationFormRow> rows = new ArrayList<>();

		for (int i = 0; i < rowCount; i++) {
			rows.add(
					new AllocationFormRow(
							valueAt(taroTypeIds, i),
							valueAt(quantities, i),
							valueAt(prices, i)));
		}

		return rows;
	}

	private <T> T valueAt(
			List<T> values,
			int index) {

		if (values == null || index >= values.size()) {
			return null;
		}
		return values.get(index);
	}

	// values used to redraw allocation rows after errors
	private record AllocationFormRow(
			Long taroTypeId,
			BigDecimal quantity,
			String pricePerKg) {
	}
}
