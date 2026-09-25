package nz.ac.aut.comp713.allocation_service;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

import nz.ac.aut.comp713.allocation_service.client.CustomerClient;
import nz.ac.aut.comp713.allocation_service.client.CustomerResponse;
import nz.ac.aut.comp713.allocation_service.client.TaroTypeResponse;
import nz.ac.aut.comp713.allocation_service.exception.CustomerNotFoundException;
import nz.ac.aut.comp713.allocation_service.exception.CustomerServiceUnavailableException;
import nz.ac.aut.comp713.allocation_service.exception.TaroTypeNotFoundException;
import nz.ac.aut.comp713.allocation_service.repository.AllocationItemRepository;
import nz.ac.aut.comp713.allocation_service.repository.WeeklyAllocationRepository;

@SpringBootTest
@AutoConfigureMockMvc
class WeeklyAllocationApiTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AllocationItemRepository allocationItemRepository;

	@Autowired
	private WeeklyAllocationRepository weeklyAllocationRepository;

	@MockitoBean
	private CustomerClient customerClient;

	@BeforeEach
	void setUp() {

		// clear allocation data before each test
		allocationItemRepository.deleteAll();
		weeklyAllocationRepository.deleteAll();

		// mock customer-service responses
		when(customerClient.getCustomer(1L))
				.thenReturn(new CustomerResponse(
						1L,
						"Island Foods",
						"John",
						"0211234567",
						true));

		when(customerClient.getCustomer(2L))
				.thenReturn(new CustomerResponse(
						2L,
						"Fresh Choice",
						"Sarah",
						"0215555555",
						true));

		when(customerClient.getTaroType(1L))
				.thenReturn(new TaroTypeResponse(
						1L,
						"Samoan Taro",
						"Large premium taro",
						new BigDecimal("50.00")));

		when(customerClient.getTaroType(2L))
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
	// verifies that a new allocation uses the taro type's standard price by default
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

		when(customerClient.getTaroType(999L))
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

		when(customerClient.getTaroType(999L))
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

		when(customerClient.getTaroType(999L))
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

	// create an allocation used as setup for tests
	private void createAllocation(
			Long customerId,
			String weekStart,
			Long taroTypeId,
			int quantity) throws Exception {

		mockMvc.perform(post("/api/v1/allocations")
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
