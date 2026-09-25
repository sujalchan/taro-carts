package nz.ac.aut.comp713.allocation_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import nz.ac.aut.comp713.allocation_service.client.CustomerClient;
import nz.ac.aut.comp713.allocation_service.client.CustomerResponse;
import nz.ac.aut.comp713.allocation_service.client.TaroTypeResponse;
import nz.ac.aut.comp713.allocation_service.dto.AllocationItemRequest;
import nz.ac.aut.comp713.allocation_service.dto.WeeklyAllocationRequest;
import nz.ac.aut.comp713.allocation_service.exception.WeeklyAllocationAlreadyExistsException;
import nz.ac.aut.comp713.allocation_service.repository.AllocationItemRepository;
import nz.ac.aut.comp713.allocation_service.repository.WeeklyAllocationRepository;
import nz.ac.aut.comp713.allocation_service.service.WeeklyAllocationService;

@SpringBootTest
class ConcurrentWeeklyAllocationTest {

	@Autowired
	private WeeklyAllocationService weeklyAllocationService;

	@Autowired
	private WeeklyAllocationRepository weeklyAllocationRepository;

	@Autowired
	private AllocationItemRepository allocationItemRepository;

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

		when(customerClient.getTaroType(1L))
				.thenReturn(new TaroTypeResponse(
						1L,
						"Samoan Taro",
						"Large premium taro",
						new BigDecimal("50.00")));
	}

	@Test
	// verifies only one concurrent request is allowed to create the same weekly
	// allocation
	void onlyOneConcurrentRequestCanCreateSameWeeklyAllocation()
			throws Exception {

		WeeklyAllocationRequest request = new WeeklyAllocationRequest(
				1L,
				LocalDate.of(2026, 9, 14),
				List.of(
						new AllocationItemRequest(
								1L,
								new BigDecimal(100),
								null)));

		CountDownLatch readyLatch = new CountDownLatch(2);
		CountDownLatch startLatch = new CountDownLatch(1);

		ExecutorService executor = Executors.newFixedThreadPool(2);

		try {

			Future<String> firstResult = executor.submit(() -> attemptCreate(
					request,
					readyLatch,
					startLatch));

			Future<String> secondResult = executor.submit(() -> attemptCreate(
					request,
					readyLatch,
					startLatch));

			// wait until both threads are ready
			readyLatch.await();

			// release both threads at almost the same time
			startLatch.countDown();

			String resultOne = firstResult.get();
			String resultTwo = secondResult.get();

			long successCount = List.of(resultOne, resultTwo)
					.stream()
					.filter("SUCCESS"::equals)
					.count();

			long rejectedCount = List.of(resultOne, resultTwo)
					.stream()
					.filter("REJECTED"::equals)
					.count();

			// exactly one request should succeed
			assertEquals(1, successCount);

			// exactly one concurrent request should be rejected
			assertEquals(1, rejectedCount);

			// only one allocation should exist in the database
			assertEquals(1, weeklyAllocationRepository.count());

			assertTrue(
					weeklyAllocationRepository
							.existsByCustomerIdAndWeekStart(
									1L,
									LocalDate.of(2026, 9, 14)));

		} finally {
			executor.shutdownNow();
		}
	}

	private String attemptCreate(
			WeeklyAllocationRequest request,
			CountDownLatch readyLatch,
			CountDownLatch startLatch)
			throws InterruptedException {

		// tell the test that this thread is ready
		readyLatch.countDown();

		// wait until both threads are ready
		startLatch.await();

		try {
			weeklyAllocationService.createWeeklyAllocation(request);
			return "SUCCESS";

		} catch (WeeklyAllocationAlreadyExistsException exception) {
			return "REJECTED";

		} catch (CannotAcquireLockException exception) {
			// sqlite allows only one writer at a time
			return "REJECTED";
		}
	}
}