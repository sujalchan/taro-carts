package nz.ac.aut.comp713.taro_service;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import nz.ac.aut.comp713.taro_service.repository.TaroTypeRepository;

@SpringBootTest
@AutoConfigureMockMvc
class TaroTypeApiTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TaroTypeRepository taroTypeRepository;

	// reset the database before each test
	@BeforeEach
	@SuppressWarnings("unused")
	void resetDatabase() {
		taroTypeRepository.deleteAll();
	}

	// test retrieving taro types when the database is empty
	@Test
	void getAllTaroTypesReturnsEmptyList() throws Exception {
		mockMvc.perform(get("/api/v1/taro-types"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray())
				.andExpect(jsonPath("$", hasSize(0)));
	}

	// test creating a valid taro type
	@Test
	void createTaroTypeReturnsCreatedTaroType() throws Exception {
		String requestBody = """
				{
				    "name": "Samoan Taro",
				    "description": "Large premium taro",
				    "standardPrice": 50.00
				}
				""";

		mockMvc.perform(post("/api/v1/taro-types")
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
				.andExpect(status().isCreated())
				.andExpect(header().exists("Location"))
				.andExpect(jsonPath("$.name").value("Samoan Taro"))
				.andExpect(jsonPath("$.description").value("Large premium taro"))
				.andExpect(jsonPath("$.standardPrice").value(50.00))
				.andExpect(jsonPath("$.id").isNumber());
	}

	// test retrieving an existing taro type by id
	@Test
	void getTaroTypeByIdReturnsTaroType() throws Exception {
		String requestBody = """
				{
				    "name": "Samoan Taro",
				    "description": "Large premium taro",
				    "standardPrice": 50.00
				}
				""";

		mockMvc.perform(post("/api/v1/taro-types")
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
				.andExpect(status().isCreated());

		Long taroTypeId = taroTypeRepository.findAll().getFirst().getId();

		mockMvc.perform(get("/api/v1/taro-types/{id}", taroTypeId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(taroTypeId))
				.andExpect(jsonPath("$.name").value("Samoan Taro"))
				.andExpect(jsonPath("$.description").value("Large premium taro"))
				.andExpect(jsonPath("$.standardPrice").value(50.00));
	}

	// test retrieving a taro type that does not exist
	@Test
	void getUnknownTaroTypeReturnsNotFound() throws Exception {
		mockMvc.perform(get("/api/v1/taro-types/999"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("TARO_TYPE_NOT_FOUND"))
				.andExpect(jsonPath("$.message")
						.value("Taro type not found with id: 999"))
				.andExpect(jsonPath("$.path")
						.value("/api/v1/taro-types/999"));
	}

	// test that a blank taro type name fails validation
	@Test
	void blankTaroTypeNameReturnsBadRequest() throws Exception {
		String requestBody = """
				{
				    "name": "",
				    "description": "Invalid taro type",
				    "standardPrice": 50.00
				}
				""";

		mockMvc.perform(post("/api/v1/taro-types")
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.message")
						.value("Taro type name is required"))
				.andExpect(jsonPath("$.path")
						.value("/api/v1/taro-types"));
	}

	// test that a missing standard price fails validation
	@Test
	void missingStandardPriceReturnsBadRequest() throws Exception {
		String requestBody = """
				{
				    "name": "Test Taro",
				    "description": "Taro without a price"
				}
				""";

		mockMvc.perform(post("/api/v1/taro-types")
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.message")
						.value("Standard price is required"))
				.andExpect(jsonPath("$.path")
						.value("/api/v1/taro-types"));
	}

	// test that a negative standard price fails validation
	@Test
	void negativeStandardPriceReturnsBadRequest() throws Exception {
		String requestBody = """
				{
				    "name": "Test Taro",
				    "description": "Taro with an invalid price",
				    "standardPrice": -10.00
				}
				""";

		mockMvc.perform(post("/api/v1/taro-types")
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.message")
						.value("Standard price cannot be negative"))
				.andExpect(jsonPath("$.path")
						.value("/api/v1/taro-types"));
	}

	// test that duplicate taro type names are rejected
	@Test
	void duplicateTaroTypeReturnsConflict() throws Exception {
		String requestBody = """
				{
				    "name": "Samoan Taro",
				    "description": "Large premium taro",
				    "standardPrice": 50.00
				}
				""";

		mockMvc.perform(post("/api/v1/taro-types")
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/v1/taro-types")
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code")
						.value("TARO_TYPE_ALREADY_EXISTS"))
				.andExpect(jsonPath("$.message")
						.value("Taro type 'Samoan Taro' already exists."))
				.andExpect(jsonPath("$.path")
						.value("/api/v1/taro-types"));

		// only one taro type should exist
		assertEquals(1, taroTypeRepository.count());
	}

	// test updating an existing taro type
	@Test
	void updateTaroTypeReturnsUpdatedTaroType() throws Exception {
		String createRequest = """
				{
				    "name": "Samoan Taro",
				    "description": "Large premium taro",
				    "standardPrice": 50.00
				}
				""";

		mockMvc.perform(post("/api/v1/taro-types")
				.contentType(MediaType.APPLICATION_JSON)
				.content(createRequest))
				.andExpect(status().isCreated());

		Long taroTypeId = taroTypeRepository.findAll().getFirst().getId();

		String updateRequest = """
				{
				    "name": "Samoan Taro",
				    "description": "Updated premium Samoan taro",
				    "standardPrice": 55.00
				}
				""";

		mockMvc.perform(put("/api/v1/taro-types/{id}", taroTypeId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(updateRequest))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(taroTypeId))
				.andExpect(jsonPath("$.name").value("Samoan Taro"))
				.andExpect(jsonPath("$.description")
						.value("Updated premium Samoan taro"))
				.andExpect(jsonPath("$.standardPrice").value(55.00));

		// confirm that the updated values were persisted
		mockMvc.perform(get("/api/v1/taro-types/{id}", taroTypeId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.description")
						.value("Updated premium Samoan taro"))
				.andExpect(jsonPath("$.standardPrice").value(55.00));
	}

	// test updating a taro type that does not exist
	@Test
	void updateUnknownTaroTypeReturnsNotFound() throws Exception {
		String requestBody = """
				{
				    "name": "Test Taro",
				    "description": "Test description",
				    "standardPrice": 40.00
				}
				""";

		mockMvc.perform(put("/api/v1/taro-types/999")
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("TARO_TYPE_NOT_FOUND"))
				.andExpect(jsonPath("$.message")
						.value("Taro type not found with id: 999"))
				.andExpect(jsonPath("$.path")
						.value("/api/v1/taro-types/999"));
	}

	// test that a blank taro type name fails validation during an update
	@Test
	void updateTaroTypeWithBlankNameReturnsBadRequest() throws Exception {
		String createRequest = """
				{
				    "name": "Samoan Taro",
				    "description": "Large premium taro",
				    "standardPrice": 50.00
				}
				""";

		mockMvc.perform(post("/api/v1/taro-types")
				.contentType(MediaType.APPLICATION_JSON)
				.content(createRequest))
				.andExpect(status().isCreated());

		Long taroTypeId = taroTypeRepository.findAll().getFirst().getId();

		String invalidUpdate = """
				{
				    "name": "",
				    "description": "Invalid update",
				    "standardPrice": 50.00
				}
				""";

		mockMvc.perform(put("/api/v1/taro-types/{id}", taroTypeId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(invalidUpdate))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.message")
						.value("Taro type name is required"))
				.andExpect(jsonPath("$.path")
						.value("/api/v1/taro-types/" + taroTypeId));
	}

	// test that a negative price fails validation during an update
	@Test
	void updateTaroTypeWithNegativePriceReturnsBadRequest() throws Exception {
		String createRequest = """
				{
				    "name": "Samoan Taro",
				    "description": "Large premium taro",
				    "standardPrice": 50.00
				}
				""";

		mockMvc.perform(post("/api/v1/taro-types")
				.contentType(MediaType.APPLICATION_JSON)
				.content(createRequest))
				.andExpect(status().isCreated());

		Long taroTypeId = taroTypeRepository.findAll().getFirst().getId();

		String invalidUpdate = """
				{
				    "name": "Samoan Taro",
				    "description": "Invalid price update",
				    "standardPrice": -10.00
				}
				""";

		mockMvc.perform(put("/api/v1/taro-types/{id}", taroTypeId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(invalidUpdate))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.message")
						.value("Standard price cannot be negative"))
				.andExpect(jsonPath("$.path")
						.value("/api/v1/taro-types/" + taroTypeId));
	}

	// test that a taro type cannot be renamed to an existing taro type name
	@Test
	void updateTaroTypeToExistingNameReturnsConflict() throws Exception {
		String samoanTaroRequest = """
				{
				    "name": "Samoan Taro",
				    "description": "Large premium taro",
				    "standardPrice": 50.00
				}
				""";

		String fijiTaroRequest = """
				{
				    "name": "Fiji Taro",
				    "description": "Fijian taro",
				    "standardPrice": 45.00
				}
				""";

		mockMvc.perform(post("/api/v1/taro-types")
				.contentType(MediaType.APPLICATION_JSON)
				.content(samoanTaroRequest))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/v1/taro-types")
				.contentType(MediaType.APPLICATION_JSON)
				.content(fijiTaroRequest))
				.andExpect(status().isCreated());

		var taroTypes = taroTypeRepository.findAll();

		Long fijiTaroId = taroTypes.stream()
				.filter(taroType -> taroType.getName().equals("Fiji Taro"))
				.findFirst()
				.orElseThrow()
				.getId();

		String conflictingUpdate = """
				{
				    "name": "Samoan Taro",
				    "description": "Fijian taro",
				    "standardPrice": 45.00
				}
				""";

		mockMvc.perform(put("/api/v1/taro-types/{id}", fijiTaroId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(conflictingUpdate))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code")
						.value("TARO_TYPE_ALREADY_EXISTS"))
				.andExpect(jsonPath("$.message")
						.value("Taro type 'Samoan Taro' already exists."))
				.andExpect(jsonPath("$.path")
						.value("/api/v1/taro-types/" + fijiTaroId));

		// make sure the failed update did not create another row
		assertEquals(2, taroTypeRepository.count());
	}
}