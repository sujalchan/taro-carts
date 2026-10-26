package nz.ac.aut.comp713.allocation_service;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

import nz.ac.aut.comp713.allocation_service.client.CustomerClient;
import nz.ac.aut.comp713.allocation_service.client.TaroClient;
import nz.ac.aut.comp713.allocation_service.client.CustomerResponse;
import nz.ac.aut.comp713.allocation_service.client.TaroTypeResponse;
import nz.ac.aut.comp713.allocation_service.exception.CustomerNotFoundException;
import nz.ac.aut.comp713.allocation_service.exception.CustomerServiceUnavailableException;
import nz.ac.aut.comp713.allocation_service.exception.TaroServiceUnavailableException;
import nz.ac.aut.comp713.allocation_service.exception.TaroTypeNotFoundException;
import nz.ac.aut.comp713.allocation_service.repository.AllocationItemRepository;
import nz.ac.aut.comp713.allocation_service.repository.InvoiceRepository;
import nz.ac.aut.comp713.allocation_service.repository.WeeklyAllocationRepository;
import nz.ac.aut.comp713.allocation_service.model.DeliveryStatus;
import nz.ac.aut.comp713.allocation_service.model.PaymentStatus;

@SpringBootTest
@AutoConfigureMockMvc
class WeeklyAllocationApiTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AllocationItemRepository allocationItemRepository;

	@Autowired
	private WeeklyAllocationRepository weeklyAllocationRepository;

	@Autowired
	private InvoiceRepository invoiceRepository;

	@MockitoBean
	private CustomerClient customerClient;

	@MockitoBean
	private TaroClient taroClient;

	@BeforeEach
	void setUp() {

		// clear allocation data before each test
		invoiceRepository.deleteAll();
		allocationItemRepository.deleteAll();
		weeklyAllocationRepository.deleteAll();

		// mock customer-service responses
		when(customerClient.getCustomer(1L))
				.thenReturn(new CustomerResponse(
						1L,
						"Island Foods",
						"12 Market Street, Auckland",
						"John",
						"0211234567",
						true));

		when(customerClient.getCustomer(2L))
				.thenReturn(new CustomerResponse(
						2L,
						"Fresh Choice",
						"12 Market Street, Auckland",
						"Sarah",
						"0215555555",
						true));

		when(taroClient.getTaroType(1L))
				.thenReturn(new TaroTypeResponse(
						1L,
						"Samoan Taro",
						"Large premium taro",
						new BigDecimal("50.00")));

		when(taroClient.getTaroType(2L))
				.thenReturn(new TaroTypeResponse(
						2L,
						"Fiji Taro",
						"Fijian taro",
						new BigDecimal("45.00")));
	}

	@Test
	// verifies that listing allocations returns an empty list when none exist
	void getAllAllocationsReturnsEmptyList() throws Exception {

		mockMvc.perform(get("/api/v1/allocations"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void getAllocationsCanFilterByCustomerId() throws Exception {
		createAllocation(1L, "2026-09-14", 1L, 100);
		createAllocation(2L, "2026-09-14", 2L, 50);

		mockMvc.perform(get("/api/v1/allocations").param("customerId", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].customerId").value(2))
				.andExpect(jsonPath("$[0].customerName").value("Fresh Choice"));
	}

	@Test
	void getAllocationsRejectsNonPositiveCustomerIdFilter() throws Exception {
		mockMvc.perform(get("/api/v1/allocations").param("customerId", "0"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_CUSTOMER_FILTER"))
				.andExpect(jsonPath("$.path").value("/api/v1/allocations"));
	}

	@Test
	void getAllocationsRejectsMalformedCustomerIdFilter() throws Exception {
		mockMvc.perform(get("/api/v1/allocations").param("customerId", "not-a-number"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_CUSTOMER_FILTER"));
	}

	@Test
	void getAllocationsReturnsEmptyListForUnknownCustomerId() throws Exception {
		mockMvc.perform(get("/api/v1/allocations").param("customerId", "999"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void serverRenderedAllocationsCanFilterByCustomerId() throws Exception {
		createAllocation(1L, "2026-09-14", 1L, 100);
		createAllocation(2L, "2026-09-14", 2L, 50);
		when(customerClient.getCustomers()).thenReturn(java.util.List.of(
				new CustomerResponse(1L, "Island Foods", "12 Market Street, Auckland", "John", "0211234567", true),
				new CustomerResponse(2L, "Fresh Choice", "12 Market Street, Auckland", "Sarah", "0215555555", true)));

		mockMvc.perform(get("/allocations").param("customerId", "2"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Fresh Choice")))
				.andExpect(content().string(not(containsString("<span>Island Foods</span>"))))
				.andExpect(content().string(containsString("customerId")));
	}

	@Test
	void serverRenderedAllocationsShowsCustomerServiceErrorWhenLoadingFilterOptionsFails() throws Exception {
		when(customerClient.getCustomers()).thenThrow(new CustomerServiceUnavailableException());

		mockMvc.perform(get("/allocations"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Unable to load allocations")))
				.andExpect(content().string(containsString("Customer service is currently unavailable")));
	}

	@Test
	void serverRenderedAllocationsReportsInvalidCustomerFilter() throws Exception {
		when(customerClient.getCustomers()).thenReturn(java.util.List.of());
		mockMvc.perform(get("/allocations").param("customerId", "invalid"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Customer filter must be a positive integer customer ID")));
	}

	@Test
	void clientRenderedAllocationsPageIncludesCustomerFilter() throws Exception {
		mockMvc.perform(get("/allocations-client.html"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("id=\"customerFilter\"")));
	}

	@Test
	void customerFilterReferenceIncludesInactiveCustomers() throws Exception {
		when(customerClient.getCustomers()).thenReturn(java.util.List.of(
				new CustomerResponse(1L, "Island Foods", "12 Market Street, Auckland", "John", "0211234567", true),
				new CustomerResponse(2L, "Former Customer", "12 Market Street, Auckland", "Sarah", "0215555555", false)));
		mockMvc.perform(get("/api/v1/customer-service-reference/customers").param("includeInactive", "true"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[1].active").value(false));
	}

	@Test
	// verifies that a new allocation uses the standard price and requested status by default
	void createAllocationUsesDefaultStandardPrice() throws Exception {

		mockMvc.perform(post("/api/v1/allocations")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						"customerId": 1,
						"weekStart": "2026-09-14",
						"allocationItems": [
						{
						"taroTypeId": 1,
						"quantity": 100
						}
						]
						}
						"""))
				.andExpect(status().isCreated())
				.andExpect(header().exists("Location"))
				.andExpect(jsonPath("$.customerId").value(1))
				.andExpect(jsonPath("$.customerName").value("Island Foods"))
				.andExpect(jsonPath("$.weekStart").value("2026-09-14"))
				.andExpect(jsonPath("$.deliveryStatus").value("REQUESTED_ALLOCATION"))
				.andExpect(jsonPath("$.paymentStatus").value("UNPAID"))
				.andExpect(jsonPath("$.allocationItems", hasSize(1)))
				.andExpect(jsonPath("$.allocationItems[0].taroTypeId").value(1))
				.andExpect(jsonPath("$.allocationItems[0].taroTypeName")
						.value("Samoan Taro"))
				.andExpect(jsonPath("$.allocationItems[0].quantity").value(100))
				.andExpect(jsonPath("$.allocationItems[0].pricePerKg").value(50.00));
	}

	@Test
	// verifies that a new allocation retains supplied custom prices
	void createAllocationPreservesCustomPrice() throws Exception {

		mockMvc.perform(post("/api/v1/allocations")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						"customerId": 2,
						"weekStart": "2026-09-14",
						"allocationItems": [
						{
						"taroTypeId": 1,
						"quantity": 80,
						"pricePerKg": 47.50
						},
						{
						"taroTypeId": 2,
						"quantity": 40,
						"pricePerKg": 42.50
						}
						]
						}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.customerName").value("Fresh Choice"))
				.andExpect(jsonPath("$.allocationItems", hasSize(2)))
				.andExpect(jsonPath(
						"$.allocationItems[*].taroTypeName",
						containsInAnyOrder("Samoan Taro", "Fiji Taro")))
				.andExpect(jsonPath(
						"$.allocationItems[*].pricePerKg",
						containsInAnyOrder(47.50, 42.50)));
	}

	@Test
	// verifies that a supplied midweek date is normalized to the monday
	void createAllocationNormalizesWeekStartToMonday() throws Exception {

		mockMvc.perform(post("/api/v1/allocations")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						"customerId": 1,
						"weekStart": "2026-09-16",
						"allocationItems": [
						{
						"taroTypeId": 1,
						"quantity": 100
						}
						]
						}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.weekStart")
						.value("2026-09-14"));
	}

	@Test
	// verifies that one customer can have allocations in separate weeks
	void sameCustomerCanHaveAllocationsForDifferentWeeks() throws Exception {

		createAllocation(1L, "2026-09-14", 1L, 100);

		mockMvc.perform(post("/api/v1/allocations")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						"customerId": 1,
						"weekStart": "2026-09-21",
						"allocationItems": [
						{
						"taroTypeId": 2,
						"quantity": 60
						}
						]
						}
						"""))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/v1/allocations"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)));
	}

	@Test
	// verifies that one customer cannot have two allocations in the same week
	void sameCustomerCannotHaveTwoAllocationsInSameWeek() throws Exception {

		createAllocation(
				1L,
				"2026-09-14",
				1L,
				100);

		mockMvc.perform(post("/api/v1/allocations")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						"customerId": 1,
						"weekStart": "2026-09-16",
						"allocationItems": [
						{
						"taroTypeId": 2,
						"quantity": 50
						}
						]
						}
						"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code")
						.value("WEEKLY_ALLOCATION_ALREADY_EXISTS"))
				.andExpect(jsonPath("$.message")
						.value("Weekly allocation already exists for Island Foods for week starting 2026-09-14"));
	}

	@Test
	// verifies that an existing allocation can be retrieved by id
	void getExistingAllocationReturnsAllocation() throws Exception {

		createAllocation(1L, "2026-09-14", 1L, 100);

		Long id = weeklyAllocationRepository.findAll().getFirst().getId();

		mockMvc.perform(get("/api/v1/allocations/{id}", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id))
				.andExpect(jsonPath("$.customerId").value(1))
				.andExpect(jsonPath("$.customerName").value("Island Foods"))
				.andExpect(jsonPath("$.allocationItems[0].taroTypeName")
						.value("Samoan Taro"));
	}

	@Test
	// verifies that retrieving an unknown allocation returns not found
	void getMissingAllocationReturns404() throws Exception {

		mockMvc.perform(get("/api/v1/allocations/999"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code")
						.value("WEEKLY_ALLOCATION_NOT_FOUND"))
				.andExpect(jsonPath("$.message")
						.value("Weekly allocation not found with id: 999"));
	}

	@Test
	// verifies that creating a duplicate weekly allocation returns a conflict
	void duplicateWeeklyAllocationReturns409() throws Exception {

		createAllocation(1L, "2026-09-14", 1L, 100);

		mockMvc.perform(post("/api/v1/allocations")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						"customerId": 1,
						"weekStart": "2026-09-14",
						"allocationItems": [
						{
						"taroTypeId": 2,
						"quantity": 20
						}
						]
						}
						"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code")
						.value("WEEKLY_ALLOCATION_ALREADY_EXISTS"))
				.andExpect(jsonPath("$.message")
						.value("Weekly allocation already exists for Island Foods for week starting 2026-09-14"));
	}

	@Test
	// verifies that duplicate taro types in one allocation are rejected
	void duplicateTaroTypeReturns400() throws Exception {

		mockMvc.perform(post("/api/v1/allocations")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						"customerId": 1,
						"weekStart": "2026-09-14",
						"allocationItems": [
						{
						"taroTypeId": 1,
						"quantity": 100
						},
						{
						"taroTypeId": 1,
						"quantity": 50
						}
						]
						}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code")
						.value("DUPLICATE_TARO_TYPE"))
				.andExpect(jsonPath("$.message")
						.value("Samoan Taro cannot appear more than once in an allocation"));
	}

	@Test
	// verifies that creating an allocation for an unknown customer returns not
	// found
	void missingCustomerReturns404() throws Exception {

		when(customerClient.getCustomer(999L))
				.thenThrow(new CustomerNotFoundException(999L));

		mockMvc.perform(post("/api/v1/allocations")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						"customerId": 999,
						"weekStart": "2026-09-14",
						"allocationItems": [
						{
						"taroTypeId": 1,
						"quantity": 100
						}
						]
						}
						"""))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code")
						.value("CUSTOMER_NOT_FOUND"));
	}

	@Test
	// verifies that creating an allocation for an unknown taro type returns not
	// found
	void missingTaroTypeReturns404() throws Exception {

		when(taroClient.getTaroType(999L))
				.thenThrow(new TaroTypeNotFoundException(999L));

		mockMvc.perform(post("/api/v1/allocations")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						"customerId": 1,
						"weekStart": "2026-09-14",
						"allocationItems": [
						{
						"taroTypeId": 999,
						"quantity": 100
						}
						]
						}
						"""))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code")
						.value("TARO_TYPE_NOT_FOUND"))
				.andExpect(jsonPath("$.message")
						.value("Taro type not found with id: 999"));
		;
	}

	@Test
	// verifies that a missing customer id is rejected
	void missingCustomerIdReturns400() throws Exception {

		mockMvc.perform(post("/api/v1/allocations")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						"weekStart": "2026-09-14",
						"allocationItems": [
						{
						"taroTypeId": 1,
						"quantity": 100
						}
						]
						}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.message")
						.value("Customer ID is required"));
	}

	@Test
	// verifies that a missing week start is rejected
	void missingWeekStartReturns400() throws Exception {

		mockMvc.perform(post("/api/v1/allocations")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						"customerId": 1,
						"allocationItems": [
						{
						"taroTypeId": 1,
						"quantity": 100
						}
						]
						}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.message")
						.value("Week start is required"));
	}

	@Test
	// verifies that an allocation without items is rejected
	void emptyAllocationItemsReturns400() throws Exception {

		mockMvc.perform(post("/api/v1/allocations")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						"customerId": 1,
						"weekStart": "2026-09-14",
						"allocationItems": []
						}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.message")
						.value("At least one allocation item is required"));
	}

	@Test
	// verifies that an allocation item without a taro type id is rejected
	void missingTaroTypeIdReturns400() throws Exception {

		mockMvc.perform(post("/api/v1/allocations")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						"customerId": 1,
						"weekStart": "2026-09-14",
						"allocationItems": [
						{
						"quantity": 100
						}
						]
						}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message")
						.value("Taro type ID is required"));
	}

	@Test
	// verifies that a zero allocation quantity is rejected
	void zeroQuantityReturns400() throws Exception {

		mockMvc.perform(post("/api/v1/allocations")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						"customerId": 1,
						"weekStart": "2026-09-14",
						"allocationItems": [
						{
						"taroTypeId": 1,
						"quantity": 0
						}
						]
						}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message")
						.value("Quantity must be greater than zero"));
	}

	@Test
	// verifies that a negative allocation quantity is rejected
	void negativeQuantityReturns400() throws Exception {

		mockMvc.perform(post("/api/v1/allocations")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						"customerId": 1,
						"weekStart": "2026-09-14",
						"allocationItems": [
						{
						"taroTypeId": 1,
						"quantity": -50
						}
						]
						}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message")
						.value("Quantity must be greater than zero"));
	}

	@Test
	// verifies that a negative price is rejected
	void negativePriceReturns400() throws Exception {

		mockMvc.perform(post("/api/v1/allocations")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						"customerId": 1,
						"weekStart": "2026-09-14",
						"allocationItems": [
						{
						"taroTypeId": 1,
						"quantity": 100,
						"pricePerKg": -5.00
						}
						]
						}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message")
						.value("Price per kg cannot be negative"));
	}

	@Test
	// verifies that an unavailable customer service during creation returns service
	// unavailable
	void customerServiceUnavailableDuringCreateReturns503() throws Exception {

		when(customerClient.getCustomer(1L))
				.thenThrow(new CustomerServiceUnavailableException());

		mockMvc.perform(post("/api/v1/allocations")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						"customerId": 1,
						"weekStart": "2026-09-14",
						"allocationItems": [
						{
						"taroTypeId": 1,
						"quantity": 100
						}
						]
						}
						"""))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code")
						.value("CUSTOMER_SERVICE_UNAVAILABLE"));
	}

	@Test
	// verifies that an unavailable customer service during retrieval returns
	// service unavailable
	void customerServiceUnavailableDuringGetReturns503() throws Exception {

		createAllocation(1L, "2026-09-14", 1L, 100);

		Long id = weeklyAllocationRepository.findAll().getFirst().getId();

		when(customerClient.getCustomer(1L))
				.thenThrow(new CustomerServiceUnavailableException());

		mockMvc.perform(get("/api/v1/allocations/{id}", id))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code")
						.value("CUSTOMER_SERVICE_UNAVAILABLE"));
	}

	@Test
	// verifies that an existing allocation can be updated
	void updateExistingAllocationReturns200() throws Exception {

		createAllocation(1L, "2026-09-14", 1L, 100);

		Long id = weeklyAllocationRepository.findAll().getFirst().getId();

		mockMvc.perform(put("/api/v1/allocations/{id}", id)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						"customerId": 1,
						"weekStart": "2026-09-14",
						"allocationItems": [
						{
						"taroTypeId": 1,
						"quantity": 150,
						"pricePerKg": 48.00
						},
						{
						"taroTypeId": 2,
						"quantity": 30
						}
						]
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.allocationItems", hasSize(2)))
				.andExpect(jsonPath(
						"$.allocationItems[*].taroTypeName",
						containsInAnyOrder("Samoan Taro", "Fiji Taro")))
				.andExpect(jsonPath(
						"$.allocationItems[*].quantity",
						containsInAnyOrder(150, 30)))
				.andExpect(jsonPath(
						"$.allocationItems[*].pricePerKg",
						containsInAnyOrder(48.00, 45.00)));
	}

	@Test
	// verifies that status changes persist and omitted values preserve the current status
	void deliveryStatusCanBeSetAndPreservedAcrossUpdates() throws Exception {
		createAllocation(1L, "2026-09-14", 1L, 100);
		Long id = weeklyAllocationRepository.findAll().getFirst().getId();
		String updateWithoutStatus = """
				{
				  "customerId": 1,
				  "weekStart": "2026-09-14",
				  "allocationItems": [{"taroTypeId": 1, "quantity": 150}]
				}
				""";

		mockMvc.perform(put("/api/v1/allocations/{id}", id)
				.contentType(MediaType.APPLICATION_JSON)
				.content(updateWithoutStatus.replace("\"allocationItems\"", "\"deliveryStatus\": \"IN_TRANSIT\", \"allocationItems\"")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.deliveryStatus").value("IN_TRANSIT"));

		mockMvc.perform(put("/api/v1/allocations/{id}", id)
				.contentType(MediaType.APPLICATION_JSON)
				.content(updateWithoutStatus))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.deliveryStatus").value("IN_TRANSIT"));

		mockMvc.perform(put("/api/v1/allocations/{id}", id)
				.contentType(MediaType.APPLICATION_JSON)
				.content(updateWithoutStatus.replace("\"allocationItems\"", "\"deliveryStatus\": \"DELIVERED\", \"allocationItems\"")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.deliveryStatus").value("DELIVERED"));

		mockMvc.perform(get("/api/v1/allocations/{id}", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.deliveryStatus").value("DELIVERED"));
		mockMvc.perform(get("/api/v1/allocations"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].deliveryStatus").value("DELIVERED"));
	}

	@Test
	void paymentStatusCanBeSetAndPreservedAcrossUpdates() throws Exception {
		createAllocation(1L, "2026-09-14", 1L, 100);
		Long id = weeklyAllocationRepository.findAll().getFirst().getId();
		String update = """
				{
				  "customerId": 1,
				  "weekStart": "2026-09-14",
				  "allocationItems": [{"taroTypeId": 1, "quantity": 100}]
				}
				""";

		for (String paymentStatus : new String[] { "PAID", "UNPAID", "OVERDUE" }) {
			mockMvc.perform(put("/api/v1/allocations/{id}", id)
					.contentType(MediaType.APPLICATION_JSON)
					.content(update.replace("\"allocationItems\"",
							"\"paymentStatus\": \"" + paymentStatus + "\", \"allocationItems\"")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paymentStatus").value(paymentStatus));

			mockMvc.perform(get("/api/v1/allocations/{id}", id))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.paymentStatus").value(paymentStatus));
		}

		mockMvc.perform(put("/api/v1/allocations/{id}", id)
				.contentType(MediaType.APPLICATION_JSON)
				.content(update))
				.andExpect(status().isOk())
			.andExpect(jsonPath("$.paymentStatus").value("OVERDUE"));
		mockMvc.perform(get("/api/v1/allocations"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].paymentStatus").value("OVERDUE"));
	}

	@Test
	void paymentStatusCannotBeSetWhenCreatingAllocation() throws Exception {
		mockMvc.perform(post("/api/v1/allocations")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "customerId": 1,
						  "weekStart": "2026-09-14",
						  "paymentStatus": "PAID",
						  "allocationItems": [{"taroTypeId": 1, "quantity": 100}]
						}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_PAYMENT_STATUS"));
		assertTrue(weeklyAllocationRepository.findAll().isEmpty());
	}

	@Test
	void invalidPaymentStatusReturns400() throws Exception {
		createAllocation(1L, "2026-09-14", 1L, 100);
		Long id = weeklyAllocationRepository.findAll().getFirst().getId();
		mockMvc.perform(put("/api/v1/allocations/{id}", id)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "customerId": 1,
						  "weekStart": "2026-09-14",
						  "paymentStatus": "UNKNOWN",
						  "allocationItems": [{"taroTypeId": 1, "quantity": 100}]
						}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void allOrderStatusesCanBeSavedAndRead() throws Exception {
		createAllocation(1L, "2026-09-14", 1L, 100);
		Long id = weeklyAllocationRepository.findAll().getFirst().getId();
		String[] statuses = {
				"REQUESTED_ALLOCATION",
				"APPROVED_ALLOCATION",
				"PREPARING_ALLOCATION",
				"AWAITING_DISPATCH",
				"IN_TRANSIT",
				"DELIVERED",
				"CANCELLED"
		};

		for (String orderStatus : statuses) {
			mockMvc.perform(put("/api/v1/allocations/{id}", id)
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "customerId": 1,
							  "weekStart": "2026-09-14",
							  "deliveryStatus": "%s",
							  "allocationItems": [{"taroTypeId": 1, "quantity": 100}]
							}
							""".formatted(orderStatus)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.deliveryStatus").value(orderStatus));

			mockMvc.perform(get("/api/v1/allocations/{id}", id))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.deliveryStatus").value(orderStatus));
		}
	}

	@Test
	// verifies that a create request cannot override the requested default
	void deliveryStatusCannotBeSetWhenCreatingAllocation() throws Exception {
		mockMvc.perform(post("/api/v1/allocations")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "customerId": 1,
						  "weekStart": "2026-09-14",
						  "deliveryStatus": "DELIVERED",
						  "allocationItems": [{"taroTypeId": 1, "quantity": 100}]
						}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_DELIVERY_STATUS"));
		assertTrue(weeklyAllocationRepository.findAll().isEmpty());
	}

	@Test
	// verifies that an unknown status value returns a validation error
	void invalidDeliveryStatusReturns400() throws Exception {
		mockMvc.perform(post("/api/v1/allocations")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "customerId": 1,
						  "weekStart": "2026-09-14",
						  "deliveryStatus": "UNKNOWN",
						  "allocationItems": [{"taroTypeId": 1, "quantity": 100}]
						}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void legacyPendingStatusCannotBeSetOnUpdate() throws Exception {
		createAllocation(1L, "2026-09-14", 1L, 100);
		Long id = weeklyAllocationRepository.findAll().getFirst().getId();

		mockMvc.perform(put("/api/v1/allocations/{id}", id)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
							{
							  "customerId": 1,
							  "weekStart": "2026-09-14",
							  "deliveryStatus": "PENDING",
							  "allocationItems": [{"taroTypeId": 1, "quantity": 100}]
							}
							"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

		mockMvc.perform(get("/api/v1/allocations/{id}", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.deliveryStatus").value("REQUESTED_ALLOCATION"));
	}

	@Test
	// verifies that status is shown on the list and edit pages but not the create page
	void serverRenderedPagesShowOrderStatus() throws Exception {
		createAllocation(1L, "2026-09-14", 1L, 100);
		Long id = weeklyAllocationRepository.findAll().getFirst().getId();

		mockMvc.perform(get("/allocations"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Order Status:")))
				.andExpect(content().string(containsString("REQUESTED ALLOCATION")))
				.andExpect(content().string(containsString("Payment Status:")))
				.andExpect(content().string(containsString("UNPAID")));
		mockMvc.perform(get("/allocations/new"))
				.andExpect(status().isOk())
				.andExpect(content().string(not(containsString("name=\"deliveryStatus\""))))
				.andExpect(content().string(not(containsString("name=\"paymentStatus\""))));
		mockMvc.perform(get("/allocations/{id}/edit", id))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("name=\"deliveryStatus\"")))
				.andExpect(content().string(containsString("Order Status")))
				.andExpect(content().string(containsString("value=\"APPROVED_ALLOCATION\"")))
				.andExpect(content().string(containsString("value=\"AWAITING_DISPATCH\"")))
				.andExpect(content().string(containsString("name=\"paymentStatus\"")))
				.andExpect(content().string(containsString("value=\"OVERDUE\"")));
	}

	@Test
	void serverRenderedEditCanChangePaymentStatus() throws Exception {
		createAllocation(1L, "2026-09-14", 1L, 100);
		Long id = weeklyAllocationRepository.findAll().getFirst().getId();

		mockMvc.perform(post("/allocations/{id}/edit", id)
				.param("customerId", "1")
				.param("weekStart", "2026-09-14")
				.param("deliveryStatus", "REQUESTED_ALLOCATION")
				.param("paymentStatus", "PAID")
				.param("taroTypeId", "1")
				.param("quantity", "100")
				.param("pricePerKg", "50.00"))
				.andExpect(status().is3xxRedirection());

		mockMvc.perform(get("/api/v1/allocations/{id}", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paymentStatus").value("PAID"));
	}

	@Test
	// verifies that updates are persisted and returned by later retrievals
	void updatedAllocationPersists() throws Exception {

		createAllocation(1L, "2026-09-14", 1L, 100);

		Long id = weeklyAllocationRepository.findAll().getFirst().getId();

		mockMvc.perform(put("/api/v1/allocations/{id}", id)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						"customerId": 1,
						"weekStart": "2026-09-14",
						"allocationItems": [
						{
						"taroTypeId": 1,
						"quantity": 150,
						"pricePerKg": 48.00
						},
						{
						"taroTypeId": 2,
						"quantity": 30
						}
						]
						}
						"""))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/allocations/{id}", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.allocationItems", hasSize(2)))
				.andExpect(jsonPath(
						"$.allocationItems[*].quantity",
						containsInAnyOrder(150, 30)))
				.andExpect(jsonPath(
						"$.allocationItems[*].pricePerKg",
						containsInAnyOrder(48, 45)));
	}

	@Test
	// verifies that an updated midweek date is normalized to the monday
	void updateNormalizesWeekStartToMonday() throws Exception {

		createAllocation(
				1L,
				"2026-09-14",
				1L,
				100);

		Long id = weeklyAllocationRepository
				.findAll()
				.getFirst()
				.getId();

		mockMvc.perform(put(
				"/api/v1/allocations/{id}",
				id)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						"customerId": 1,
						"weekStart": "2026-09-17",
						"allocationItems": [
						{
						"taroTypeId": 1,
						"quantity": 150
						}
						]
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.weekStart")
						.value("2026-09-14"));
	}

	@Test
	// verifies that updating an unknown allocation returns not found
	void updateMissingAllocationReturns404() throws Exception {

		mockMvc.perform(put("/api/v1/allocations/999")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						"customerId": 1,
						"weekStart": "2026-09-14",
						"allocationItems": [
						{
						"taroTypeId": 1,
						"quantity": 100
						}
						]
						}
						"""))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code")
						.value("WEEKLY_ALLOCATION_NOT_FOUND"));
	}

	@Test
	// verifies that an update cannot duplicate an existing customer and week pair
	void updateToExistingCustomerAndWeekReturns409() throws Exception {

		createAllocation(1L, "2026-09-14", 1L, 100);
		createAllocation(1L, "2026-09-21", 2L, 60);

		Long firstId = weeklyAllocationRepository.findAll().stream()
				.filter(allocation -> allocation.getWeekStart().toString()
						.equals("2026-09-14"))
				.findFirst()
				.orElseThrow()
				.getId();

		mockMvc.perform(put("/api/v1/allocations/{id}", firstId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						"customerId": 1,
						"weekStart": "2026-09-21",
						"allocationItems": [
						{
						"taroTypeId": 1,
						"quantity": 100
						}
						]
						}
						"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code")
						.value("WEEKLY_ALLOCATION_ALREADY_EXISTS"));
	}

	@Test
	// verifies that duplicate taro types are rejected during an update
	void duplicateTaroTypeDuringUpdateReturns400() throws Exception {

		createAllocation(1L, "2026-09-14", 1L, 100);

		Long id = weeklyAllocationRepository.findAll().getFirst().getId();

		mockMvc.perform(put("/api/v1/allocations/{id}", id)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						"customerId": 1,
						"weekStart": "2026-09-14",
						"allocationItems": [
						{
						"taroTypeId": 1,
						"quantity": 100
						},
						{
						"taroTypeId": 1,
						"quantity": 50
						}
						]
						}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code")
						.value("DUPLICATE_TARO_TYPE"))
				.andExpect(jsonPath("$.message")
						.value("Samoan Taro cannot appear more than once in an allocation"));
		;
	}

	@Test
	// verifies that updating to an unknown customer returns not found
	void missingCustomerDuringUpdateReturns404() throws Exception {

		createAllocation(1L, "2026-09-14", 1L, 100);

		Long id = weeklyAllocationRepository.findAll().getFirst().getId();

		when(customerClient.getCustomer(999L))
				.thenThrow(new CustomerNotFoundException(999L));

		mockMvc.perform(put("/api/v1/allocations/{id}", id)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						"customerId": 999,
						"weekStart": "2026-09-14",
						"allocationItems": [
						{
						"taroTypeId": 1,
						"quantity": 100
						}
						]
						}
						"""))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code")
						.value("CUSTOMER_NOT_FOUND"));
	}

	@Test
	// verifies that updating to an unknown taro type returns not found
	void missingTaroTypeDuringUpdateReturns404() throws Exception {

		createAllocation(1L, "2026-09-14", 1L, 100);

		Long id = weeklyAllocationRepository.findAll().getFirst().getId();

		when(taroClient.getTaroType(999L))
				.thenThrow(new TaroTypeNotFoundException(999L));

		mockMvc.perform(put("/api/v1/allocations/{id}", id)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						"customerId": 1,
						"weekStart": "2026-09-14",
						"allocationItems": [
						{
						"taroTypeId": 999,
						"quantity": 100
						}
						]
						}
						"""))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code")
						.value("TARO_TYPE_NOT_FOUND"));
	}

	@Test
	// verifies that an invalid quantity is rejected during an update
	void invalidQuantityDuringUpdateReturns400() throws Exception {

		createAllocation(1L, "2026-09-14", 1L, 100);

		Long id = weeklyAllocationRepository.findAll().getFirst().getId();

		mockMvc.perform(put("/api/v1/allocations/{id}", id)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						"customerId": 1,
						"weekStart": "2026-09-14",
						"allocationItems": [
						{
						"taroTypeId": 1,
						"quantity": 0
						}
						]
						}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message")
						.value("Quantity must be greater than zero"));
	}

	@Test
	// verifies that a negative price is rejected during an update
	void negativePriceDuringUpdateReturns400() throws Exception {

		createAllocation(1L, "2026-09-14", 1L, 100);

		Long id = weeklyAllocationRepository.findAll().getFirst().getId();

		mockMvc.perform(put("/api/v1/allocations/{id}", id)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						"customerId": 1,
						"weekStart": "2026-09-14",
						"allocationItems": [
						{
						"taroTypeId": 1,
						"quantity": 100,
						"pricePerKg": -5
						}
						]
						}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message")
						.value("Price per kg cannot be negative"));
	}

	@Test
	// verifies that an unavailable customer service during an update returns
	// service unavailable
	void customerServiceUnavailableDuringUpdateReturns503() throws Exception {

		createAllocation(1L, "2026-09-14", 1L, 100);

		Long id = weeklyAllocationRepository.findAll().getFirst().getId();

		when(customerClient.getCustomer(1L))
				.thenThrow(new CustomerServiceUnavailableException());

		mockMvc.perform(put("/api/v1/allocations/{id}", id)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						"customerId": 1,
						"weekStart": "2026-09-14",
						"allocationItems": [
						{
						"taroTypeId": 1,
						"quantity": 200
						}
						]
						}
						"""))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code")
						.value("CUSTOMER_SERVICE_UNAVAILABLE"));
	}

	@Test
	// verifies that a failed update leaves the existing allocation unchanged
	void failedUpdateDoesNotModifyExistingAllocation() throws Exception {

		createAllocation(1L, "2026-09-14", 1L, 100);

		Long id = weeklyAllocationRepository.findAll().getFirst().getId();

		when(taroClient.getTaroType(999L))
				.thenThrow(new TaroTypeNotFoundException(999L));

		mockMvc.perform(put("/api/v1/allocations/{id}", id)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						"customerId": 1,
						"weekStart": "2026-09-14",
						"allocationItems": [
						{
						"taroTypeId": 999,
						"quantity": 200
						}
						]
						}
						"""))
				.andExpect(status().isNotFound());

		mockMvc.perform(get("/api/v1/allocations/{id}", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.allocationItems", hasSize(1)))
				.andExpect(jsonPath("$.allocationItems[0].taroTypeId").value(1))
				.andExpect(jsonPath("$.allocationItems[0].quantity").value(100))
				.andExpect(jsonPath("$.allocationItems[0].pricePerKg").value(50.00));
	}

	@Test
	// verifies that deleting an allocation also deletes its child items
	void deleteExistingAllocationReturns204AndDeletesItems() throws Exception {

		createAllocation(
				1L,
				"2026-09-14",
				1L,
				100);

		Long id = weeklyAllocationRepository
				.findAll()
				.getFirst()
				.getId();

		// make sure the allocation has child items before deleting
		assertTrue(
				!allocationItemRepository
						.findByWeeklyAllocationId(id)
						.isEmpty());

		mockMvc.perform(
				delete("/api/v1/allocations/{id}", id))
				.andExpect(status().isNoContent());

		// parent allocation should be gone
		assertTrue(
				weeklyAllocationRepository
						.findById(id)
						.isEmpty());

		// all child allocation items should also be gone
		assertTrue(
				allocationItemRepository
						.findByWeeklyAllocationId(id)
						.isEmpty());
	}

	@Test
	void customerCanDeleteOwnAllocationWhileRequested() throws Exception {
		createCustomerAllocation(1L, "2026-09-14", 1L, 100);
		Long id = weeklyAllocationRepository.findAll().getFirst().getId();
		assertEquals(1L, weeklyAllocationRepository.findById(id).orElseThrow().getRequestedByCustomerId());
		mockMvc.perform(get("/api/v1/allocations/{id}", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.requestedByCustomerId").value(1))
				.andExpect(jsonPath("$.deliveryStatus").value("REQUESTED_ALLOCATION"));

		mockMvc.perform(delete("/api/v1/allocations/{id}/customer-request", id)
				.param("customerId", "1"))
				.andExpect(status().isNoContent());

		assertTrue(weeklyAllocationRepository.findById(id).isEmpty());
		assertTrue(allocationItemRepository.findByWeeklyAllocationId(id).isEmpty());
	}

	@Test
	void customerCannotDeleteAnotherCustomersAllocation() throws Exception {
		createCustomerAllocation(1L, "2026-09-14", 1L, 100);
		Long id = weeklyAllocationRepository.findAll().getFirst().getId();

		mockMvc.perform(delete("/api/v1/allocations/{id}/customer-request", id)
				.param("customerId", "2"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("CUSTOMER_ALLOCATION_FORBIDDEN"));

		assertTrue(weeklyAllocationRepository.findById(id).isPresent());
	}

	@Test
	void customerCannotDeleteAllocationAfterApproval() throws Exception {
		createCustomerAllocation(1L, "2026-09-14", 1L, 100);
		var allocation = weeklyAllocationRepository.findAll().getFirst();
		allocation.setDeliveryStatus(DeliveryStatus.APPROVED_ALLOCATION);
		weeklyAllocationRepository.saveAndFlush(allocation);

		mockMvc.perform(delete("/api/v1/allocations/{id}/customer-request", allocation.getId())
				.param("customerId", "1"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ALLOCATION_NOT_REQUESTED"));

		assertTrue(weeklyAllocationRepository.findById(allocation.getId()).isPresent());
	}

	@Test
	void customerCannotDeleteManagerCreatedAllocation() throws Exception {
		createAllocation(1L, "2026-09-14", 1L, 100);
		Long id = weeklyAllocationRepository.findAll().getFirst().getId();

		mockMvc.perform(delete("/api/v1/allocations/{id}/customer-request", id)
				.param("customerId", "1"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("CUSTOMER_ALLOCATION_FORBIDDEN"));

		assertTrue(weeklyAllocationRepository.findById(id).isPresent());
	}

	@Test
	void customerCanMarkUnpaidOrOverdueAllocationPaidAtEveryEligibleStage() throws Exception {
		createAllocation(1L, "2026-09-14", 1L, 100);
		var allocation = weeklyAllocationRepository.findAll().getFirst();
		for (DeliveryStatus deliveryStatus : new DeliveryStatus[] {
				DeliveryStatus.APPROVED_ALLOCATION,
				DeliveryStatus.PREPARING_ALLOCATION,
				DeliveryStatus.AWAITING_DISPATCH,
				DeliveryStatus.IN_TRANSIT,
				DeliveryStatus.DELIVERED }) {
			allocation.setDeliveryStatus(deliveryStatus);
			allocation.setPaymentStatus(deliveryStatus == DeliveryStatus.IN_TRANSIT
					? PaymentStatus.OVERDUE : PaymentStatus.UNPAID);
			weeklyAllocationRepository.saveAndFlush(allocation);

			mockMvc.perform(post("/api/v1/allocations/{id}/pay", allocation.getId())
					.param("customerId", "1"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.paymentStatus").value("PAID"))
					.andExpect(jsonPath("$.deliveryStatus").value(deliveryStatus.name()));
			assertEquals(PaymentStatus.PAID,
					weeklyAllocationRepository.findById(allocation.getId()).orElseThrow().getPaymentStatus());
		}
	}

	@Test
	void customerCannotPayRequestedCancelledOrAlreadyPaidAllocation() throws Exception {
		createAllocation(1L, "2026-09-14", 1L, 100);
		var allocation = weeklyAllocationRepository.findAll().getFirst();
		mockMvc.perform(post("/api/v1/allocations/{id}/pay", allocation.getId())
				.param("customerId", "1"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PAYMENT_NOT_ALLOWED"));

		allocation.setDeliveryStatus(DeliveryStatus.CANCELLED);
		weeklyAllocationRepository.saveAndFlush(allocation);
		mockMvc.perform(post("/api/v1/allocations/{id}/pay", allocation.getId())
				.param("customerId", "1"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PAYMENT_NOT_ALLOWED"));

		allocation.setDeliveryStatus(DeliveryStatus.APPROVED_ALLOCATION);
		allocation.setPaymentStatus(PaymentStatus.PAID);
		weeklyAllocationRepository.saveAndFlush(allocation);
		mockMvc.perform(post("/api/v1/allocations/{id}/pay", allocation.getId())
				.param("customerId", "1"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ALLOCATION_ALREADY_PAID"));
		assertEquals(PaymentStatus.PAID,
				weeklyAllocationRepository.findById(allocation.getId()).orElseThrow().getPaymentStatus());
	}

	@Test
	void customerCannotPayAnotherCustomersAllocation() throws Exception {
		createAllocation(1L, "2026-09-14", 1L, 100);
		var allocation = weeklyAllocationRepository.findAll().getFirst();
		allocation.setDeliveryStatus(DeliveryStatus.APPROVED_ALLOCATION);
		weeklyAllocationRepository.saveAndFlush(allocation);
		mockMvc.perform(post("/api/v1/allocations/{id}/pay", allocation.getId())
				.param("customerId", "2"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("CUSTOMER_PAYMENT_FORBIDDEN"));
		assertEquals(PaymentStatus.UNPAID,
				weeklyAllocationRepository.findById(allocation.getId()).orElseThrow().getPaymentStatus());
	}

	@Test
	// verifies that deleting an unknown allocation returns not found
	void deleteMissingAllocationReturns404() throws Exception {

		mockMvc.perform(
				delete("/api/v1/allocations/999"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code")
						.value("WEEKLY_ALLOCATION_NOT_FOUND"))
				.andExpect(jsonPath("$.message")
						.value(
								"Weekly allocation not found with id: 999"));
	}

	@Test
	// verifies that a fractional allocation quantity is rejected
	void fractionalQuantityReturns400() throws Exception {

		mockMvc.perform(post("/api/v1/allocations")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						"customerId": 1,
						"weekStart": "2026-10-26",
						"allocationItems": [
						{
						"taroTypeId": 1,
						"quantity": 1.5
						}
						]
						}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code")
						.value("INVALID_QUANTITY"))
				.andExpect(jsonPath("$.message")
						.value(
								"Quantity must be a positive whole number"));
	}

	@Test
	void taroServiceUnavailableDuringCreateReturns503() throws Exception {
		when(taroClient.getTaroType(1L)).thenThrow(new TaroServiceUnavailableException());
		mockMvc.perform(post("/api/v1/allocations")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						"customerId": 1,
						"weekStart": "2026-09-14",
						"allocationItems": [{"taroTypeId": 1, "quantity": 100}]
						}
						"""))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("TARO_SERVICE_UNAVAILABLE"));
	}

	@Test
	void taroServiceUnavailableDuringGetReturns503() throws Exception {
		createAllocation(1L, "2026-09-14", 1L, 100);
		Long id = weeklyAllocationRepository.findAll().getFirst().getId();
		when(taroClient.getTaroType(1L)).thenThrow(new TaroServiceUnavailableException());
		mockMvc.perform(get("/api/v1/allocations/{id}", id))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("TARO_SERVICE_UNAVAILABLE"));
		mockMvc.perform(get("/api/v1/allocations"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("TARO_SERVICE_UNAVAILABLE"));
	}

	@Test
	void taroServiceUnavailableDuringUpdateReturns503() throws Exception {
		createAllocation(1L, "2026-09-14", 1L, 100);
		Long id = weeklyAllocationRepository.findAll().getFirst().getId();
		when(taroClient.getTaroType(1L)).thenThrow(new TaroServiceUnavailableException());
		mockMvc.perform(put("/api/v1/allocations/{id}", id)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						"customerId": 1,
						"weekStart": "2026-09-14",
						"allocationItems": [{"taroTypeId": 1, "quantity": 100}]
						}
						"""))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("TARO_SERVICE_UNAVAILABLE"));
	}

	@Test
	void referenceEndpointsUseSeparateClients() throws Exception {
		when(customerClient.getCustomers()).thenReturn(java.util.List.of(
				new CustomerResponse(1L, "Island Foods", "12 Market Street, Auckland", "John", "0211234567", true)));
		when(taroClient.getTaroTypes()).thenReturn(java.util.List.of(
				new TaroTypeResponse(1L, "Samoan Taro", "Large premium taro", new BigDecimal("50.00"))));
		mockMvc.perform(get("/api/v1/customer-service-reference/customers"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].name").value("Island Foods"))
				.andExpect(jsonPath("$[0].address").value("12 Market Street, Auckland"));
		mockMvc.perform(get("/api/v1/taro-service-reference/taro-types"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].name").value("Samoan Taro"));
	}

	// create an allocation used as setup for tests
	private void createAllocation(
			Long customerId,
			String weekStart,
			Long taroTypeId,
			int quantity) throws Exception {
		createAllocationAtPath("/api/v1/allocations", customerId, weekStart, taroTypeId, quantity);
	}

	private void createCustomerAllocation(
			Long customerId,
			String weekStart,
			Long taroTypeId,
			int quantity) throws Exception {
		createAllocationAtPath("/api/v1/allocations/customer-request", customerId, weekStart, taroTypeId, quantity);
	}

	private void createAllocationAtPath(
			String path,
			Long customerId,
			String weekStart,
			Long taroTypeId,
			int quantity) throws Exception {

		mockMvc.perform(post(path)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						"customerId": %d,
						"weekStart": "%s",
						"allocationItems": [
						{
						"taroTypeId": %d,
						"quantity": %d
						}
						]
						}
						""".formatted(
						customerId,
						weekStart,
						taroTypeId,
						quantity)))
				.andExpect(status().isCreated());
	}
}
