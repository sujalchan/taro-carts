package nz.ac.aut.comp713.customer_service;

import static org.hamcrest.Matchers.hasSize;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import nz.ac.aut.comp713.customer_service.repository.CustomerRepository;

@SpringBootTest
@AutoConfigureMockMvc
class CustomerApiTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private CustomerRepository customerRepository;

	// reset the database before each test
	@BeforeEach
	@SuppressWarnings("unused")
	void resetDatabase() {
		customerRepository.deleteAll();
	}

	// test retrieving customers when the database is empty
	@Test
	void getAllCustomersReturnsEmptyList() throws Exception {
		mockMvc.perform(get("/api/v1/customers"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray())
				.andExpect(jsonPath("$", hasSize(0)));
	}

	// test creating a valid customer
	@Test
	void createCustomerReturnsCreatedCustomer() throws Exception {
		String requestBody = """
				{
				"name": "Island Foods",
				"contactName": "John",
				"phone": "0211234567",
				"active": true
				}
				""";

		mockMvc.perform(post("/api/v1/customers")
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
				.andExpect(status().isCreated())
				.andExpect(header().exists("Location"))
				.andExpect(jsonPath("$.name").value("Island Foods"))
				.andExpect(jsonPath("$.contactName").value("John"))
				.andExpect(jsonPath("$.phone").value("0211234567"))
				.andExpect(jsonPath("$.active").value(true))
				.andExpect(jsonPath("$.id").isNumber());
	}

	// test retrieving an existing customer by id
	@Test
	void getCustomerByIdReturnsCustomer() throws Exception {
		String requestBody = """
				{
				"name": "Island Foods",
				"contactName": "John",
				"phone": "0211234567",
				"active": true
				}
				""";

		mockMvc.perform(post("/api/v1/customers")
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
				.andExpect(status().isCreated());

		Long customerId = customerRepository.findAll().getFirst().getId();

		mockMvc.perform(get("/api/v1/customers/{id}", customerId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(customerId))
				.andExpect(jsonPath("$.name").value("Island Foods"))
				.andExpect(jsonPath("$.contactName").value("John"))
				.andExpect(jsonPath("$.phone").value("0211234567"))
				.andExpect(jsonPath("$.active").value(true));
	}

	// test retrieving a customer that does not exist
	@Test
	void getUnknownCustomerReturnsNotFound() throws Exception {
		mockMvc.perform(get("/api/v1/customers/999"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"))
				.andExpect(jsonPath("$.message")
						.value("Customer not found with id: 999"))
				.andExpect(jsonPath("$.path")
						.value("/api/v1/customers/999"));
	}

	// test that a blank customer name fails validation
	@Test
	void blankCustomerNameReturnsBadRequest() throws Exception {
		String requestBody = """
				{
				"name": "",
				"contactName": "Sarah",
				"phone": "0219876543",
				"active": true
				}
				""";

		mockMvc.perform(post("/api/v1/customers")
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.message")
						.value("Customer name is required"))
				.andExpect(jsonPath("$.path")
						.value("/api/v1/customers"));
	}

	// test that duplicate customer names are rejected
	@Test
	void duplicateCustomerReturnsConflict() throws Exception {
		String requestBody = """
				{
				"name": "Island Foods",
				"contactName": "John",
				"phone": "0211234567",
				"active": true
				}
				""";

		mockMvc.perform(post("/api/v1/customers")
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/v1/customers")
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code")
						.value("CUSTOMER_ALREADY_EXISTS"))
				.andExpect(jsonPath("$.message")
						.value("Customer 'Island Foods' already exists."))
				.andExpect(jsonPath("$.path")
						.value("/api/v1/customers"));

		// only one customer should exist
		org.junit.jupiter.api.Assertions.assertEquals(
				1,
				customerRepository.count());
	}

	// test that invalid phone numbers are rejected
	@Test
	void invalidPhoneReturns400() throws Exception {
		mockMvc.perform(post("/api/v1/customers")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						"name": "Test Customer",
						"contactName": "Sujal",
						"phone": "Sujal",
						"active": true
						}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code")
						.value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.message")
						.value("Phone number must contain 7 to 15 digits"));
	}

	// test updating an existing customer's details
	@Test
	void updateCustomerReturnsUpdatedCustomer() throws Exception {
		String createRequest = """
				{
				"name": "Island Foods",
				"contactName": "John",
				"phone": "0211234567",
				"active": true
				}
				""";

		mockMvc.perform(post("/api/v1/customers")
				.contentType(MediaType.APPLICATION_JSON)
				.content(createRequest))
				.andExpect(status().isCreated());

		Long customerId = customerRepository.findAll().getFirst().getId();

		String updateRequest = """
				{
				"name": "Island Foods",
				"contactName": "James",
				"phone": "0219999999",
				"active": true
				}
				""";

		mockMvc.perform(put("/api/v1/customers/{id}", customerId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(updateRequest))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(customerId))
				.andExpect(jsonPath("$.name").value("Island Foods"))
				.andExpect(jsonPath("$.contactName").value("James"))
				.andExpect(jsonPath("$.phone").value("0219999999"))
				.andExpect(jsonPath("$.active").value(true));

		// confirm that the updated values were persisted
		mockMvc.perform(get("/api/v1/customers/{id}", customerId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.contactName").value("James"))
				.andExpect(jsonPath("$.phone").value("0219999999"));
	}

	// test updating a customer that does not exist
	@Test
	void updateUnknownCustomerReturnsNotFound() throws Exception {
		String requestBody = """
				{
				"name": "Test Customer",
				"contactName": "John",
				"phone": "0210000000",
				"active": true
				}
				""";

		mockMvc.perform(put("/api/v1/customers/999")
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"))
				.andExpect(jsonPath("$.message")
						.value("Customer not found with id: 999"))
				.andExpect(jsonPath("$.path")
						.value("/api/v1/customers/999"));
	}

	// test that a blank customer name fails validation during an update
	@Test
	void updateCustomerWithBlankNameReturnsBadRequest() throws Exception {
		String createRequest = """
				{
				"name": "Island Foods",
				"contactName": "John",
				"phone": "0211234567",
				"active": true
				}
				""";

		mockMvc.perform(post("/api/v1/customers")
				.contentType(MediaType.APPLICATION_JSON)
				.content(createRequest))
				.andExpect(status().isCreated());

		Long customerId = customerRepository.findAll().getFirst().getId();

		String invalidUpdate = """
				{
				"name": "",
				"contactName": "James",
				"phone": "0219999999",
				"active": true
				}
				""";

		mockMvc.perform(put("/api/v1/customers/{id}", customerId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(invalidUpdate))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.message")
						.value("Customer name is required"))
				.andExpect(jsonPath("$.path")
						.value("/api/v1/customers/" + customerId));
	}

	// test that a customer cannot be renamed to an existing customer's name
	@Test
	void updateCustomerToExistingNameReturnsConflict() throws Exception {
		String islandFoodsRequest = """
				{
				"name": "Island Foods",
				"contactName": "John",
				"phone": "0211234567",
				"active": true
				}
				""";

		String freshChoiceRequest = """
				{
				"name": "Fresh Choice",
				"contactName": "Sarah",
				"phone": "0215555555",
				"active": true
				}
				""";

		mockMvc.perform(post("/api/v1/customers")
				.contentType(MediaType.APPLICATION_JSON)
				.content(islandFoodsRequest))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/v1/customers")
				.contentType(MediaType.APPLICATION_JSON)
				.content(freshChoiceRequest))
				.andExpect(status().isCreated());

		var customers = customerRepository.findAll();

		Long freshChoiceId = customers.stream()
				.filter(customer -> customer.getName().equals("Fresh Choice"))
				.findFirst()
				.orElseThrow()
				.getId();

		String conflictingUpdate = """
				{
				"name": "Island Foods",
				"contactName": "Sarah",
				"phone": "0215555555",
				"active": true
				}
				""";

		mockMvc.perform(put("/api/v1/customers/{id}", freshChoiceId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(conflictingUpdate))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code")
						.value("CUSTOMER_ALREADY_EXISTS"))
				.andExpect(jsonPath("$.message")
						.value("Customer 'Island Foods' already exists."))
				.andExpect(jsonPath("$.path")
						.value("/api/v1/customers/" + freshChoiceId));

		// make sure the failed update did not create another row
		org.junit.jupiter.api.Assertions.assertEquals(
				2,
				customerRepository.count());
	}
}