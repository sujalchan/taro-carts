package nz.ac.aut.comp713.customer_service;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import nz.ac.aut.comp713.customer_service.repository.CustomerRepository;

@SpringBootTest
@AutoConfigureMockMvc
class ConcurrentCustomerCreationTest {

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

	// test that only one of two concurrent requests can create the same customer
	@Test
	void onlyOneConcurrentRequestCanCreateSameCustomer() throws Exception {

		String requestBody = """
				{
				"name": "Island Foods",
				"contactName": "John",
				"phone": "0211234567",
				"active": true
				}
				""";

		ExecutorService executor = Executors.newFixedThreadPool(2);

		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);

		try {
			Future<MvcResult> requestA = executor.submit(() -> {
				ready.countDown();
				start.await();

				return mockMvc.perform(
						post("/api/v1/customers")
								.contentType(MediaType.APPLICATION_JSON)
								.content(requestBody))
						.andReturn();
			});

			Future<MvcResult> requestB = executor.submit(() -> {
				ready.countDown();
				start.await();

				return mockMvc.perform(
						post("/api/v1/customers")
								.contentType(MediaType.APPLICATION_JSON)
								.content(requestBody))
						.andReturn();
			});

			// wait until both threads are ready
			ready.await();

			// release both requests together
			start.countDown();

			int statusA = requestA.get().getResponse().getStatus();
			int statusB = requestB.get().getResponse().getStatus();

			List<Integer> statuses = List.of(statusA, statusB);

			long createdCount = statuses.stream()
					.filter(status -> status == 201)
					.count();

			long conflictCount = statuses.stream()
					.filter(status -> status == 409)
					.count();

			assertEquals(
					1,
					createdCount,
					"Exactly one request should create the customer");

			assertEquals(
					1,
					conflictCount,
					"Exactly one request should receive 409 Conflict");

			assertEquals(
					1,
					customerRepository.count(),
					"Only one customer row should exist");

		} finally {
			executor.shutdownNow();
		}
	}
}